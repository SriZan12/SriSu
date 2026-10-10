package com.srisu.srisu.features.couplechat.evaluation

import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.core.data.remote.*
import com.srisu.srisu.core.lifecycle.ApplicationLifetime
import com.srisu.srisu.core.session.*
import com.srisu.srisu.features.couplechat.crypto.*
import com.srisu.srisu.features.couplechat.data.*
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

/** Only compiled by -PchatAppEvaluation=true. Never part of a release artifact. */
object ChatAppEvaluation {
    private class MemorySession(private var value:String?):SessionStorage {
        override fun getSession(sessionKey:String)=value
        override fun saveSession(credentials:String,sessionKey:String) {value=credentials}
        override fun clearSession():Boolean {value=null;return true}
        override fun clearOnReinstall(key:String)=Unit
    }
    private class Client(user:JsonObject,base:String,driver:NativeChatDriver,offline:Boolean=false) {
        val blocked=MutableStateFlow(offline)
        val sessions=SessionCoordinator(MemorySession(user.toString()))
        val lifetime=ApplicationLifetime()
        val environment=ApiEnvironment(base,development=true)
        val http=HttpClientFactory.create(sessions,environment,Darwin.create()).apply {
            plugin(HttpSend).intercept {request ->
                if(blocked.value)throw kotlinx.io.IOException("Synthetic offline")
                execute(request)
            }
        }
        val api=CoupleChatApi(http,environment)
        val metadata=CoupleChatRepository(api,KtorSocketConnector(http,environment,"ws/couple-chat/v1/updates/"),sessions,lifetime)
        val repo=ConversationRepository(api,metadata,sessions,driver,lifetime)
        fun start(room:String) {lifetime.setForeground(true);repo.enter(room)}
        suspend fun ready(condition:(ConversationState)->Boolean)=withTimeout(45000) {repo.state.first(condition)}
        suspend fun close() {repo.close();metadata.close();lifetime.close();lifetime.scope.coroutineContext.job.join();http.close()}
    }
    fun run(fixture:String,driver:NativeChatDriver,completion:(String)->Unit) {
        val job=SupervisorJob()
        CoroutineScope(job+Dispatchers.Default).launch {
            var first:Client?=null;var second:Client?=null
            var phase="fixture"
            var result="FAIL"
            try {
                val data=ChatJson.parseToJsonElement(fixture).jsonObject
                val users=data.getValue("users").jsonArray.map {it.jsonObject}
                val room=data.getValue("room_id").jsonPrimitive.content
                val base=data.getValue("base_url").jsonPrimitive.content
                require(base.startsWith("http://192.168.1.") || base.startsWith("http://127.0.0.1:"))
                users.take(2).forEach {
                    val account=it.getValue("id").jsonPrimitive.content;require(account.startsWith("8100000000000000"))
                    check(ChatJson.parseToJsonElement(driver.execute(account,"reset","{\"confirm_history_loss\":true}")).jsonObject["ok"]?.jsonPrimitive?.boolean==true)
                }
                var a=Client(users[0],base,driver);first=a
                val b=Client(users[1],base,driver);second=b
                phase="secure setup";a.start(room);b.start(room)
                a.ready {it.security==ConversationSecurity.READY};b.ready {it.security==ConversationSecurity.READY}
                a.repo.preferences(ConversationPreferences(nickname="Private iOS nickname",readReceipts=false))
                a.ready {it.preferences.nickname=="Private iOS nickname"};check(b.repo.state.value.preferences.nickname.isEmpty())
                phase="offline send"
                a.blocked.value=true;a.repo.updateDraft("Synthetic iOS नमस्ते 👋")
                a.ready {it.draft.isNotEmpty()};a.repo.sendText()
                val original=a.ready {it.messages.any {m->m.transport==ChatTransport.QUEUED}}.messages.single().id
                a.repo.updateDraft("Synthetic iOS saved draft");a.ready {it.draft.isNotEmpty()}
                a.close();a=Client(users[0],base,driver,offline=true);first=a;a.start(room)
                phase="protected restart"
                a.ready {it.messages.any {m->m.id==original} && it.draft=="Synthetic iOS saved draft"}
                a.blocked.value=false;a.repo.retry()
                a.ready {it.messages.any {m->m.id==original && m.transport==ChatTransport.SENT}}
                b.ready {it.messages.any {m->m.id==original && m.content is ChatContent.Text}}
                check(a.repo.state.value.messages.count {it.id==original}==1)
                phase="reaction"
                b.repo.react(original,"❤️")
                a.ready {it.messages.first {m->m.id==original}.reactions[b.sessions.stamp().accountId]=="❤️"}
                phase="reply"
                b.repo.setReply(original);b.repo.updateDraft("Synthetic iOS reply");b.repo.sendText()
                val reply=a.ready {it.messages.any {m->m.replyTo==original}}.messages.first {it.replyTo==original}
                a.repo.markVisible(listOf(reply.id));delay(750)
                check(!b.repo.state.value.messages.first {it.id==reply.id}.read)
                phase="Ask"
                a.repo.sendCard(ChatCard.Ask("Synthetic iOS poll",listOf("First","Second")))
                val ask=b.ready {it.messages.any {m->(m.content as? ChatContent.Card)?.card is ChatCard.Ask}}.messages.first {(it.content as? ChatContent.Card)?.card is ChatCard.Ask}
                b.repo.answerCard(ask.id,CardReply(vote=1))
                a.ready {it.messages.firstOrNull {m->m.id==ask.id}?.cardResponses?.values?.any {r->r.vote==1}==true}
                phase="Spark"
                a.repo.sendCard(ChatCard.Spark("Everyday us",SparkDecks.questions.getValue("Everyday us").first()))
                val spark=b.ready {it.messages.any {m->(m.content as? ChatContent.Card)?.card is ChatCard.Spark}}.messages.first {(it.content as? ChatContent.Card)?.card is ChatCard.Spark}
                a.repo.answerSpark(spark.id,"Synthetic iOS first answer");a.ready {it.sparks[spark.id]?.submitted==true}
                b.repo.retry();b.ready {it.sparks[spark.id]?.partnerSubmitted==true}
                check(b.repo.state.value.sparks.getValue(spark.id).answers.isEmpty())
                b.repo.answerSpark(spark.id,"Synthetic iOS second answer")
                a.ready {it.sparks[spark.id]?.answers?.size==2 && it.sparks[spark.id]?.revealed==true}
                b.ready {it.sparks[spark.id]?.answers?.size==2 && it.sparks[spark.id]?.revealed==true}
                phase="deletion"
                a.repo.deleteForEveryone(original);b.ready {it.messages.first {m->m.id==original}.deleted}
                check((b.repo.state.value.messages.first {it.id==reply.id}.content as ChatContent.Text).quote?.preview=="Message unavailable")
                b.repo.reconcileHistory();b.ready {it.messages.first {m->m.id==original}.deleted && !it.synchronizing}
                check(b.repo.state.value.messages.first {it.id==original}.content==null)
                result="PASS"
            } catch(failure:Exception) {
                fun state(client:Client?):String=client?.repo?.state?.value?.let {s ->
                    val account=s.accountId!!;val room=s.roomId!!
                    val db=LocalConversation(ProtectedChatStore(driver,account),room)
                    "${s.security}/sync=${s.synchronizing}/account=$account/rows=${s.messages.size}/unavailable=${s.messages.map {it.unavailable}}/reactionActors=${s.messages.map {it.reactions.keys}}/cursor=${db.cursor()}/queue=${db.queue().map {it.kind}}/failures=${s.messages.mapNotNull {it.failure}}/error=${s.error}"
                }.orEmpty()
                result="FAIL: $phase (${failure::class.simpleName}) A=${state(first)} B=${state(second)}"
            }
            finally {
                first?.close();second?.close()
                withContext(Dispatchers.Main) {completion(result)}
                job.cancel()
            }
        }
    }
}
