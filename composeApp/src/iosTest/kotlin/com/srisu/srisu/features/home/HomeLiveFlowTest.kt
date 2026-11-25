@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.uuid.ExperimentalUuidApi::class,
    kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.srisu.srisu.features.home

import androidx.lifecycle.viewModelScope
import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.core.data.remote.*
import com.srisu.srisu.core.lifecycle.ApplicationLifetime
import com.srisu.srisu.core.session.*
import com.srisu.srisu.features.chat.matrix.*
import com.srisu.srisu.features.challenges.data.ChallengeRepository
import com.srisu.srisu.features.coupleprofile.data.*
import com.srisu.srisu.features.home.connection.data.remote.api.ConnectionApiService
import com.srisu.srisu.features.home.connection.domain.repository.ConnectionRepository
import com.srisu.srisu.features.home.dashboard.state.*
import com.srisu.srisu.features.home.dashboard.vm.HomeViewModel
import com.srisu.srisu.features.moments.data.MomentRepository
import com.srisu.srisu.features.plans.data.*
import com.srisu.srisu.features.sparks.data.*
import com.srisu.srisu.utils.Constants.Auth.SESSION_KEY
import io.ktor.client.engine.darwin.Darwin
import kotlinx.cinterop.toKString
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.serialization.json.*
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.posix.getenv
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.uuid.Uuid

/** Explicit opt-in: fresh loopback Django/PostgreSQL/Synapse and three synthetic accounts. */
class HomeLiveFlowTest {
    private fun <T:Any> NetworkAPIResult<T?>.value():T = when(this) {
        is NetworkAPIResult.Success -> requireNotNull(response)
        is NetworkAPIResult.Error -> error("Home live HTTP failure: ${failure.status} ${failure.code}")
    }

