package com.srisu.srisu.features.sparks.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.srisu.srisu.features.chat.matrix.SparkRecoveryStatus
import com.srisu.srisu.theme.spacing

// Recovery secrets are ephemeral UI state, never saved-state bundles/navigation/logs.
data class SparkRecoveryUi(val open: Boolean = false, val status: SparkRecoveryStatus? = null,
    val creating: Boolean = false, val busy: Boolean = false, val key: String = "", val confirmation: String = "",
    val message: String? = null, val error: String? = null) {
    override fun toString() = "SparkRecoveryUi(status=$status, busy=$busy)"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun SparkRecoveryContent(ui: SparkRecoveryUi, secure: Boolean,
    onBack: () -> Unit, onGenerate: () -> Unit, onExisting: () -> Unit,
    onKey: (String) -> Unit, onConfirm: (String) -> Unit, onEnable: () -> Unit,
    onUnlock: () -> Unit, onBackup: () -> Unit, onRetry: () -> Unit) {
    val space = MaterialTheme.spacing
    val focus = LocalFocusManager.current
    Scaffold(topBar = { TopAppBar(title = { Text("Answer recovery") }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to Sparks") }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()
            .verticalScroll(rememberScrollState()).padding(space.gutter), verticalArrangement = Arrangement.spacedBy(space.medium)) {
            Icon(Icons.Outlined.Lock, null)
            Text("Keep your answers with you", style = MaterialTheme.typography.headlineMedium)
            Text("Save an encrypted backup for your next phone. Your recovery key unlocks it; SriSu cannot read your answers or recover a lost key.", style = MaterialTheme.typography.bodyLarge)
            if (ui.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (!secure) Text("Connect to secure chat to continue.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            when {
                ui.creating -> {
                    Text("1. Save this recovery key", style = MaterialTheme.typography.titleMedium)
                    Text("Store it in your password manager or another safe place outside this phone. Anyone with this key and access to your account can unlock your saved answers.")
                    SelectionContainer { Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
                        Text(ui.key, Modifier.fillMaxWidth().padding(space.medium), style = MaterialTheme.typography.bodyLarge)
                    } }
                    Text("2. Confirm your saved copy", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(ui.confirmation, onConfirm, Modifier.fillMaxWidth(), enabled = !ui.busy,
                        isError = ui.error != null,
                        label = { Text("Enter your saved recovery key") }, visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password))
                    Button({ focus.clearFocus(); onEnable() }, Modifier.fillMaxWidth(), enabled = secure && !ui.busy && ui.key.isNotBlank() &&
                        ui.key.filterNot(Char::isWhitespace) == ui.confirmation.filterNot(Char::isWhitespace)) { Text("Enable encrypted backup") }
                }
                ui.status == SparkRecoveryStatus.Pending -> {
                    Text("Finish setting up your backup", style = MaterialTheme.typography.titleMedium)
                    Text("Your saved recovery key is kept securely on this phone. Retry to finish the interrupted setup using the same key.")
                    Button(onBackup, Modifier.fillMaxWidth(), enabled = secure && !ui.busy) { Text("Retry backup setup") }
                }
                ui.status == SparkRecoveryStatus.Ready -> {
                    Text("Recovery unlocked", style = MaterialTheme.typography.titleMedium)
                    Text("Answers are saved only after this account securely opens them. Run a full backup before changing phones and keep your recovery key.")
                    Button(onBackup, Modifier.fillMaxWidth(), enabled = secure && !ui.busy) { Text("Back up all answers") }
                }
                ui.status == SparkRecoveryStatus.Locked -> {
                    Text("Unlock on this phone", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(ui.key, onKey, Modifier.fillMaxWidth(), enabled = !ui.busy,
                        isError = ui.error != null,
                        supportingText = if (ui.error != null) ({ Text("Couldn’t unlock. Check your recovery key and connection, then retry.") }) else null,
                        label = { Text("Recovery key") }, visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password))
                    Button({ focus.clearFocus(); onUnlock() }, Modifier.fillMaxWidth(), enabled = secure && !ui.busy && ui.key.isNotBlank()) { Text("Recover answers") }
                    Text("This is separate from your sign-in code. If you haven’t made a backup, use a phone that still opens your old answers to set one up.", style = MaterialTheme.typography.bodyMedium)
                }
                ui.status == SparkRecoveryStatus.NotSetUp -> {
                    Button(onGenerate, Modifier.fillMaxWidth(), enabled = secure && !ui.busy) { Text("Create recovery key") }
                    TextButton(onExisting, Modifier.fillMaxWidth(), enabled = !ui.busy) { Text("I already have a recovery key") }
                    Text("Set this up on a phone that can open your existing answers. Missing answers cannot be recreated from a sign-in code.", style = MaterialTheme.typography.bodyMedium)
                }
                !ui.busy -> TextButton(onRetry) { Text("Check recovery status") }
            }
            ui.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (ui.status != SparkRecoveryStatus.Locked) ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
