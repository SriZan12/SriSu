package com.srisu.srisu.features.sparks

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

/** Explicit isolated backend fixture, real app/navigation/repositories. Never loads a personal account. */
class QuestionsAppDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun waitFor(matcher: SemanticsMatcher) {
        compose.waitUntil(30_000) {compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()}
    }
    private val currentQuestion = hasText("Go first") or hasText("Answer to reveal")
    private fun back() { instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); compose.waitForIdle() }
    private fun keyboardShown() = android.os.ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand("dumpsys input_method")
    ).bufferedReader().use {it.readText().contains("mInputShown=true")}

    @Test fun dailyDraftNavigationSearchAndHistoryUseTheRealApp() {
        val context=instrumentation.targetContext
        val file=File(context.filesDir,"questions-app-device-fixture.json")
        assumeTrue("Requires an isolated Questions QA fixture",file.isFile)
        val fixture=JSONObject(file.readText())
        require(fixture.getString("scope")=="isolated-questions-ui-qa")
        require(fixture.getString("base_url")=="http://127.0.0.1:8940/")
        require(ApiEnvironment.configured().baseUrl==fixture.getString("base_url"))
        val session=fixture.getJSONObject("session")
        require(session.getString("phone_number")=="+9779800000241")
        val vault=KVault(context,Constants.Auth.SESSION_FILE)
        vault.string(Constants.Auth.SESSION_KEY)?.let { require(JSONObject(it).optString("phone_number")==session.getString("phone_number")) }
        assertTrue(vault.set(Constants.Auth.SESSION_KEY,session.toString()))
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                waitFor(hasText("You & Aatithi"))
                if (compose.onAllNodesWithText("Stay in touch").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText("Not now").performClick()
                compose.onNodeWithText("Questions",useUnmergedTree=true).performClick()
                waitFor(currentQuestion)
                compose.onNode(currentQuestion).performScrollTo().performClick()
                waitFor(hasTestTag("question-answer"))
                val editor=compose.onNodeWithTag("question-answer")
                editor.performScrollTo().performTouchInput {click()}
                compose.waitUntil(10_000) {keyboardShown()}
                editor.performTextClearance()
                val draft="QA question draft: coffee together after our walk.\nA second thought."
                draft.forEach {editor.performTextInput(it.toString())}
                editor.assertTextContains(draft)
                back();compose.waitUntil(10_000) {!keyboardShown()}
                compose.onNodeWithContentDescription("Back").performClick()
                waitFor(currentQuestion);compose.onNode(currentQuestion).performScrollTo().performClick()
                waitFor(hasTestTag("question-answer") and hasText(draft,substring=true))
                compose.onNodeWithTag("question-primary").assertIsDisplayed()
                compose.onNodeWithContentDescription("Back").performClick()
                waitFor(hasContentDescription("Search questions"));compose.onNodeWithContentDescription("Search questions").performClick()
                waitFor(hasTestTag("question-search"));compose.onNodeWithTag("question-search").performTextInput("no matching question")
                compose.onNodeWithText("No questions found").assertExists()
                back();compose.onNodeWithContentDescription("Back").performClick()
                waitFor(hasContentDescription("Recently answered questions"));compose.onNodeWithContentDescription("Recently answered questions").performClick()
                waitFor(hasText("Recent answers, together"))
                compose.onNodeWithContentDescription("Back").performClick()
                waitFor(currentQuestion)
            }
        } finally {vault.clear();file.delete()}
    }
}
