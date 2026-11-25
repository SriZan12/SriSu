package com.srisu.srisu.features.coupleprofile.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srisu.srisu.features.settings.*
import com.srisu.srisu.theme.SettingsTokens
import com.srisu.srisu.theme.spacing
import org.koin.compose.viewmodel.koinViewModel

/** Created only in an owned profile, scoped to its navigation entry's ViewModelStore. */
@Composable
internal fun CoupleProfileNickname(coupleId: Long, vm: SettingsViewModel = koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(coupleId) { vm.reload() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.reload() }
    CoupleProfileNicknameContent(coupleId, state, vm::saveNickname, vm::reload)
}

@Composable
internal fun CoupleProfileNicknameContent(
    coupleId: Long, state: SettingsState,
    onSave: (SettingsPartner, String) -> Unit, onRetry: () -> Unit,
) {
    // A visited, stale or replaced couple must never expose the current partner's editor.
    if (state.partner != null && state.partner.coupleId != coupleId) return
    if (state.loaded && state.partner == null) return
    PartnerNicknameEditorHost(state, onSave, onRetry) { openEditor ->
        val partner = state.partner
        val unavailable = !state.loaded && !state.loading
        val enabled = !state.loading && !state.busy && !state.nicknameSaving &&
            (unavailable || (state.loaded && partner?.nicknameRevision != null))
        Surface(
            onClick = if (unavailable) onRetry else openEditor,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().testTag("couple-profile-nickname"),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Row(
                Modifier.heightIn(min = SettingsTokens.rowHeight).padding(SettingsTokens.rowPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            ) {
                Icon(Icons.Outlined.Edit, null, Modifier.size(SettingsTokens.icon), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.tiny)) {
                    Text("Partner nickname", style = MaterialTheme.typography.bodyMedium)
                    Text(when {
                        state.loading -> "Checking your connection…"
                        unavailable -> "Couldn’t load nickname. Tap to retry."
                        partner?.nicknameRevision == null -> "Available after the server update"
                        else -> partner.nickname ?: "Add a nickname for ${partner.name}"
                    }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
