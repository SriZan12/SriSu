package com.srisu.srisu.components

import androidx.compose.runtime.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/** IME edits are immediate; a delayed model acknowledgement must not replace newer typing. */
@Stable
internal class DraftEditor(initial: String) {
    var value by mutableStateOf(TextFieldValue(initial, TextRange(initial.length)))
        private set
    private val pending = mutableListOf<String>()

    fun edit(next: TextFieldValue): Boolean {
        val changed = value.text != next.text
        value = next
        if (changed) pending += next.text
        return changed
    }

    fun acceptModel(text: String) {
        val acknowledgement = pending.indexOfLast { it == text }
        if (acknowledgement >= 0) {
            pending.subList(0, acknowledgement + 1).clear()
            return
        }
        pending.clear()
        if (value.text != text) value = TextFieldValue(text, TextRange(text.length))
    }
}

@Composable
internal fun rememberDraftEditor(text: String): DraftEditor {
    val editor = remember { DraftEditor(text) }
    // External replacements (restoration, suggestion, clear) still reach the editor.
    LaunchedEffect(text) { editor.acceptModel(text) }
    return editor
}
