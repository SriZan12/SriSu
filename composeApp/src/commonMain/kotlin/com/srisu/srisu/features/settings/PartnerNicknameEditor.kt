package com.srisu.srisu.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import com.srisu.srisu.core.identity.nicknameLength
import com.srisu.srisu.core.identity.nicknameValidation
import com.srisu.srisu.theme.SettingsTokens

/** Shared by Settings and the owner's couple profile; neither surface owns a second save flow. */
@Composable
internal fun PartnerNicknameEditorHost(
    state: SettingsState,
    onSave: (SettingsPartner, String) -> Unit,
    onRetry: () -> Unit,
    content: @Composable (openEditor: () -> Unit) -> Unit,
) {
    val partner = state.partner
    var open by rememberSaveable(partner?.connectionId, partner?.userId) { mutableStateOf(false) }
    var draft by rememberSaveable(partner?.connectionId, partner?.userId) { mutableStateOf("") }
    var savedAtOpen by rememberSaveable(partner?.connectionId, partner?.userId) { mutableLongStateOf(state.nicknameSaved) }
    val locked = state.busy || state.nicknameSaving
    LaunchedEffect(state.nicknameSaved) {
        if (open && state.nicknameSaved > savedAtOpen) open = false
    }
    content {
        if (partner != null && state.loaded && !state.loading && !locked && partner.nicknameRevision != null) {
            draft = partner.nickname.orEmpty()
            savedAtOpen = state.nicknameSaved
            open = true
        }
    }
    if (!open || partner == null) return
    val validation = nicknameValidation(draft)
    val canSave = state.loaded && !state.loading && !locked && validation == null &&
        draft.trim() != partner.nickname.orEmpty() && partner.nicknameRevision != null
    AlertDialog(
        onDismissRequest = { if (!state.nicknameSaving) open = false },
        title = { Text("Partner nickname") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(SettingsTokens.gap)) {
                Text("This changes how their name appears to you across SriSu. When you save a new nickname, your partner gets a notification showing it.",
                    style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = draft, onValueChange = { draft = it }, singleLine = true,
                    enabled = !state.nicknameSaving, label = { Text("Nickname") },
                    modifier = Modifier.fillMaxWidth().testTag("partner-nickname"),
                    isError = validation != null,
                    supportingText = { Text(validation ?: "${nicknameLength(draft.trim())}/40") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (canSave) onSave(partner, draft) }),
                )
                state.nicknameError?.let { SettingsNotice(it, true) }
                if (!state.loaded) TextButton(onClick = onRetry, enabled = !state.loading && !locked) { Text("Retry connection") }
                if (!partner.nickname.isNullOrBlank()) TextButton(
                    onClick = { draft = ""; onSave(partner, "") }, enabled = state.loaded && !state.loading && !locked,
                ) { Text("Remove nickname") }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(partner, draft) }, enabled = canSave) { Text(if (state.nicknameSaving) "Saving…" else "Save") } },
        dismissButton = { TextButton(onClick = { open = false }, enabled = !state.nicknameSaving) { Text("Cancel") } },
    )
}
