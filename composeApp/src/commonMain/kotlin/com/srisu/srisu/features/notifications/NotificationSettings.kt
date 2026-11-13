package com.srisu.srisu.features.notifications

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.srisu.srisu.theme.spacing
import org.koin.compose.koinInject

/** Derived from existing SriSu theme/components; live Figma settings frame is quota-limited. */
@Composable fun NotificationSettings(repository: NotificationRepository = koinInject(), onBack: () -> Unit) {
    val state by repository.state.collectAsState()
    val native by NativeNotifications.state.collectAsState()
    LaunchedEffect(Unit) { repository.reload();NativeNotifications.driver?.refresh() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(MaterialTheme.spacing.gutter),
        verticalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
        TextButton(onClick=onBack) { Text("Back") }
        Text("Notifications & diagnostics",style=MaterialTheme.typography.headlineSmall)
        Text("Alerts keep your private details off the lock screen. Open SriSu to see the update.")
        Text(when {
            !native.configured -> "Notifications are not configured in this build."
            native.permission == "granted" -> "Notifications are allowed on this device. Manage sounds in system settings."
            native.permission == "denied" -> "Notifications are off on this device. You can allow them in system settings."
            else -> "Allow private notifications on this device."
        })
        if(native.configured) TextButton(onClick={ if(native.permission=="not_determined") NativeNotifications.driver?.requestPermission() else NativeNotifications.driver?.openSettings() }) {
            Text(if(native.permission=="not_determined") "Enable notifications" else "Open system settings")
        }
        if(state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { Text(it,color=MaterialTheme.colorScheme.error);TextButton(onClick=repository::reload,enabled=!state.busy) { Text("Reload settings") } }
        if(state.registrationFailed) { Text("This device hasn’t connected for notifications yet.");TextButton(onClick={NativeNotifications.driver?.refresh();repository.reload()}) {Text("Retry connection")} }
        state.preferences?.let { prefs ->
            SettingSwitch("Push notifications",prefs.push,!state.busy) {repository.save(push=it)}
            if ("matrix-chat-push-1" in prefs.capabilities) {
            SettingSwitch("Chat messages",prefs.chat,!state.busy) {repository.save(chat=it)}
            Text(if(prefs.muteForever) "Chat muted until you turn it back on" else prefs.muteUntil?.let {"Chat muted until $it"} ?: "Chat is not muted")
            FlowRow(horizontalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                listOf("1h" to "Mute 1 hour","8h" to "Mute 8 hours","forever" to "Mute indefinitely","off" to "Unmute").forEach { (value,label) ->
                    TextButton(onClick={repository.save(mute=value)},enabled=!state.busy) {Text(label)}
                }
            }
            } else Text("Chat push notifications are not available yet.")
            SettingSwitch("Invitations & shared activity",prefs.activity,!state.busy) {repository.save(activity=it)}
        }
        HorizontalDivider()
        Text("Crash reports",style=MaterialTheme.typography.titleMedium)
        Text("Help diagnose crashes with optional reports. We do not add your name, messages or account ID. The operating system may retain crash data locally. Turning this off deletes unsent reports.")
        SettingSwitch("Share crash diagnostics",native.diagnostics,native.configured) { NativeNotifications.driver?.setDiagnostics(it) }
    }
}
@Composable private fun SettingSwitch(label:String,checked:Boolean,enabled:Boolean,onChange:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
        Text(label,Modifier.weight(1f).padding(end=MaterialTheme.spacing.small))
        Switch(checked,onChange,enabled=enabled,modifier=Modifier.semantics { contentDescription=label })
    }
}