    @Test fun twoPartnersUseHomeAndReturnFromRealEncryptedChat()=runBlocking {
        val path=getenv("SRISU_HOME_LIVE")?.toKString() ?: return@runBlocking
        val fixture=ApiJson.parseToJsonElement(FileSystem.SYSTEM.read(path.toPath()) {readUtf8()}).jsonObject
        val base=fixture.getValue("base_url").jsonPrimitive.content
        require(base=="http://127.0.0.1:8940/")
        val env=ApiEnvironment(base,true)
        val couple=fixture.getValue("couple_id").jsonPrimitive.long
        val room=fixture.getValue("room_id").jsonPrimitive.content
        val people=fixture.getValue("people").jsonArray.map {it.jsonObject}
        val sessions=people.map {person ->
            val values=mutableMapOf(SESSION_KEY to ApiJson.encodeToString(Session(
                id=person.getValue("id").jsonPrimitive.long,
                access=person.getValue("tokens").jsonObject.getValue("access").jsonPrimitive.content,
                refresh=person.getValue("tokens").jsonObject.getValue("refresh").jsonPrimitive.content)))
            SessionCoordinator(object:SessionStorage {
                override fun getSession(sessionKey:String)=values[sessionKey]
                override fun saveSession(credentials:String,sessionKey:String) {values[sessionKey]=credentials}
                override fun clearSession():Boolean {values.clear();return true}
                override fun clearOnReinstall(key:String)=Unit
            })
        }
        val http=sessions.map {HttpClientFactory.create(it,env,Darwin.create())}
        val sparks=http.map {SparkRepository(it,env)}
        val statuses=http.map {CoupleStatusRepository(it,env)}
        val ideas=http.map {DateIdeaRepository(it,env)}
        val plans=http.map {PlanRepository(it,env)}
        val profiles=http.mapIndexed {i,h -> CoupleProfileRepository(h,env,sessions[i])}
        val connections=http.map {ConnectionRepository(ConnectionApiService(it))}
        Dispatchers.setMain(Dispatchers.Default)
        val lives=List(2) {ApplicationLifetime().apply {setForeground(true)}}
        val engines=List(2) {Darwin.create()}
        val matrix=List(2) {i -> TrixnityMatrixChatRepository(MatrixSessionApiService(http[i],env),sessions[i],env,
            engines[i],IosMatrixPlatformStore(legacyKeyCleaner={}),lives[i],sparkSource=sparks[i])}
        val homes=List(3) {i -> HomeViewModel(MomentRepository(http[i],env,profiles[i]),ChallengeRepository(http[i],env),
            plans[i],sessions[i],sparks=sparks[i],statuses=statuses[i],ideas=ideas[i],matrix=matrix.getOrNull(i),
            resolver=ConnectionCurrentCoupleRoomResolver(connections[i]),connections=connections[i],apiOrigin=base,profiles=profiles[i])}
        val collectors=homes.map {launch {it.state.collect {}}}
        suspend fun home(i:Int,check:(HomeUiState)->Boolean):HomeUiState = try {
            withTimeout(45_000) {homes[i].state.first(check)}
        } catch (timeout:TimeoutCancellationException) {
            val current=homes[i].state.value
            error("Home $i timed out: status=${current.status}, couple=${current.couple!=null}, question=${current.couple?.questionFresh}, statusFresh=${current.couple?.statusesFresh}, ideas=${current.couple?.ideasFresh}, chat=${current.couple?.chatAvailability}, unread=${current.couple?.unread}, busy=${current.busy}")
        }
        fun question(state:HomeUiState)=(state.couple?.question as? HomeSection.Ready)?.value?.session
        val ownId=people[0].getValue("id").jsonPrimitive.long
        val partnerId=people[1].getValue("id").jsonPrimitive.long
        try {
            homes.forEach {it.visible(true)}
            val one=home(0) {it.couple?.questionFresh==true && it.couple.chatAvailability is MatrixChatAvailability.Ready && it.couple.statusesFresh && it.couple.ideasFresh}
            val two=home(1) {it.couple?.questionFresh==true && it.couple.chatAvailability is MatrixChatAvailability.Ready}
            assertEquals(question(one)?.id,question(two)?.id)
            home(2) {it.status==HomeStatus.READY && it.couple==null}
            assertEquals(404,(statuses[2].get(couple) as NetworkAPIResult.Error).failure.status)
            assertEquals(404,(ideas[2].list(couple) as NetworkAPIResult.Error).failure.status)
            println("HOME_LIVE: paired/unpaired state and unrelated-user denial passed")

            homes[0].setStatus("Feeling cosy")
            home(0) {it.couple?.statuses?.items?.any {s -> s.author==ownId && s.text=="Feeling cosy"}==true}
            homes[1].refresh()
            home(1) {it.couple?.statuses?.items?.any {s -> s.author==ownId && s.text=="Feeling cosy"}==true}
            homes[0].suggest("coffee")
            val created=home(0) {(it.couple?.ideas as? HomeSection.Ready)?.value?.items?.isNotEmpty()==true}
            var idea=(created.couple!!.ideas as HomeSection.Ready).value.items.first()
            idea=ideas[1].act(idea.id,idea.revision,Uuid.random().toString(),"interest",buildJsonObject {put("interested",true)}).value()
            assertEquals(setOf(ownId,partnerId),idea.interestedBy.toSet())
            val conversion=Uuid.random().toString()
            val fields=buildJsonObject {put("title","Coffee together");put("starts_at",(Clock.System.now()+2.days).toString());put("place","Home QA café");put("time_zone","Asia/Kathmandu")}
            val converted=ideas[0].act(idea.id,idea.revision,conversion,"plan",fields).value()
            assertEquals(converted.planId,ideas[0].act(idea.id,idea.revision,conversion,"plan",fields).value().planId)
            val plan=plans[1].get(requireNotNull(converted.planId)).value()
            assertEquals("pending",plan.response)
            homes[1].refresh();home(1) {it.couple?.featuredPlan?.id==plan.id}
            homes[1].acceptPlan(plan)
            home(1) {it.couple?.featuredPlan?.id==plan.id && it.couple.featuredPlan.response=="yes"}
            homes[0].refresh();home(0) {it.couple?.featuredPlan?.id==plan.id && it.couple.featuredPlan.response=="yes"}
            println("HOME_LIVE: private status, idea interest, idempotent conversion and separate Plan acceptance passed")

            val partnerOwner=MatrixChatRoomOwner();matrix[1].openRoom(room,partnerOwner)
            withTimeout(45_000) {matrix[1].observeRoom(room).first {it.availability is MatrixChatAvailability.Ready}}
            val text="Our synthetic Home verification message ${Uuid.random()}"
            matrix[1].sendText(room,text).getOrThrow()
            home(0) {it.couple?.conversation?.text==text && it.couple.unread==true}
            delay(1_000);assertTrue(homes[0].state.value.couple?.unread==true, "Home-only observation must not mark the partner message read; close any installed conversation using this account")
            homes[0].visible(false)
            val conversation=MatrixChatRoomOwner();matrix[0].openRoom(room,conversation)
            val snapshot=withTimeout(45_000) {matrix[0].observeRoom(room).first {it.messages.any {m->m.body==text}}}
            val message=snapshot.messages.first {it.body==text}
            matrix[0].markRead(room,message.id).getOrThrow()
            matrix[0].closeRoom(room,conversation);homes[0].visible(true)
            home(0) {it.couple?.conversation?.text==text && it.couple.unread==false}
            println("HOME_LIVE: real encrypted Matrix message decrypted locally; Home did not mark read; conversation ownership and return retained the message")

            val daily=sparks[0].daily(couple,"Asia/Kathmandu").value()
            val answer=matrix[1].encryptSparkAnswer(daily.session,"Synthetic answer kept out of Home").getOrThrow()
            sparks[1].answer(daily.session.id,answer).value()
            val waiting=sparks[0].daily(couple,"UTC").value()
            assertTrue(waiting.session.answers.isEmpty());assertTrue(partnerId in waiting.session.submittedBy)
            assertEquals("answered",waiting.partnerActivityKind)
            homes[0].refresh();home(0) {question(it)?.submitted(partnerId)==true}
            sessions[0].clearSession();home(0) {it.couple==null}
            println("HOME_LIVE: encrypted answer omitted from Home; partner answer activity and account-loss cleanup passed")
        } finally {
            homes.forEach {it.visible(false);it.viewModelScope.cancel()};collectors.forEach {it.cancel()}
            sessions.forEach {it.clearSession()};delay(1_000)
            matrix.forEach {it.close()};lives.forEach {it.close()};engines.forEach {it.close()};http.forEach {it.close()}
            Dispatchers.resetMain()
        }
    }
}
