package com.srisu.srisu.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

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
    val editor = rememberDraftEditor(value)
    OutlinedTextField(
        value = editor.value,
        onValueChange = { next ->
            if (next.text.length <= maxLength && editor.edit(next)) onValueChange(next.text)
        },
        modifier = modifier,
        enabled = enabled,
        label = label,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
    )
}
