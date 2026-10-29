package com.srisu.srisu.features.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.srisu.srisu.notifications.AndroidNotifications
import com.srisu.srisu.notifications.FirebaseIdState
import com.srisu.srisu.notifications.isNotificationDebugBuild
import com.srisu.srisu.theme.spacing

@Composable
internal actual fun NotificationRegistrationTools() {
    if (!isNotificationDebugBuild(LocalContext.current)) return
    val native by NativeNotifications.state.collectAsState()
    val provider = remember(native.revision) { AndroidNotifications.instance }
    key(native.revision) {
        FirebaseIdCopyPanel(
            state = provider?.registrationIdAccess()?.state ?: FirebaseIdState.NOT_CONFIGURED,
            onCopy = { provider?.copyRegistrationIdForTesting() == true },
            onRetry = { provider?.refresh() },
        )
    }
}

@Composable
internal fun FirebaseIdCopyPanel(state: FirebaseIdState, onCopy: () -> Boolean, onRetry: () -> Unit) {
    if (state == FirebaseIdState.HIDDEN) return
    var copyResult by remember(state) { mutableStateOf<Boolean?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        HorizontalDivider()
        Text("Firebase test notifications", style = MaterialTheme.typography.titleMedium)
        Text("Copy this phone’s Firebase ID (FID) into Firebase’s Send test message dialog.")
        Text(when (state) {
            FirebaseIdState.NOT_CONFIGURED -> "Firebase is not configured in this build."
            FirebaseIdState.SIGN_IN_REQUIRED -> "Sign in to get this phone’s Firebase ID."
            FirebaseIdState.PERMISSION_REQUIRED -> "Enable notifications above to register this phone."
            FirebaseIdState.REGISTERING -> "Getting this phone’s Firebase ID…"
            FirebaseIdState.WAITING -> "Waiting for Firebase registration. You can retry if it does not finish."
            FirebaseIdState.FAILED -> "Couldn’t register with Firebase. Check your connection and retry."
            FirebaseIdState.READY -> "This phone is ready for a Firebase test notification."
            FirebaseIdState.HIDDEN -> ""
        })
        if (state == FirebaseIdState.REGISTERING) LinearProgressIndicator()
        TextButton(onClick = { copyResult = onCopy() }, enabled = state == FirebaseIdState.READY) {
            Text("Copy Firebase ID")
        }
        if (state == FirebaseIdState.WAITING || state == FirebaseIdState.FAILED) {
            TextButton(onClick = { copyResult = null; onRetry() }) { Text("Retry Firebase registration") }
        }
        when (copyResult) {
            true -> Text("Copied. Paste it into Firebase’s Send test message dialog.")
            false -> Text("Couldn’t copy the Firebase ID. Check notification permission and try again.", color = MaterialTheme.colorScheme.error)
            null -> Unit
        }
    }
}
