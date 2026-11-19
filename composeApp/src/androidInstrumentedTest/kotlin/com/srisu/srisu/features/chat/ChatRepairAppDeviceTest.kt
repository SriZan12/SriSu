package com.srisu.srisu.features.chat

import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.liftric.kvault.KVault
import com.srisu.srisu.MainActivity
import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.utils.Constants
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Real installed-app keyboard/navigation checks, restricted to isolated synthetic accounts. */
class ChatRepairAppDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun waitFor(matcher: SemanticsMatcher) = compose.waitUntil(60_000) {
        compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
    }
    private fun keyboardShown() = android.os.ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand("dumpsys input_method")
    ).bufferedReader().use { it.readText().contains("mInputShown=true") }
    private fun capture(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.filesDir, "chat-repair-$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
    @Test fun editingDeletionKeyboardAndScrolling() {
        val context = instrumentation.targetContext
        val fixtureFile = File(context.filesDir, "chat-app-fixture.json")
        assumeTrue("Requires isolated chat QA fixture", fixtureFile.isFile)
        val fixture = JSONObject(fixtureFile.readText())
        require(fixture.getString("scope") == "isolated-core-matrix-e2e")
        require(fixture.getString("base_url") == "http://127.0.0.1:8029/")
        require(ApiEnvironment.configured().baseUrl == fixture.getString("base_url"))
        val session = fixture.getJSONArray("users").getJSONObject(0)
        require(session.getLong("id") == 82000000000000001L)
        val vault = KVault(context, Constants.Auth.SESSION_FILE)
        vault.string(Constants.Auth.SESSION_KEY)?.let { require(JSONObject(it).optLong("id").toString().startsWith("8200000000000000")) }
        assertTrue(vault.set(Constants.Auth.SESSION_KEY, session.toString()))
        val body = "Synthetic native check ${System.nanoTime()}"
        val editedBody = "$body edited"
        try { ActivityScenario.launch(MainActivity::class.java).use {
            waitFor(hasText("Chat", substring = false))
            if (compose.onAllNodesWithText("Not now").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText("Not now").performClick()
            compose.onNodeWithText("Chat", useUnmergedTree = true).performClick()
            waitFor(hasContentDescription("Message") and hasSetTextAction())
            val input = compose.onNode(hasContentDescription("Message") and hasSetTextAction())
            input.performTouchInput { click() }; input.performTextInput(body)
            compose.waitUntil(10_000) { keyboardShown() || compose.onAllNodesWithText("Not now").fetchSemanticsNodes().isNotEmpty() }
            if (compose.onAllNodesWithText("Not now").fetchSemanticsNodes().isNotEmpty()) {
                compose.onNodeWithText("Not now").performClick(); input.performTouchInput { click() }
            }
            compose.waitUntil(10_000) { keyboardShown() }
            capture("composer")
            compose.onNodeWithContentDescription("Send message").assertIsDisplayed()
            compose.waitUntil(60_000) { !compose.onNodeWithContentDescription("Send message").fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) }
            compose.onNodeWithContentDescription("Send message").performClick()
            waitFor(hasText(body) and !hasSetTextAction())
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            compose.waitUntil(10_000) { !keyboardShown() }
            compose.onNodeWithText(body).performTouchInput { longClick() }
            waitFor(hasText("Edit text"))
            capture("options")
            compose.onNodeWithText("Edit text").performClick()
            waitFor(hasText("Editing message"))
            compose.onNode(hasSetTextAction() and hasText(body)).performClick()
                .performTextReplacement(editedBody)
            compose.waitUntil(10_000) { keyboardShown() }
            compose.onNodeWithContentDescription("Save edit").assertIsDisplayed()
            capture("edit-keyboard")
            compose.onNodeWithContentDescription("Save edit").performClick()
            waitFor(hasText(editedBody) and !hasSetTextAction())
            assertTrue(compose.onAllNodesWithText("Edited", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
            input.performTouchInput { click() }; input.performTextInput("Draft survives menu and back")
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            compose.waitUntil(10_000) { !keyboardShown() }
            compose.onNodeWithText(editedBody).performTouchInput { longClick() }
            compose.onNodeWithText("Delete for everyone").performClick()
            compose.onNodeWithText("Delete").performClick()
            compose.waitUntil(30_000) { compose.onAllNodesWithText(editedBody).fetchSemanticsNodes().isEmpty() }
            assertTrue(compose.onAllNodesWithText("This message was deleted").fetchSemanticsNodes().isNotEmpty())
            input.assertTextContains("Draft survives menu and back")
            repeat(4) { compose.onNode(hasScrollAction()).performTouchInput { swipeDown(); swipeUp() } }
            input.assertIsDisplayed()
            capture("after-delete")
            input.performTextClearance()
        } } finally { vault.clear(); fixtureFile.delete() }
    }
}
