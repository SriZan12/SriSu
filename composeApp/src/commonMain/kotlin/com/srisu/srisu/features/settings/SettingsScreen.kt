package com.srisu.srisu.features.settings

import com.srisu.srisu.theme.selectedSurface

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.ImeAction
import com.srisu.srisu.core.identity.nicknameLength
import com.srisu.srisu.core.identity.nicknameValidation
import com.srisu.srisu.core.identity.partnerDisplayName
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.srisu.srisu.components.NavigationBackButton
import com.srisu.srisu.navigation.graph.Route
import com.srisu.srisu.navigation.graph.ProfileNav
import com.srisu.srisu.navigation.graph.ChatNav
import com.srisu.srisu.features.coupleprofile.navigation.*
import com.srisu.srisu.theme.SettingsTokens as T
import com.srisu.srisu.theme.spacing
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpen: (Route) -> Unit, vm: SettingsViewModel = koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.reload() }
    SettingsContent(state, onBack, onOpen, vm::reload, vm::disconnect, vm::signOut, vm::saveNickname)
}

@Composable
internal fun SettingsContent(state: SettingsState, onBack: () -> Unit, onOpen: (Route) -> Unit,
    onRetry: () -> Unit = {}, onDisconnect: (SettingsPartner) -> Unit = {}, onSignOut: () -> Unit = {},
    onNickname: (SettingsPartner, String) -> Unit = { _, _ -> }) {
    var expanded by rememberSaveable(state.partner?.connectionId) { mutableStateOf(true) }
    var disconnect by remember { mutableStateOf<SettingsPartner?>(null) }
    var signOut by rememberSaveable { mutableStateOf(false) }
    var nicknameOpen by rememberSaveable(state.partner?.connectionId) { mutableStateOf(false) }
    var nicknameDraft by rememberSaveable(state.partner?.connectionId) { mutableStateOf("") }
    var savedAtOpen by remember { mutableLongStateOf(state.nicknameSaved) }
    val locked = state.busy || state.nicknameSaving
    LaunchedEffect(state.nicknameSaved) {
        if (nicknameOpen && state.nicknameSaved > savedAtOpen) nicknameOpen = false
    }
    LaunchedEffect(state.partner) {
        if (disconnect != null && disconnect != state.partner) disconnect = null
    }
    SettingsPage("Settings", onBack, listTag="settings-list") {
        item { SettingsSection("YOU") }
        item { SettingsRow("Account", Icons.Outlined.Person, onClick={onOpen(ProfileNav.SettingsDetail("account"))}) }
        item { SettingsRow("Notifications", Icons.Outlined.Notifications, onClick={onOpen(ProfileNav.NotificationSettings)}) }
        item { SettingsRow("Language", Icons.Outlined.Language, onClick={onOpen(ProfileNav.SettingsDetail("language"))}) }
        item { SettingsSection("TOGETHER", spaced=true) }
        item {
            Surface(color=MaterialTheme.colorScheme.selectedSurface, contentColor=MaterialTheme.colorScheme.onSurface, shape=MaterialTheme.shapes.medium) {
                Column {
                    val partner = state.partner
                    SettingsRow("Partner", Icons.Outlined.PeopleOutline,
                        subtitle=when {
                            state.loading -> "Checking your connection…"
                            !state.loaded -> "Connection unavailable"
                            partner == null -> "Not connected yet"
                            else -> "Partner profile, shared notifications"
                        }, filled=false, enabled=!state.loading && state.loaded && !locked,
                        action=if (state.loaded && partner == null) "Connect" else null,
                        expanded=if (partner != null) expanded else null,
                        onClick={if(partner == null) onOpen(ChatNav.PartnerFlow) else expanded = !expanded})
                    if(partner != null && expanded) Column(
                        Modifier.padding(start=T.gap, end=T.gap, bottom=T.gap), verticalArrangement=Arrangement.spacedBy(T.gap)) {
                        SettingsRow(partnerDisplayName(partner.userId, partner.nickname ?: partner.name, partner.coupleId), Icons.Outlined.Person,
                            subtitle=partner.connectedDays?.let { if(it == 1) "Connected · 1 day" else "Connected · $it days" } ?: "Connected",
                            avatar=partner.photo, person=true, enabled=state.loaded && !state.loading && !locked,
                            onClick={onOpen(ChatNav.PartnerPreview(partner.userId))})
                        SettingsRow("Nickname", Icons.Outlined.Edit,
                            subtitle=if(partner.nicknameRevision == null) "Available after the server update" else partner.nickname ?: "A name just for you",
                            enabled=state.loaded && !state.loading && !locked && partner.nicknameRevision != null,
                            onClick={nicknameDraft=partner.nickname.orEmpty();savedAtOpen=state.nicknameSaved;nicknameOpen=true})
                        SettingsRow("Shared notifications", Icons.Outlined.Notifications, enabled=state.loaded && !state.loading && !locked,
                            onClick={onOpen(ProfileNav.SharedNotifications)})
                        SettingsRow(if(state.busy) "Disconnecting…" else "Disconnect partner", Icons.Outlined.LinkOff,
                            enabled=state.loaded && !state.loading && !locked, onClick={disconnect=partner})
                    }
                }
            }
        }
        if(state.error != null) item {
            SettingsNotice(state.error, true)
            TextButton(onClick=onRetry, enabled=!locked && !state.loading) { Text("Retry connection") }
        }
        if(state.notice != null) item {SettingsNotice(state.notice)}
        item { SettingsRow("Shared preferences", Icons.Outlined.Tune, "Plans, reminders, moments",
            onClick={onOpen(ProfileNav.SettingsDetail("shared"))}) }
        item { SettingsSection("APP", spaced=true) }
        item { SettingsRow("Downloads", Icons.Outlined.FileDownload, onClick={onOpen(ProfileNav.SettingsDetail("downloads"))}) }
        item { SettingsRow("Appearance", Icons.Outlined.Contrast, onClick={onOpen(ProfileNav.SettingsDetail("appearance"))}) }
        item { SettingsRow("Accessibility", Icons.Outlined.AccessibilityNew, onClick={onOpen(ProfileNav.SettingsDetail("accessibility"))}) }
        item { SettingsRow("Support", Icons.Outlined.HelpOutline, onClick={onOpen(ProfileNav.SettingsDetail("support"))}) }
        item { SettingsRow("Privacy & Security", Icons.Outlined.VerifiedUser, onClick={onOpen(ProfileNav.SettingsDetail("privacy"))}) }
        item {
            TextButton(onClick={signOut=true}, modifier=Modifier.fillMaxWidth().padding(top=T.gap), enabled=!locked) {
                Text("Sign out", color=MaterialTheme.colorScheme.error)
            }
        }
    }
    disconnect?.let { expected ->
        AlertDialog(onDismissRequest={disconnect=null}, title={Text("Disconnect partner?")},
            text={Text("This ends your connection with ${expected.name} and access to your shared space. It does not delete either account or erase copies already saved on a device.")},
            confirmButton={TextButton(enabled=state.loaded && !state.loading && !locked && expected == state.partner,
                onClick={disconnect=null;onDisconnect(expected)}) {Text("Disconnect",color=MaterialTheme.colorScheme.error)}},
            dismissButton={TextButton(onClick={disconnect=null}) {Text("Keep connected")}})
    }
    if(signOut) AlertDialog(onDismissRequest={signOut=false},title={Text("Sign out?")},
        text={Text("You’ll need to verify your phone number to sign in again. Your account and shared content stay saved.")},
        confirmButton={TextButton(onClick={signOut=false;onSignOut()}) {Text("Sign out")}},
        dismissButton={TextButton(onClick={signOut=false}) {Text("Cancel")}})
    if(nicknameOpen && state.partner != null) {
        val partner=state.partner
        val validation=nicknameValidation(nicknameDraft)
        val canSave=state.loaded && !state.loading && !locked && validation == null &&
            nicknameDraft.trim() != partner.nickname.orEmpty() && partner.nicknameRevision != null
        AlertDialog(onDismissRequest={if(!state.nicknameSaving) nicknameOpen=false},
            title={Text("Nickname for ${partner.name}")},
            text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(T.gap)) {
                Text("Only you see this nickname. It syncs to your devices and appears across your shared space.",
                    style=MaterialTheme.typography.bodyMedium)
                OutlinedTextField(value=nicknameDraft,onValueChange={nicknameDraft=it},singleLine=true,
                    enabled=!state.nicknameSaving, label={Text("Nickname")},
                    modifier=Modifier.fillMaxWidth().testTag("partner-nickname"),
                    isError=validation != null, supportingText={Text(validation ?: "${nicknameLength(nicknameDraft.trim())}/40")},
                    keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),
                    keyboardActions=KeyboardActions(onDone={if(canSave) onNickname(partner,nicknameDraft)}))
                state.nicknameError?.let {SettingsNotice(it,true)}
                if(!state.loaded) TextButton(onClick=onRetry,enabled=!state.loading && !locked) {Text("Retry connection")}
                if(!partner.nickname.isNullOrBlank()) TextButton(onClick={nicknameDraft="";onNickname(partner,"")},
                    enabled=state.loaded && !state.loading && !locked) {Text("Remove nickname")}
            }},
            confirmButton={TextButton(onClick={onNickname(partner,nicknameDraft)},enabled=canSave) {
                Text(if(state.nicknameSaving) "Saving…" else "Save")
            }},
            dismissButton={TextButton(onClick={nicknameOpen=false},enabled=!state.nicknameSaving) {Text("Cancel")}})
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsPage(title: String, onBack: () -> Unit, listTag: String="settings-detail-list",
    content: LazyListScope.() -> Unit) {
    Scaffold(containerColor=MaterialTheme.colorScheme.background, topBar={
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val density = LocalDensity.current
            val measured = rememberTextMeasurer().measure(title, MaterialTheme.typography.titleLarge,
                constraints=Constraints(maxWidth=with(density) {(maxWidth-96.dp).roundToPx().coerceAtLeast(1)}))
            val height = with(density) {measured.size.height.toDp()} + MaterialTheme.spacing.medium
            CenterAlignedTopAppBar(title={Text(title,style=MaterialTheme.typography.titleLarge)},
                expandedHeight=maxOf(64.dp,height), navigationIcon={NavigationBackButton(onBack)},
                colors=TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor=MaterialTheme.colorScheme.background))
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentAlignment=Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max=T.contentWidth).fillMaxSize().testTag(listTag),
                contentPadding=PaddingValues(start=T.gutter,end=T.gutter,top=MaterialTheme.spacing.large,bottom=MaterialTheme.spacing.large),
                verticalArrangement=Arrangement.spacedBy(T.gap),content=content)
        }
    }
}

