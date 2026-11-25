package com.srisu.srisu.features.couplechat.presentation

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.srisu.srisu.features.chat.matrix.MATRIX_TEXT_MAX_PLAINTEXT_BYTES
import com.srisu.srisu.features.couplechat.data.ConversationState
import com.srisu.srisu.theme.SriSuTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ConversationComposerInputTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun delayedEchoKeepsNewTypingAndSuccessfulSendClearsTheEditor() {
        val state = mutableStateOf(ConversationState())
        val edits = mutableListOf<String>()
        compose.setContent {
            SriSuTheme {
                ConversationComposer(state.value, false, false, edits::add,
                    onSend = { state.value = state.value.copy(draft = "") },
                    onAttach = {}, onCancelReply = {}, voice = { Text("Record voice") })
            }
        }
        val field = compose.onNodeWithContentDescription("Message")
        field.performClick().performTextInput("co")
        field.performTextInput("ffee")
        compose.runOnIdle { state.value = state.value.copy(draft = edits.first()) }
        field.assertTextEquals("coffee")
        compose.runOnIdle { state.value = state.value.copy(draft = edits.last()) }
        compose.onNodeWithContentDescription("Send message").performClick()
        field.assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.EditableText,
            androidx.compose.ui.text.AnnotatedString("")
        ))
        compose.onNodeWithText("Record voice").assertExists()
        compose.runOnIdle { state.value = state.value.copy(draft = "A Home suggestion") }
        field.assertTextEquals("A Home suggestion")
    }

    @Test fun rejectedUtf8InputKeepsAcceptedTextAndStillRequestsValidationFeedback() {
        val state = mutableStateOf(ConversationState(draft = "coffee"))
        var rejected = false
        compose.setContent {
            SriSuTheme {
                ConversationComposer(state.value, false, false, onDraft = { text ->
                    if (text.encodeToByteArray().size > MATRIX_TEXT_MAX_PLAINTEXT_BYTES) rejected = true
                    else state.value = state.value.copy(draft = text)
                }, onSend = {}, onAttach = {}, onCancelReply = {}, voice = {})
            }
        }
        val field = compose.onNodeWithContentDescription("Message")
        field.performClick().performTextInput("❤".repeat(MATRIX_TEXT_MAX_PLAINTEXT_BYTES / 3))
        field.assertTextEquals("coffee")
        compose.runOnIdle { assertTrue(rejected); assertEquals("coffee", state.value.draft) }
        field.performTextInput(" together")
        field.assertTextEquals("coffee together")
    }
}
