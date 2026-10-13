package com.srisu.srisu.features.notifications

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.toRoute
import com.srisu.srisu.navigation.graph.*
import com.srisu.srisu.features.coupleprofile.navigation.CoupleProfileDestination
import org.koin.compose.koinInject

fun notificationMatches(route: Route, current: Route?): Boolean = when {
    route is ChatNav.CoupleConversation && current is ChatNav.CoupleConversation -> route.roomId == current.roomId
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
    if(protected && current is ChatNav.CoupleConversation && native.configured && native.permission=="not_determined" && !native.promptDismissed) {
        AlertDialog(onDismissRequest={NativeNotifications.driver?.dismissPrompt()},title={Text("Stay in touch")},
            text={Text("Get a private alert when your partner messages you. Message details stay inside SriSu.")},
            confirmButton={TextButton(onClick={NativeNotifications.driver?.dismissPrompt();NativeNotifications.driver?.requestPermission()}) {Text("Enable notifications")}},
            dismissButton={TextButton(onClick={NativeNotifications.driver?.dismissPrompt()}) {Text("Not now")}})
    }
}
