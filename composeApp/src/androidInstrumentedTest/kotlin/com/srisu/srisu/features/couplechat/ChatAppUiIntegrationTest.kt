package com.srisu.srisu.features.couplechat

import android.view.WindowManager
import android.view.WindowInsets
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.srisu.srisu.features.couplechat.crypto.AndroidChatDriver
import com.srisu.srisu.features.couplechat.data.*
import com.srisu.srisu.features.couplechat.presentation.*
import com.srisu.srisu.features.coupleprofile.data.CoupleProfileRepository
import com.srisu.srisu.theme.SriSuTheme
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.koin.compose.KoinApplication
import org.koin.dsl.module
import java.io.File

/** Visible production conversation; its second partner uses the actual repository.
 * Credentials exist only in the disposable host fixture, never in app code. */
class ChatAppUiIntegrationTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private fun visible(text:String)=compose.waitUntil(45000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
    private fun menu(text:String) {
        // A quote repeats the original body but belongs to the replying message.
        // Exclude that path before looking up the owning bubble's actions.
        val node=compose.onAllNodesWithText(text).fetchSemanticsNodes()
            .filter {target->generateSequence(target) {it.parent}.none {
                it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Quoted message. Open original")==true
            }}
            .flatMap {target->generateSequence(target) {it.parent}.toList()}
            .first {it.config.contains(SemanticsActions.CustomActions)}
        compose.runOnIdle {assertTrue(node.config[SemanticsActions.CustomActions].first {it.label=="Message options"}.action())}
    }
    @Test fun visibleConversationSendsRepliesSharesAndDeletes() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val fixtureFile=File(context.filesDir,"chat-app-fixture.json")
        val fixture=JSONObject(fixtureFile.readText());val users=fixture.getJSONArray("users")
        val room=fixture.getString("room_id");val base=fixture.getString("base_url")
        require(base.startsWith("http://127.0.0.1:"))
        for(i in 0..1) {
            val id=users.getJSONObject(i).getLong("id").toString();require(id.startsWith("8100000000000000"))
            val native=AndroidChatDriver(context)
            assertTrue(JSONObject(native.execute(id,"reset","{\"confirm_history_loss\":true}")).getBoolean("ok"));native.close(id)
        }
        val harness=ChatAppIntegrationTest()
        val first=harness.Client(users.getJSONObject(0),base)
        val partner=harness.Client(users.getJSONObject(1),base)
        val profiles=CoupleProfileRepository(first.http,first.environment,first.sessions)
        val dependencies=module {single {first.api};single {profiles}}
        val viewModel=ConversationViewModel(first.repo,profiles)
        try {
            first.start(room);partner.start(room)
            runBlocking {
                first.waitFor("UI secure setup") {it.security==ConversationSecurity.READY}
                partner.waitFor("Partner secure setup") {it.security==ConversationSecurity.READY}
            }
            compose.setContent {KoinApplication(application={modules(dependencies)}) {
                SriSuTheme {CoupleChatScreen(room,{}, {}, {_,_->}, {},viewModel,onOpenMedia={_,_,_,_->})}
            }}
            visible("Message") // The subtitle may already show the partner's live activity.
            compose.runOnIdle {assertTrue(compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)}
            compose.onNodeWithContentDescription("Message").performClick()
            compose.waitUntil(15000) {compose.activity.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime())==true}
            compose.onNodeWithContentDescription("Message").performTextInput("Synthetic UI hello")
            compose.onNodeWithContentDescription("Send message").assertIsDisplayed()
            compose.onNodeWithContentDescription("Send message").performClick()
            val original=runBlocking {partner.waitFor("UI text delivery") {it.messages.any {m->(m.content as? ChatContent.Text)?.body=="Synthetic UI hello"}}.messages.single().id}
            partner.repo.setReply(original);partner.repo.updateDraft("Synthetic partner reply");partner.repo.sendText()
            visible("Synthetic partner reply")
            menu("Synthetic partner reply");compose.onNodeWithText("❤️").performClick()
            runBlocking {partner.waitFor("UI reaction delivery") {it.messages.any {m->m.reactions[first.user.getLong("id")]=="❤️"}}}
            compose.onNodeWithContentDescription("Conversation options").performClick()
            compose.onNodeWithText("Personal nickname").performTextInput("Only my nickname")
            compose.onNodeWithText("Save nickname").performClick();visible("Only my nickname")
            assertEquals("",partner.repo.state.value.preferences.nickname)
            compose.onNodeWithContentDescription("Attach or share").performClick()
            compose.onNodeWithText("Ask").performClick()
            compose.onNodeWithText("Your question").performTextInput("Synthetic UI choice?")
            compose.onNodeWithText("Optional poll choices, one per line").performTextInput("Tea\nWalk")
            compose.onNodeWithText("Send Ask").performScrollTo().performClick()
            val ask=runBlocking {partner.waitFor("UI Ask delivery") {it.messages.any {m->(m.content as? ChatContent.Card)?.card is ChatCard.Ask}}.messages.first {(it.content as? ChatContent.Card)?.card is ChatCard.Ask}}
            partner.repo.answerCard(ask.id,CardReply(vote=1));visible("Synthetic app participant 1: Walk")
            partner.repo.sendCard(ChatCard.Challenge("future-synthetic-reference"))
            visible("This Challenge is unavailable in this version.")
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(first.repo.state.value.messages.indexOfFirst {it.id==original})
            visible("Synthetic UI hello")
            menu("Synthetic UI hello");compose.onNodeWithText("Delete for everyone").performScrollTo().performClick()
            compose.onNodeWithText("Delete").performClick()
            runBlocking {partner.waitFor("UI deletion") {it.messages.first {m->m.id==original}.deleted}}
            visible("This message was deleted")
            compose.onNodeWithText("Synthetic UI hello").assertDoesNotExist()
        } finally {
            compose.runOnIdle {compose.activity.finish()}
            runBlocking {first.close();partner.close()};fixtureFile.delete()
        }
    }
}
