package com.srisu.srisu.features.home

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

/** Opt-in installed-app journey. Only the isolated localhost Home QA account is accepted.
 * No account override or fixture reader is included in the production app. */
class HomeAppDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun waitFor(matcher: SemanticsMatcher) {
        compose.waitUntil(30_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun back() {
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
    }
    private fun keyboardShown(): Boolean = android.os.ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand("dumpsys input_method")
    ).bufferedReader().use { it.readText().contains("mInputShown=true") }

    @Test fun homePlanAndChatKeepDraftsAndClearTheKeyboard() {
        val context = instrumentation.targetContext
        val fixtureFile = File(context.filesDir, "home-app-device-fixture.json")
        assumeTrue("Requires explicit isolated Home QA fixture", fixtureFile.isFile)
        val fixture = JSONObject(fixtureFile.readText())
        require(fixture.getString("scope") == "isolated-home-ui-qa")
        require(fixture.getString("base_url") == "http://127.0.0.1:8940/")
        require(ApiEnvironment.configured().baseUrl == fixture.getString("base_url"))
        val session = fixture.getJSONObject("session")
        require(session.getString("phone_number") == "+9779800000241")
        val vault = KVault(context, Constants.Auth.SESSION_FILE)
        vault.string(Constants.Auth.SESSION_KEY)?.let {
            require(JSONObject(it).optString("phone_number") == session.getString("phone_number")) {
                "Use a dedicated test installation, never a personal login"
            }
        }
        assertTrue(vault.set(Constants.Auth.SESSION_KEY, session.toString()))
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var activity: MainActivity
                scenario.onActivity { activity = it }
                waitFor(hasText("You & Aatithi"))
                val homeList = compose.onNode(hasScrollToIndexAction())
                repeat(3) {
                    homeList.performTouchInput { swipeUp() }
                    homeList.performTouchInput { swipeDown() }
                }
                homeList.performScrollToNode(hasText("Plan a date"))
                compose.onNodeWithText("Plan a date").performClick()
                waitFor(hasText("What would you like to do?"))
                val title = compose.onNode(hasSetTextAction() and hasText("What would you like to do?"))
                title.performTouchInput { click() }
                compose.waitUntil(10_000) { keyboardShown() }
                title.performTextClearance()
                val draft = "QA draft: coffee together after our walk."
                draft.forEach { title.performTextInput(it.toString()) }
                title.assertTextContains(draft, substring = true)
                compose.waitUntil(10_000) { keyboardShown() }
                compose.onNodeWithText("Discard draft").performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("Invite Aatithi").assertIsDisplayed()
                // Back dismisses the IME first, without losing the editor or draft.
                back()
                compose.waitUntil(10_000) { !keyboardShown() }
                compose.onNodeWithContentDescription("Save draft and close").performClick()
                waitFor(hasText("Continue draft"))
                compose.onNodeWithText(draft).assertExists()
                back()
                waitFor(hasText("Plan a date"))
                compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Plans done"))
                compose.onNodeWithText("Chat", useUnmergedTree = true).performClick()
                waitFor(hasContentDescription("Message"))
                // First-run notification primer owns window focus until dismissed.
                if (compose.onAllNodesWithText("Stay in touch").fetchSemanticsNodes().isNotEmpty()) {
                    compose.onNodeWithText("Not now").performClick()
                }
                compose.waitUntil(10_000) { activity.hasWindowFocus() }
                val editor = compose.onNodeWithContentDescription("Message")
                editor.performTouchInput { click() }
                editor.assertIsFocused()
                compose.waitUntil(10_000) { keyboardShown() }
                editor.performTextClearance()
                draft.forEach { editor.performTextInput(it.toString()) }
                editor.performTextInput("\nSecond line: ready when you are.")
                editor.assertTextContains("$draft\nSecond line: ready when you are.")
                compose.waitUntil(10_000) { keyboardShown() }
                val sendBounds = compose.onNodeWithContentDescription("Send message").fetchSemanticsNode().boundsInWindow
                compose.runOnIdle {
                    val decor = activity.window.decorView
                    val ime = decor.rootWindowInsets.getInsets(android.view.WindowInsets.Type.ime()).bottom
                    assertTrue("Native keyboard has a visible inset", ime > 0)
                    assertTrue("Send stays above the native keyboard", sendBounds.bottom <= decor.height - ime)
                }
                back()
                compose.waitUntil(10_000) { !keyboardShown() }
                editor.assertTextContains(draft, substring = true)
                compose.onNodeWithContentDescription("Back to conversations").performClick()
                waitFor(hasText("Plans done"))
                compose.onNodeWithText("Plans done").assertIsDisplayed()
                compose.onNodeWithText("Chat", useUnmergedTree = true).performClick()
                // Room/account-scoped draft restoration follows the route's loading state.
                waitFor(hasContentDescription("Message") and hasText(draft, substring = true))
                compose.onNodeWithContentDescription("Message").assertTextContains(draft, substring = true)
            }
        } finally {
            vault.clear()
            fixtureFile.delete()
        }
    }
}
