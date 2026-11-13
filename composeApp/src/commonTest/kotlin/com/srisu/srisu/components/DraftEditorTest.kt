package com.srisu.srisu.components

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.*

class DraftEditorTest {
    @Test fun delayedAcknowledgementPreservesNewerTextSelectionAndComposition() {
        val editor = DraftEditor("")
        editor.edit(TextFieldValue("co", TextRange(2)))
        val composing = TextFieldValue("coffee", TextRange(3), TextRange(0, 6))
        editor.edit(composing)
        editor.acceptModel("co")
        assertEquals(composing, editor.value)
        editor.acceptModel("coffee")
        assertEquals(composing, editor.value)
    }

    @Test fun conflatedAcknowledgementStillAllowsReplacementAndSendClear() {
        val editor = DraftEditor("")
        listOf("c", "co", "coffee").forEach { editor.edit(TextFieldValue(it, TextRange(it.length))) }
        editor.acceptModel("coffee")
        editor.acceptModel("Walk together")
        assertEquals(TextFieldValue("Walk together", TextRange(13)), editor.value)
        editor.acceptModel("")
        assertEquals(TextFieldValue("", TextRange(0)), editor.value)
    }

    @Test fun selectionOnlyChangeDoesNotGenerateAnotherTextEdit() {
        val editor = DraftEditor("coffee")
        assertFalse(editor.edit(TextFieldValue("coffee", TextRange(2))))
        editor.acceptModel("coffee")
        assertEquals(TextRange(2), editor.value.selection)
    }
}
