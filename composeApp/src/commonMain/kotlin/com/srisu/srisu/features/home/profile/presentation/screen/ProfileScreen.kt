package com.srisu.srisu.features.home.profile.presentation.screen

import com.srisu.srisu.components.NavigationBackButton
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import com.srisu.srisu.components.SriSuButton
import com.srisu.srisu.components.SriSuButtonVariant
import com.srisu.srisu.features.auth.data.remote.response.User
import com.srisu.srisu.features.home.profile.presentation.state.ProfileUIState
import com.srisu.srisu.features.home.profile.presentation.vm.ProfileViewModel
import com.srisu.srisu.theme.PartnerLinkTokens
import com.srisu.srisu.theme.SriSuPartnerLinkTypography
import com.srisu.srisu.theme.pill
import com.srisu.srisu.theme.spacing
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import srisu.composeapp.generated.resources.*

@Composable
fun ProfileScreen(
    profileViewModel: ProfileViewModel = koinViewModel(), onBack: () -> Unit,
    actionBusy: Boolean = false, canConnect: Boolean = true, actionError: String? = null,
    onAction: (User, String) -> Unit = { _, _ -> }, onRetryAction: () -> Unit = {},
) {
    val state by profileViewModel.profileUIState.collectAsStateWithLifecycle()
    PartnerProfileContent(state, onBack, profileViewModel::retry, actionBusy, canConnect,
        actionError, onAction, onRetryAction)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun PartnerProfileContent(
    state: ProfileUIState, onBack: () -> Unit, onRetry: () -> Unit = {},
    actionBusy: Boolean = false, canConnect: Boolean = true, actionError: String? = null,
    onAction: (User, String) -> Unit = { _, _ -> }, onRetryAction: () -> Unit = {},
) {
    MaterialTheme(typography = SriSuPartnerLinkTypography()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(stringResource(Res.string.partner_preview_title), style = MaterialTheme.typography.titleMedium) },
                    navigationIcon = { NavigationBackButton(onBack) },
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    Modifier.widthIn(max = PartnerLinkTokens.contentMaxWidth).fillMaxSize(),
                    contentPadding = PaddingValues(MaterialTheme.spacing.gutter),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
                ) {
                    val user = state.userProfileData
                    when {
                        state.isLoading -> item {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.compact)) {
                                CircularProgressIndicator(Modifier.size(MaterialTheme.spacing.chromeIcon))
                                Text(stringResource(Res.string.partner_preview_loading), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        user == null -> item {
                            val kind = state.error?.kind
                            val unavailable = kind == null || kind == com.srisu.srisu.core.data.remote.NetworkAPIResult.ErrorType.NOT_FOUND ||
                                kind == com.srisu.srisu.core.data.remote.NetworkAPIResult.ErrorType.FORBIDDEN
                            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.compact)) {
                                Text(stringResource(if (unavailable) Res.string.partner_preview_unavailable else Res.string.partner_profile_load_error),
                                    style = MaterialTheme.typography.titleLarge)
                                Text(if (unavailable) stringResource(Res.string.partner_preview_unavailable_body)
                                    else state.error?.message.orEmpty(), style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (!unavailable && kind != com.srisu.srisu.core.data.remote.NetworkAPIResult.ErrorType.UNAUTHORIZED)
                                    SriSuButton(stringResource(Res.string.partner_preview_retry), onRetry, variant = SriSuButtonVariant.Outline)
                                SriSuButton(stringResource(Res.string.partner_preview_back), onBack, variant = SriSuButtonVariant.Outline)
                            }
                        }
                        else -> {
                            item(key = "identity") { ProfileIdentity(user) }
                            item(key = "connection") {
                                Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                                    val label = when (state.connectionState) {
                                        "available" -> Res.string.partner_profile_connect
                                        "outgoing" -> Res.string.partner_profile_request_sent
                                        "incoming" -> Res.string.partner_profile_view_invitation
                                        "connected" -> Res.string.partner_profile_connected
                                        else -> null
                                    }
                                    label?.let {
                                        SriSuButton(stringResource(if (actionBusy) Res.string.partner_profile_working else it),
                                            onClick = { onAction(user, state.connectionState) }, modifier = Modifier.fillMaxWidth(),
                                            enabled = !actionBusy && (state.connectionState != "available" || canConnect))
                                    }
                                    if (state.connectionState == "available") Text(stringResource(Res.string.partner_profile_connect_hint),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (state.connectionState == "unavailable") Text(stringResource(Res.string.partner_profile_connection_unavailable),
                                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    actionError?.let { message ->
                                        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                                        TextButton(onClick = onRetryAction, enabled = !actionBusy) { Text(stringResource(Res.string.partner_profile_check_invitation)) }
                                    }
                                }
                            }
                            val bio = user.bio?.trim()?.takeIf { it.isNotEmpty() && state.detailsVisible }
                            val interests = if (state.detailsVisible) user.userInterests.orEmpty().filterNotNull().filter { it.removed != true }
                                .mapNotNull { it.name?.trim()?.takeIf(String::isNotEmpty) }.distinct() else emptyList()
                            if (bio != null) item(key = "about") {
                                ProfileSection(stringResource(Res.string.partner_preview_about)) {
                                    Text(bio, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                            if (interests.isNotEmpty()) item(key = "interests") {
                                ProfileSection(stringResource(Res.string.partner_preview_interests)) {
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                                        interests.forEach { interest ->
                                            Surface(shape = MaterialTheme.shapes.pill, color = MaterialTheme.colorScheme.surfaceContainer) {
                                                Text(interest, style = MaterialTheme.typography.bodyMedium,
                                                    modifier = Modifier.padding(horizontal = MaterialTheme.spacing.compact, vertical = MaterialTheme.spacing.small))
                                            }
                                        }
                                    }
                                }
                            }
                            if (!state.detailsVisible || (bio == null && interests.isEmpty())) item(key = "details_notice") {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Text(stringResource(if (state.detailsVisible) Res.string.partner_profile_no_details else Res.string.partner_profile_private_details),
                                    modifier = Modifier.padding(top = MaterialTheme.spacing.medium),
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileIdentity(user: User) {
    val name = user.fullName?.trim()?.takeIf(String::isNotEmpty)
        ?: user.username?.trim()?.takeIf(String::isNotEmpty)
        ?: stringResource(Res.string.partner_preview_person)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked = maxWidth < PartnerLinkTokens.previewStackWidth || LocalDensity.current.fontScale > 1.3f
        val photo: @Composable () -> Unit = {
            ProfilePhoto(user.profilePhoto, stringResource(Res.string.partner_preview_named_photo, name),
                Modifier.size(PartnerLinkTokens.previewAvatar).clip(CircleShape))
        }
        val identity: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.tiny)) {
                Text(name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                user.username?.trim()?.takeIf { it.isNotEmpty() && it != name }?.let {
                    Text("@${it.removePrefix("@")}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

            }
        }
        if (stacked) Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) { photo(); identity() }
        else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
            photo()
            Box(Modifier.weight(1f)) { identity() }
        }
    }
}

@Composable
private fun ProfileSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.compact)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        content()
    }
}

@Composable
private fun ProfilePhoto(url: String?, description: String, modifier: Modifier, portrait: Boolean = true) {
    var retry by remember(url) { mutableIntStateOf(0) }
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceContainer) {
        if (url.isNullOrBlank()) {
            Box(contentAlignment = Alignment.Center) {
                Icon(if (portrait) Icons.Default.Person else Icons.Default.Photo,
                    stringResource(Res.string.partner_preview_no_photo), Modifier.size(MaterialTheme.spacing.extraLarge),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else key(url, retry) {
            SubcomposeAsyncImage(model = url, contentDescription = description,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter,
                loading = {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(MaterialTheme.spacing.icon))
                    }
                },
                error = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.Photo, stringResource(Res.string.partner_preview_photo_error), Modifier.size(MaterialTheme.spacing.icon))
                        TextButton(onClick = { retry++ }) { Text(stringResource(Res.string.partner_preview_retry), style = MaterialTheme.typography.labelMedium) }
                    }
                },
                success = { SubcomposeAsyncImageContent() },
            )
        }
    }
}
