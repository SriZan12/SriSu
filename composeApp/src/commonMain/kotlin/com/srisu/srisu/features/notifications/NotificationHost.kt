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

fun notificationMatches(route: Route, current: Route?): Boolean = when {
    route is ChatNav.CoupleConversation && current is ChatNav.CoupleConversation -> route.roomId == current.roomId
    route is PlansDestination && current is PlansDestination -> route.id == current.id
    route is SparksDestination && current is SparksDestination -> route.id == current.id
    route is CoupleProfileDestination && current is CoupleProfileDestination -> route == current
    else -> route == current
}

@Composable fun NotificationHost(protected: Boolean, entry: NavBackStackEntry?, open: (Route)->Unit, snackbars: SnackbarHostState) {
    val repo: NotificationRepository = koinInject()
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
        else -> null
    }
    val currentRoute by rememberUpdatedState(when {
        current is ChatNav.CoupleConversation && visible is ChatNav.CoupleConversation -> visible
        current is CoupleProfileDestination && visible is CoupleProfileDestination -> visible
        else -> current
    })
    LaunchedEffect(signal,protected,retry) {
        val pending = signal ?: return@LaunchedEffect
        if(!protected) return@LaunchedEffect
        when(val result=repo.resolve(pending)) {
            is NotificationResolution.Ready -> {
                if(pending.tapped) open(result.route)
                else if(!notificationMatches(result.route,currentRoute)) {
                    if(snackbars.showSnackbar("You have an update. Open SriSu to view it.","Open",withDismissAction=true)==SnackbarResult.ActionPerformed) {
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
    if(protected && (current is CoupleProfileDestination || current == ChatNav.PartnerRequests) && native.configured && native.permission=="not_determined" && !native.promptDismissed) {
        AlertDialog(onDismissRequest={NativeNotifications.driver?.dismissPrompt()},title={Text("Stay in touch")},
            text={Text("Get private alerts for invitations and shared activity. Personal details stay inside SriSu.")},
            confirmButton={TextButton(onClick={NativeNotifications.driver?.dismissPrompt();NativeNotifications.driver?.requestPermission()}) {Text("Enable notifications")}},
            dismissButton={TextButton(onClick={NativeNotifications.driver?.dismissPrompt()}) {Text("Not now")}})
    }
}
