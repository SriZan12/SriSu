package com.srisu.srisu.features.notifications

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.srisu.srisu.features.settings.*
import com.srisu.srisu.theme.spacing
import org.koin.compose.koinInject

/** Both entry points edit only the authenticated user's notification preferences. */
@Composable fun NotificationSettings(repository: NotificationRepository = koinInject(), sharedOnly: Boolean=false, onBack: () -> Unit) {
    val state by repository.state.collectAsState()
    val native by NativeNotifications.state.collectAsState()
    LaunchedEffect(Unit) { repository.reload(); NativeNotifications.driver?.refresh() }
    SettingsPage(if(sharedOnly) "Shared notifications" else "Notifications", onBack) {
        item { SettingsNotice(if(sharedOnly) "Choose which shared activities alert you. These settings affect your notifications, never your partner’s."
            else "Choose your alerts. Sounds and lock-screen previews are controlled by your device.") }
        item { SettingsNotice(when {
            !native.configured -> "Notifications are not configured in this build."
            native.permission == "granted" -> "Notifications are allowed on this device."
            native.permission == "denied" -> "Notifications are off on this device. Allow them in system settings."
            else -> "Allow notifications on this device."
        }) }
        if(native.configured) item { TextButton(onClick={
            if(native.permission=="not_determined") NativeNotifications.driver?.requestPermission()
            else NativeNotifications.driver?.openSettings()
        }) {Text(if(native.permission=="not_determined") "Enable notifications" else "Open system settings")} }
        if(state.busy) item {LinearProgressIndicator(Modifier.fillMaxWidth())}
        state.error?.let { item { SettingsNotice(it,true); TextButton(onClick=repository::reload,enabled=!state.busy) {Text("Reload settings")} } }
        if(state.registrationFailed) item {
            SettingsNotice("This device hasn’t connected for notifications yet.",true)
            TextButton(onClick={NativeNotifications.driver?.refresh();repository.reload()},enabled=!state.busy) {Text("Retry connection")}
        }
        state.preferences?.let { prefs ->
            if(!sharedOnly) {
                item {SettingsSection("YOUR ALERTS")}
                item {SettingSwitch("Push notifications",prefs.push,!state.busy) {repository.save(push=it)}}
                if("matrix-chat-push-1" in prefs.capabilities) {
                    item {SettingSwitch("Chat messages",prefs.chat,!state.busy && prefs.push) {repository.save(chat=it)}}
                    item {
                        SettingsNotice(if(prefs.muteForever) "Chat muted until you turn it back on" else prefs.muteUntil?.let {"Chat muted until $it"} ?: "Chat is not muted")
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                            listOf("1h" to "Mute 1 hour","8h" to "Mute 8 hours","forever" to "Mute indefinitely","off" to "Unmute").forEach { (value,label) ->
                                TextButton(onClick={repository.save(mute=value)},enabled=!state.busy && prefs.push && prefs.chat) {Text(label)}
                            }
                        }
                    }
                } else item {SettingsNotice("Chat push notifications are not available yet.")}
            } else if(!prefs.push) item {SettingsNotice("Push notifications are off. Turn them on in Notifications to receive these alerts.")}
            item {SettingsSection("SHARED ACTIVITY",spaced=!sharedOnly)}
            item {SettingSwitch("Invitations & shared activity",prefs.activity,!state.busy && prefs.push) {repository.save(activity=it)}}
            if(NOTIFICATION_CATALOGUE in prefs.capabilities) {
                item {SettingsNotice("These controls affect phone alerts. Activity stays in your notification centre. Requests and Plan invitations can sound; other updates arrive quietly.")}
                notificationCategories.forEach { category -> item {
                    SettingSwitch(category.label,prefs.categories[category.key] ?: category.enabledByDefault,
                        !state.busy && prefs.push && prefs.activity) {repository.save(category=category.key to it)}
                } }
            }
        }
        if(!sharedOnly) {
            item {SettingsSection("DIAGNOSTICS",spaced=true)}
            item {SettingsNotice("Optional crash reports help diagnose problems. We do not add your name, messages or account ID. Turning this off deletes unsent reports.")}
            item {SettingSwitch("Share crash diagnostics",native.diagnostics,native.configured) {NativeNotifications.driver?.setDiagnostics(it)}}
        }
    }
}
@Composable private fun SettingSwitch(label:String,checked:Boolean,enabled:Boolean,onChange:(Boolean)->Unit) {
    Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().toggleable(value=checked,enabled=enabled,role=Role.Switch,onValueChange=onChange).semantics {contentDescription=label}
            .padding(horizontal=MaterialTheme.spacing.medium,vertical=MaterialTheme.spacing.small),
            verticalAlignment=Alignment.CenterVertically) {
            Text(label,Modifier.weight(1f).padding(end=MaterialTheme.spacing.small),style=MaterialTheme.typography.bodyLarge)
            Switch(checked,onCheckedChange=null,enabled=enabled)
        }
    }
}
