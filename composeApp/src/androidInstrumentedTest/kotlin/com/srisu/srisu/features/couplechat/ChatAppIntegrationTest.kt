package com.srisu.srisu.features.couplechat

import androidx.test.platform.app.InstrumentationRegistry
import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.core.data.remote.*
import com.srisu.srisu.core.lifecycle.ApplicationLifetime
import com.srisu.srisu.core.session.*
import com.srisu.srisu.features.couplechat.crypto.AndroidChatDriver
import com.srisu.srisu.features.couplechat.data.*
import com.srisu.srisu.utils.Constants
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/** Real KMP repositories, HTTP/WebSockets, Keystore, SQLite and libsignal.
 * The host runner supplies short-lived synthetic sessions in app-private files.
 * No fixture loader or credential override is included in the production app. */
class ChatAppIntegrationTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private class MemorySession(var value:String?):SessionStorage {
        override fun getSession(sessionKey:String)=value
        override fun saveSession(credentials:String,sessionKey:String) {value=credentials}
        override fun clearSession():Boolean {value=null;return true}
        override fun clearOnReinstall(key:String)=Unit
    }
    internal inner class Client(val user:JSONObject,val base:String,offline:Boolean=false) {
        val blocked=AtomicBoolean(offline)
        val sessions=SessionCoordinator(MemorySession(user.toString()))
        val lifetime=ApplicationLifetime()
        val driver=AndroidChatDriver(context)
        val environment=ApiEnvironment(base,development=true)
        val http=HttpClientFactory.create(sessions,environment,OkHttp.create {
            addInterceptor {chain->if(blocked.get())throw IOException("Synthetic offline");chain.proceed(chain.request())}
        })
        val api=CoupleChatApi(http,environment)
        val metadata=CoupleChatRepository(api,KtorSocketConnector(http,environment,"ws/couple-chat/v1/updates/"),sessions,lifetime)
        val repo=ConversationRepository(api,metadata,sessions,driver,lifetime)
        fun start(room:String) {lifetime.setForeground(true);repo.enter(room)}
        suspend fun waitFor(label:String,condition:(ConversationState)->Boolean):ConversationState = try {
            withTimeout(45000) {repo.state.first(condition)}
        } catch(failure:TimeoutCancellationException) {
            throw AssertionError("$label timed out: ${repo.state.value.security}; ${repo.state.value.error}")
        }
        suspend fun close() {repo.close();metadata.close();lifetime.close();lifetime.scope.coroutineContext.job.join();http.close()}
    }
    @Test fun twoPartnersUseActualRepositoriesAndRecoverOfflineQueue() = runBlocking {
        val file=File(context.filesDir,"chat-app-fixture.json")
        assertTrue("Use tools/chat_app_integration.py with the disposable backend",file.isFile)
        val fixture=JSONObject(file.readText());val users=fixture.getJSONArray("users")
        val room=fixture.getString("room_id");val base=fixture.getString("base_url")
        require(base.startsWith("http://127.0.0.1:"))
        for(i in 0..1) {
            val id=users.getJSONObject(i).getLong("id").toString();require(id.startsWith("8100000000000000"))
            val driver=AndroidChatDriver(context)
            assertTrue(JSONObject(driver.execute(id,"reset","{\"confirm_history_loss\":true}")).getBoolean("ok"));driver.close(id)
        }
        var first=Client(users.getJSONObject(0),base)
        val second=Client(users.getJSONObject(1),base)
        try {
            first.start(room);second.start(room)
            first.waitFor("First secure setup") {it.security==ConversationSecurity.READY}
            second.waitFor("Second secure setup") {it.security==ConversationSecurity.READY}
            first.repo.preferences(ConversationPreferences(nickname="My private nickname",readReceipts=false))
            first.waitFor("Nickname saved") {it.preferences.nickname=="My private nickname"}
            assertEquals("",second.repo.state.value.preferences.nickname)
            first.blocked.set(true)
            first.repo.updateDraft("Synthetic offline नमस्ते 👋")
            first.waitFor("Draft saved") {it.draft.isNotEmpty()}
            first.repo.sendText()
            val queued=first.waitFor("Offline bubble") {it.messages.any {m->m.transport==ChatTransport.QUEUED}}
            val original=queued.messages.single().id
            first.repo.updateDraft("Synthetic unsent draft")
            first.waitFor("Second draft saved") {it.draft=="Synthetic unsent draft"}
            first.close()
            first=Client(users.getJSONObject(0),base,offline=true);first.start(room)
            first.waitFor("Protected cached reopening") {it.messages.any {m->m.id==original} && it.draft=="Synthetic unsent draft"}
            first.blocked.set(false);first.repo.retry()
            first.waitFor("Offline queue accepted") {it.messages.any {m->m.id==original && m.transport==ChatTransport.SENT}}
            second.waitFor("Partner receives recovered send") {it.messages.any {m->m.id==original && m.content is ChatContent.Text}}
            assertEquals(1,first.repo.state.value.messages.count {it.id==original})
            second.repo.react(original,"❤️")
            first.waitFor("Encrypted reaction") {it.messages.first {m->m.id==original}.reactions[second.user.getLong("id")]=="❤️"}
            second.repo.setReply(original);second.repo.updateDraft("Synthetic reply");second.repo.sendText()
            val reply=first.waitFor("Encrypted reply") {it.messages.any {m->m.replyTo==original}}.messages.first {it.replyTo==original}
            first.repo.markVisible(listOf(reply.id));delay(750)
            assertFalse(second.repo.state.value.messages.first {it.id==reply.id}.read)
            first.repo.sendCard(ChatCard.Ask("Synthetic choice",listOf("First","Second")))
            val ask=second.waitFor("Ask card") {it.messages.any {m->(m.content as? ChatContent.Card)?.card is ChatCard.Ask}}.messages.first {(it.content as? ChatContent.Card)?.card is ChatCard.Ask}
            second.repo.answerCard(ask.id,CardReply(vote=1))
            first.waitFor("Ask vote") {it.messages.firstOrNull {m->m.id==ask.id}?.cardResponses?.values?.any {r->r.vote==1}==true}
            first.repo.sendCard(ChatCard.Spark("Everyday us",SparkDecks.questions.getValue("Everyday us").first()))
            val spark=second.waitFor("Spark card") {it.messages.any {m->(m.content as? ChatContent.Card)?.card is ChatCard.Spark}}.messages.first {(it.content as? ChatContent.Card)?.card is ChatCard.Spark}
            first.repo.answerSpark(spark.id,"Synthetic first answer")
            first.waitFor("Held Spark answer") {it.sparks[spark.id]?.submitted==true}
            second.repo.retry()
            second.waitFor("Partner sees only submitted flag") {it.sparks[spark.id]?.partnerSubmitted==true}
            assertTrue(second.repo.state.value.sparks.getValue(spark.id).answers.isEmpty())
            second.repo.answerSpark(spark.id,"Synthetic second answer")
            first.waitFor("First mutual reveal") {it.sparks[spark.id]?.answers?.size==2 && it.sparks[spark.id]?.revealed==true}
            second.waitFor("Second mutual reveal") {it.sparks[spark.id]?.answers?.size==2 && it.sparks[spark.id]?.revealed==true}
            first.repo.deleteForEveryone(original)
            second.waitFor("Delete and quote invalidation") {it.messages.first {m->m.id==original}.deleted}
            assertEquals("Message unavailable",(second.repo.state.value.messages.first {it.id==reply.id}.content as ChatContent.Text).quote?.preview)
            second.repo.reconcileHistory()
            second.waitFor("No deleted-content resurrection") {it.messages.first {m->m.id==original}.deleted && !it.synchronizing}
            assertNull(second.repo.state.value.messages.first {it.id==original}.content)
        } finally {first.close();second.close();file.delete()}
    }
}
