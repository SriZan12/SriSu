package com.srisu.srisu.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import com.srisu.srisu.core.permissionmanager.*
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.features.coupleprofile.navigation.*
import com.srisu.srisu.features.plans.navigation.PlansDestination
import com.srisu.srisu.navigation.*
import com.srisu.srisu.navigation.graph.*
import com.srisu.srisu.theme.spacing
import kotlinx.coroutines.*
import org.koin.compose.koinInject

private val pages = mapOf("account" to "Account", "language" to "Language", "shared" to "Shared preferences",
    "downloads" to "Downloads", "appearance" to "Appearance", "accessibility" to "Accessibility",
    "support" to "Support", "privacy" to "Privacy & Security")

@Composable
fun SettingsDetailScreen(page: String, onBack: () -> Unit, onOpen: (Route) -> Unit) {
    val title = pages[page] ?: return NavigationUnavailable(onBack)
    val sessions: SessionCoordinator = koinInject()
    val access: CoupleAccessCoordinator = koinInject()
    val connection by access.state.collectAsState()
    val connected = (connection as? CoupleAccess.Ready)?.takeIf { it.warning == null }?.coupleId
    val prefs: AppearancePreferences = koinInject()
    val appearance by prefs.appearance.collectAsState(AppAppearance.SYSTEM)
    val scope = rememberCoroutineScope()
    val context = LocalPlatformContext.current
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val permissions = createPermissionsManager(object : PermissionCallback {
        override fun onPermissionStatus(permissionType: PermissionType, status: PermissionState) = Unit
    })
    fun connectedRoute(route: Route) { onOpen(if(connected != null) route else ChatNav.PartnerFlow) }
    SettingsPage(title,onBack) {
        when(page) {
            "account" -> {
                item { SettingsNotice("Manage your personal profile. These details belong to your account.") }
                item { SettingsRow("Edit profile",Icons.Outlined.Person,"Photo, name, bio and interests",onClick={onOpen(ProfileNav.EditProfileDetails)}) }
                sessions.currentSession()?.phoneNumber?.let { phone -> item { InfoBlock("Verified phone number",phone) } }
                item { SettingsRow("Profile visibility",Icons.Outlined.VerifiedUser,"Choose whether to share your bio and interests",onClick={onOpen(ProfileNav.EditProfileDetails)}) }
            }
            "language" -> {
                item { InfoBlock("English","SriSu currently supports English. Your messages and shared content stay in the language you write them in.") }
            }
            "shared" -> {
                item { SettingsNotice("Shared choices stay with your couple. Notification preferences apply only to your own alerts.") }
                if(connected == null) item { ConnectionNotice(connection, access::retry) {onOpen(ChatNav.PartnerFlow)} }
                else {
                    item { SettingsRow("Plans & reminders",Icons.Outlined.CalendarMonth,"Manage a reminder from the plan it belongs to",onClick={connectedRoute(PlansDestination())}) }
                    item { SettingsRow("Moment & profile sharing",Icons.Outlined.PeopleOutline,"Review what you both choose to share",onClick={connectedRoute(CoupleProfileDestination(connected,CoupleSection.SHARING))}) }
                    item { SettingsRow("Shared notifications",Icons.Outlined.Notifications,"Your alerts about your shared activity",onClick={onOpen(ProfileNav.SharedNotifications)}) }
                }
            }
            "downloads" -> {
                item { InfoBlock("Photos & attachments","Chat attachments download when you open them. Saving a photo or video uses your device’s share options. SriSu does not automatically save Moments to your photo library.") }
                item { SettingsRow("Clear image cache",Icons.Outlined.CleaningServices,"Frees temporary image previews; images can load again",enabled=!busy,onClick={confirmClear=true}) }
                item { SettingsNotice("This does not remove your photos, chat history, encrypted chat downloads or pending uploads.") }
            }
            "appearance" -> {
                item { SettingsNotice("Choose how SriSu looks on this device. Chat keeps its selected palette.") }
                AppAppearance.entries.forEach { choice -> item {
                    Surface(onClick={if(!busy) {busy=true;scope.launch {
                        try {prefs.save(choice);notice=null} catch(e:CancellationException) {throw e}
                        catch(_:Exception) {failed=true;notice="Couldn’t save appearance. Try again."}
                        finally {busy=false}
                    }}},enabled=!busy,shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surfaceContainer,
                        modifier=Modifier.fillMaxWidth().semantics {role=Role.RadioButton;selected=appearance==choice}) {
                        Row(Modifier.padding(MaterialTheme.spacing.medium),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                            Text(choice.label,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge)
                            RadioButton(appearance==choice,null)
                        }
                    }
                } }
            }
            "accessibility" -> {
                item { InfoBlock("Text & display","SriSu follows your device’s text size. Settings rows grow and wrap so larger text stays readable.") }
                item { InfoBlock("Screen readers","Use VoiceOver on iPhone or TalkBack on Android. Controls include labels and generous touch targets.") }
                item { InfoBlock("Motion","This Settings screen uses no decorative animation. Expanding Partner keeps you in the same list.") }
                item { SettingsNotice("Change text size and screen reader preferences in your device’s Accessibility settings.") }
            }
            "support" -> {
                item { InfoBlock("Having trouble connecting?","Check your internet connection and confirm the phone number your partner used to join. Pending invitations appear in Find Partner.") }
                item { SettingsRow("Find Partner",Icons.Outlined.PeopleOutline,onClick={onOpen(ChatNav.PartnerFlow)}) }
                item { InfoBlock("Missing an alert?","Check notification permission on this device, then your SriSu notification preferences. Muted chats do not alert you.") }
                item { SettingsRow("Notification settings",Icons.Outlined.Notifications,onClick={onOpen(ProfileNav.NotificationSettings)}) }
                item { InfoBlock("Photo or camera access","If you declined access, you can allow it from SriSu’s permissions in your device settings.") }
                item { SettingsRow("App permissions",Icons.Outlined.PermDeviceInformation,onClick=permissions::launchSettings) }
            }
            "privacy" -> {
                item { SettingsRow("Find Partner visibility",Icons.Outlined.Person,"Manage bio and interests on your profile",onClick={onOpen(ProfileNav.EditProfileDetails)}) }
                item { SettingsRow("Shared profile visibility",Icons.Outlined.PeopleOutline,"Review your couple’s sharing choices",onClick={onOpen(ProfileNav.SettingsDetail("shared"))}) }
                item { SettingsRow("App permissions",Icons.Outlined.PermDeviceInformation,"Camera, photos and microphone",onClick=permissions::launchSettings) }
                item { SettingsRow("Notifications & diagnostics",Icons.Outlined.Notifications,"Manage alerts and optional crash reports",onClick={onOpen(ProfileNav.NotificationSettings)}) }
                item { InfoBlock("Private conversation","Your couple conversation uses Matrix end-to-end encryption. Moment visibility is controlled separately when you share a Moment.") }
            }
        }
        if(busy) item {LinearProgressIndicator(Modifier.fillMaxWidth())}
        notice?.let { item {SettingsNotice(it,failed)} }
    }
    if(confirmClear) AlertDialog(onDismissRequest={confirmClear=false},title={Text("Clear image cache?")},
        text={Text("Temporary previews will load again when needed. Your original photos and messages stay saved.")},
        dismissButton={TextButton(onClick={confirmClear=false}) {Text("Cancel")}},
        confirmButton={TextButton(onClick={confirmClear=false;busy=true;scope.launch {
            try {
                val loader=SingletonImageLoader.get(context)
                withContext(Dispatchers.IO) {loader.diskCache?.clear();loader.memoryCache?.clear()}
                failed=false;notice="Image cache cleared."
            } catch(e:CancellationException) {throw e}
            catch(_:Exception) {failed=true;notice="Couldn’t clear image cache. Try again."}
            finally {busy=false}
        }}) {Text("Clear cache")}})
}

@Composable private fun InfoBlock(title:String,body:String) {
    Column(Modifier.fillMaxWidth().padding(vertical=MaterialTheme.spacing.small),verticalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        Text(title,style=MaterialTheme.typography.titleMedium)
        Text(body,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun ConnectionNotice(state:CoupleAccess,retry:()->Unit,connect:()->Unit) {
    when {
        state is CoupleAccess.Ready && state.warning == null && state.coupleId == null ->
            SettingsRow("Connect your partner",Icons.Outlined.PeopleOutline,"Then choose how you share together",onClick=connect)
        state is CoupleAccess.Resolving -> SettingsNotice("Checking your connection…")
        else -> SettingsRow("Retry connection",Icons.Outlined.Refresh,"Couldn’t check your shared space",onClick=retry)
    }
}
