package com.srisu.srisu.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/** Keeps IME selection/composition local while the ViewModel owns the saved text. */
@Composable
internal fun DraftOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: @Composable (() -> Unit)? = null,
    maxLines: Int = Int.MAX_VALUE,
    maxLength: Int = Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    var editor by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    // Echoes of local edits must not replace the IME's selection or composing range.
    // A real model replacement (idea chip, restored draft, successful send) still wins.
    LaunchedEffect(value) {
        if (editor.text != value) editor = TextFieldValue(value, TextRange(value.length))
    }
    OutlinedTextField(
        value = editor,
        onValueChange = { next ->
            if (next.text.length <= maxLength) {
                val textChanged = next.text != editor.text
                editor = next
                if (textChanged) onValueChange(next.text)
            }
        },
        modifier = modifier,
        enabled = enabled,
        label = label,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
    )
}
