package com.srisu.srisu.components

import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.srisu.srisu.theme.SriSuTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DraftOutlinedTextFieldTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun modelEchoKeepsCaretWhileReplacementAndClearUpdateTheEditor() {
        val text = mutableStateOf("")
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalInspectionMode provides true) { org.jetbrains.compose.resources.PreviewContextConfigurationEffect() }
            SriSuTheme {
            DraftOutlinedTextField(text.value, { text.value = it }, label = { Text("Draft") })
        } }
        val field = compose.onNodeWithText("Draft")
        field.performClick()
        for (letter in "test") field.performTextInput(letter.toString())
        compose.runOnIdle { assertEquals("test", text.value) }
        field.performTextInputSelection(TextRange(1))
        field.performTextInput("X")
        compose.runOnIdle { assertEquals("tXest", text.value) }
        compose.runOnIdle { text.value = "Idea" }
        field.assertTextContains("Idea")
        field.performTextInput("!")
        compose.runOnIdle { assertEquals("Idea!", text.value); text.value = "" }
        compose.waitForIdle()
        field.performTextInput("New")
        compose.runOnIdle { assertEquals("New", text.value) }
    }

    @Test fun lengthRejectionKeepsTheLastAcceptedDraft() {
        val text = mutableStateOf("test")
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalInspectionMode provides true) { org.jetbrains.compose.resources.PreviewContextConfigurationEffect() }
            SriSuTheme {
            DraftOutlinedTextField(text.value, { text.value = it }, label = { Text("Draft") }, maxLength = 4)
        } }
        compose.onNodeWithText("Draft").performClick().performTextInput(" extra")
        compose.runOnIdle { assertEquals("test", text.value) }
        compose.onNodeWithText("Draft").assertTextContains("test")
    }
}
