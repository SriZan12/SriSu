package com.srisu.srisu.features.sparks

import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.srisu.srisu.features.sparks.data.*
import com.srisu.srisu.features.sparks.presentation.*
import com.srisu.srisu.navigation.navhost.*
import com.srisu.srisu.theme.SriSuTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Installed production composables with synthetic state. Native IME, touch, and insets; no account. */
class QuestionsDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val session = SparkSession("11111111-1111-4111-8111-111111111111",3,2,
        "22222222-2222-4222-8222-222222222222","!test:example.invalid",1,"deep-7-v1",
        "What made you feel close to me this week?","deep","open",1)
    private val decks = listOf("deep" to "Deep", "memories" to "Memories", "playful" to "Playful", "future" to "Future").map { (id,name) ->
        SparkDeck(id,name,"A little closer", listOf(SparkQuestion("$id-1-v1",id,"What is a little moment you remember?"))) }
    private val home = SparkHome(decks,3,listOf(SparkMember(1,"Srijan"),SparkMember(2,"Aatithi")))
    private val base = SparkState(home=home,account=1,loading=false,fresh=true,secure=true,daily=SparkDaily(session,"2026-11-13","Asia/Kathmandu"))
    private var lastDraft = ""
    private var opened: String? = null
    private var connections = 0
    private fun show(initial: SparkState=base, page: QuestionPage=QuestionPage.HOME, scale:Float=1f) {
        compose.setContent {
            var state by remember { mutableStateOf(initial) }
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density,scale)) {
                SriSuTheme(darkTheme=false) {
                    val tab = page == QuestionPage.HOME && state.session == null
                    Scaffold(contentWindowInsets=WindowInsets(0,0,0,0),bottomBar={ if (tab) AppBottomBar({},BottomDestination.QUESTIONS,true) }) {padding ->
                        Box(Modifier.padding(padding).consumeWindowInsets(padding)) {
                            SparkContent(state, {}, {}, {}, {}, { lastDraft=it; state=state.copy(draft=SparkDraft(it)) }, {}, {}, {opened=it}, {}, {}, {},
                                page=page,onConnect={connections++},tab=tab)
                        }
                    }
                }
            }
        }
    }
    private fun keyboardShown() = android.os.ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand("dumpsys input_method")
    ).bufferedReader().use {it.readText().contains("mInputShown=true")}
    private fun capture(name:String) {
        compose.waitForIdle()
        val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
        File(instrumentation.targetContext.filesDir,"questions-$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
    }
    @Test fun editorPreservesRapidMultilineTypingAndKeepsActionAboveNativeKeyboard() {
        show(base.copy(session=session))
        val editor=compose.onNodeWithTag("question-answer")
        editor.performScrollTo().performTouchInput {click()}
        compose.waitUntil(10_000) {keyboardShown()}
        val draft="QA draft: coffee together after our walk.\nA second thought, just for us."
        draft.forEach {editor.performTextInput(it.toString())}
        editor.assertTextContains(draft)
        assertEquals(draft,lastDraft)
        compose.onNodeWithTag("question-primary").assertIsDisplayed().assertIsEnabled()
        val button=compose.onNodeWithTag("question-primary").fetchSemanticsNode().boundsInWindow
        compose.runOnIdle {
            val decor=compose.activity.window.decorView
            val ime=decor.rootWindowInsets.getInsets(android.view.WindowInsets.Type.ime()).bottom
            assertTrue("Native keyboard is shown",ime>0)
            assertTrue("Submit remains above keyboard",button.bottom<=decor.height-ime+1)
        }
        capture("keyboard")
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(10_000) {!keyboardShown()}
        editor.assertTextContains(draft); capture("answer")
    }
    @Test fun largeTextEditorAndKeyboardRemainReachable() {
        show(base.copy(session=session),scale=2f)
        val editor=compose.onNodeWithTag("question-answer")
        editor.performScrollTo().performTouchInput {click()}
        compose.waitUntil(10_000) {keyboardShown()}
        editor.performTextInput("A thought for us.\nAnd another.")
        editor.assertTextContains("A thought for us.\nAnd another.")
        compose.onNodeWithTag("question-primary").assertIsDisplayed().assertIsEnabled()
        editor.assertIsDisplayed();capture("large-keyboard")
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(10_000) {!keyboardShown()}
        editor.performScrollTo().assertTextContains("A thought for us.\nAnd another.")
    }
    @Test fun largeTextKeepsDailyActionAndAllTopicsReachable() {
        show(scale=2f)
        val body=compose.onNodeWithTag("questions-scroll")
        repeat(3) { body.performTouchInput {swipeUp()}; body.performTouchInput {swipeDown()} }
        compose.onNodeWithText("Go first").performScrollTo().performClick();assertEquals(session.id,opened)
        compose.onNodeWithText("Future").performScrollTo().assertIsDisplayed();capture("large-text")
        compose.onNodeWithText("Profile",useUnmergedTree=true).assertIsDisplayed()
    }
    @Test fun emptyConnectionAndHistoryHaveHonestActions() {
        show(base.copy(home=home.copy(couple=null),daily=null))
        compose.onNodeWithText("Connect with your partner").performScrollTo().performClick()
        assertEquals(1,connections);compose.onNodeWithText("Go first").assertDoesNotExist();capture("unpaired")
    }
    @Test fun searchHasUsefulEmptyResultAndClearsIt() {
        show(page=QuestionPage.SEARCH)
        compose.onNodeWithTag("question-search").performTextInput("no such question")
        compose.onNodeWithText("No questions found").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.onNodeWithText("No questions found").assertDoesNotExist()
    }
}
