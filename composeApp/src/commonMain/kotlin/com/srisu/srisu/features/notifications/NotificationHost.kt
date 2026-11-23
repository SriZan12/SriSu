package com.srisu.srisu.features.notifications

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.toRoute
import com.srisu.srisu.navigation.graph.*
import com.srisu.srisu.features.coupleprofile.navigation.CoupleProfileDestination
import org.koin.compose.koinInject
import com.srisu.srisu.features.plans.navigation.PlansDestination
import com.srisu.srisu.features.sparks.navigation.SparksDestination
import com.srisu.srisu.features.challenges.navigation.ChallengesDestination
import com.srisu.srisu.features.moments.navigation.*
import com.srisu.srisu.features.plans.navigation.DateIdeaDestination

fun notificationMatches(route: Route, current: Route?): Boolean = when {
    route is ChatNav.CoupleConversation && current is ChatNav.CoupleConversation -> route.roomId == current.roomId
    route is PlansDestination && current is PlansDestination -> route.id == current.id
    route is SparksDestination && current is SparksDestination -> route.id == current.id
    route is ChallengesDestination && current is ChallengesDestination -> route.id == current.id
    route is MomentViewerDestination && current is MomentViewerDestination -> route.momentId == current.momentId
    route is CoupleProfileDestination && current is CoupleProfileDestination -> route == current
    else -> route == current
}

internal fun notificationFallback(route: Route): String = when (route) {
    is ChatNav.CoupleConversation -> "New message. Open your chat to read it."
    is PlansDestination -> "Your partner updated a plan. Open the plan for details."
    is SparksDestination -> "Your shared question has an update. Open it to see your next turn."
    is ChallengesDestination -> "Your shared challenge has an update. Open the challenge for details."
    is MomentViewerDestination -> "There’s activity on a shared moment. Open the moment to see it."
    is MomentNoteDestination -> "You received a note or reply about a moment. Open the conversation."
    is DateIdeaDestination -> "Your shared date idea has an update. Open the idea for details."
    ChatNav.PartnerRequests -> "You have a partner invitation. Open your requests."
    is CoupleProfileDestination -> "Your partner updated your shared space. Open it for details."
    HomeNavigation.Home -> "Your partner updated their status. Open Home to see it."
    else -> "Open SriSu to view this notification."
}

@Composable fun NotificationHost(protected: Boolean, entry: NavBackStackEntry?, open: (Route)->Unit, snackbars: SnackbarHostState) {
    val repo: NotificationRepository = koinInject()
    val previews: NotificationPreviewReader = koinInject()
    val signal by NativeNotifications.signal.collectAsState()
    val native by NativeNotifications.state.collectAsState()
    val visible by NotificationVisibility.current.collectAsState()
    var retry by remember { mutableStateOf(0) }
    val current = when {
        entry?.destination?.hasRoute<ChatNav.CoupleConversation>() == true -> entry.toRoute<ChatNav.CoupleConversation>()
        entry?.destination?.hasRoute<CoupleProfileDestination>() == true -> entry.toRoute<CoupleProfileDestination>()
        entry?.destination?.hasRoute<ChatNav.PartnerRequests>() == true -> ChatNav.PartnerRequests
        entry?.destination?.hasRoute<PlansDestination>() == true -> entry.toRoute<PlansDestination>()
        entry?.destination?.hasRoute<SparksDestination>() == true -> entry.toRoute<SparksDestination>()
        entry?.destination?.hasRoute<ChallengesDestination>() == true -> entry.toRoute<ChallengesDestination>()
        entry?.destination?.hasRoute<MomentViewerDestination>() == true -> entry.toRoute<MomentViewerDestination>()
        entry?.destination?.hasRoute<MomentNoteDestination>() == true -> entry.toRoute<MomentNoteDestination>()
        entry?.destination?.hasRoute<DateIdeaDestination>() == true -> entry.toRoute<DateIdeaDestination>()
        entry?.destination?.hasRoute<HomeNavigation.Home>() == true -> HomeNavigation.Home
        else -> null
    }
    val currentRoute by rememberUpdatedState(when {
        current is ChatNav.CoupleConversation && visible is ChatNav.CoupleConversation -> visible
        current is CoupleProfileDestination && visible is CoupleProfileDestination -> visible
        current is MomentViewerDestination && visible is MomentViewerDestination -> visible
        else -> current
    })
    LaunchedEffect(signal,protected,retry) {
        val pending = signal ?: return@LaunchedEffect
        if(!protected) return@LaunchedEffect
        when(val result=repo.resolve(pending)) {
            is NotificationResolution.Ready -> {
                if(pending.tapped) open(result.route)
                else if(!notificationMatches(result.route,currentRoute)) {
                    val preview = (previews.load(pending.deliveryId) as? PreviewResult.Ready)?.value
                    val copy = preview?.let { "${it.title}: ${it.body}" } ?: notificationFallback(result.route)
                    if(snackbars.showSnackbar(copy,"Open",withDismissAction=true)==SnackbarResult.ActionPerformed) {
                        when(val refreshed=repo.resolve(pending)) {
                            is NotificationResolution.Ready -> open(refreshed.route)
                            else -> snackbars.showSnackbar("This update is no longer available. Open the app to refresh.")
                        }
                    }
                }
                NativeNotifications.consume(pending)
            }
            NotificationResolution.Unavailable -> {
                if(pending.tapped) snackbars.showSnackbar("This update is no longer available.")
                NativeNotifications.consume(pending)
            }
            NotificationResolution.Retry -> if(pending.tapped) {
                if(snackbars.showSnackbar("Couldn’t open this update. Check your connection and try again.","Retry",withDismissAction=true)==SnackbarResult.ActionPerformed) retry++
                else NativeNotifications.consume(pending)
            } else NativeNotifications.consume(pending)
        }
    }
    // Contextual, once-dismissed primer only when the user opens the conversation.
    if(protected && (current is ChatNav.CoupleConversation || current is CoupleProfileDestination || current == ChatNav.PartnerRequests) && native.configured && native.permission=="not_determined" && !native.promptDismissed) {
        AlertDialog(onDismissRequest={NativeNotifications.driver?.dismissPrompt()},title={Text("Stay in touch")},
            text={Text("Get message previews and updates about your plans and shared moments. Manage lock-screen previews in your device settings.")},
            confirmButton={TextButton(onClick={NativeNotifications.driver?.dismissPrompt();NativeNotifications.driver?.requestPermission()}) {Text("Enable notifications")}},
            dismissButton={TextButton(onClick={NativeNotifications.driver?.dismissPrompt()}) {Text("Not now")}})
    }
}