@Composable internal fun SettingsSection(text: String, spaced: Boolean=false) {
    Text(text, style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.onSurfaceVariant,
        modifier=Modifier.padding(start=4.dp,top=if(spaced) T.sectionGap else 0.dp,bottom=4.dp).semantics {heading()})
}

@Composable internal fun SettingsNotice(text: String, error: Boolean=false) {
    Text(text, style=MaterialTheme.typography.bodyMedium,
        color=if(error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier=Modifier.padding(horizontal=4.dp,vertical=T.gap))
}

@Composable internal fun SettingsRow(title: String, icon: ImageVector, subtitle: String?=null,
    action: String?=null, expanded: Boolean?=null, avatar: String?=null, person: Boolean=false,
    enabled: Boolean=true, filled: Boolean=true, onClick: () -> Unit) {
    val stackAction = LocalDensity.current.fontScale > 1.3f && action != null
    Surface(onClick=onClick, enabled=enabled, shape=MaterialTheme.shapes.medium,
        contentColor=MaterialTheme.colorScheme.onSurface, color=if(filled) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.selectedSurface,
        modifier=Modifier.fillMaxWidth().semantics {
            if(expanded != null) stateDescription=if(expanded) "Expanded" else "Collapsed"
        }) {
        Row(Modifier.heightIn(min=T.rowHeight).padding(horizontal=T.rowPadding,vertical=MaterialTheme.spacing.compact),
            verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.compact)) {
            if(person) Surface(Modifier.size(T.avatar),shape=CircleShape,color=MaterialTheme.colorScheme.selectedSurface) {
                Box(contentAlignment=Alignment.Center) {
                    Icon(icon,null,Modifier.size(T.icon))
                    if(avatar != null) AsyncImage(avatar,null,Modifier.fillMaxSize().clip(CircleShape),contentScale=ContentScale.Crop)
                }
            } else Icon(icon,null,Modifier.size(T.icon),tint=MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Text(title,style=MaterialTheme.typography.bodyLarge)
                subtitle?.let {Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                if(stackAction) Text(action!!,style=MaterialTheme.typography.labelLarge)
            }
            when {
                stackAction -> Unit
                action != null -> Text(action,style=MaterialTheme.typography.labelMedium)
                expanded != null -> Icon(if(expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,null,Modifier.size(T.icon))
                else -> Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight,null,Modifier.size(T.icon),tint=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
