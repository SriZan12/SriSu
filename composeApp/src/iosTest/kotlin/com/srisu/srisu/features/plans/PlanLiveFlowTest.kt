@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.uuid.ExperimentalUuidApi::class,
    kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.srisu.srisu.features.plans

import androidx.lifecycle.viewModelScope
import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.core.lifecycle.ApplicationLifetime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import com.srisu.srisu.core.data.remote.*
import com.srisu.srisu.core.session.*
import com.srisu.srisu.features.plans.data.*
import com.srisu.srisu.features.plans.presentation.*
import com.srisu.srisu.features.chat.matrix.*
import com.srisu.srisu.features.moments.MomentFixtures
import com.srisu.srisu.features.sparks.FakeSparkMatrix
import com.srisu.srisu.utils.Constants.Auth.SESSION_KEY
import de.connect2x.trixnity.client.*
import de.connect2x.trixnity.client.cryptodriver.vodozemac.vodozemac
import de.connect2x.trixnity.client.room.*
import de.connect2x.trixnity.clientserverapi.client.*
import de.connect2x.trixnity.clientserverapi.model.authentication.LoginType
import de.connect2x.trixnity.core.model.*
import de.connect2x.trixnity.core.model.events.m.room.EncryptionEventContent
import de.connect2x.trixnity.core.model.events.m.room.EncryptedMessageEventContent.MegolmEncryptedMessageEventContent
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import io.ktor.client.engine.darwin.Darwin
import io.ktor.http.Url
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
import kotlin.uuid.Uuid

/** Opt-in local Django/PostgreSQL/Synapse, three synthetic users. Never prints credentials or content. */
class PlanLiveFlowTest {
    @Test fun twoPartnersCompleteAndRetryAgainstLiveServices() = runBlocking {
        val path = getenv("SRISU_PLAN_LIVE")?.toKString() ?: return@runBlocking
        val fixture = ApiJson.parseToJsonElement(FileSystem.SYSTEM.read(path.toPath()) { readUtf8() }).jsonObject
        val base = fixture.getValue("base_url").jsonPrimitive.content
        require(base == "http://127.0.0.1:8000/")
        val environment = ApiEnvironment(base, true)
        val people = fixture.getValue("people").jsonArray.map { it.jsonObject }
        val couple = fixture.getValue("couple_id").jsonPrimitive.long
        val room = fixture.getValue("room_id").jsonPrimitive.content
        val coordinators = people.map { person ->
            val values = mutableMapOf(SESSION_KEY to ApiJson.encodeToString(Session(
                id = person.getValue("id").jsonPrimitive.long,
                access = person.getValue("tokens").jsonObject.getValue("access").jsonPrimitive.content,
                refresh = person.getValue("tokens").jsonObject.getValue("refresh").jsonPrimitive.content)))
            SessionCoordinator(object : SessionStorage {
                override fun getSession(sessionKey: String) = values[sessionKey]
                override fun saveSession(credentials: String, sessionKey: String) { values[sessionKey] = credentials }
                override fun clearSession(): Boolean { values.clear(); return true }
                override fun clearOnReinstall(key: String) = Unit
            })
        }
        val http = coordinators.map { HttpClientFactory.create(it, environment, Darwin.create()) }
        val sources = http.map { PlanRepository(it, environment) }
        val store = IosMatrixPlatformStore(legacyKeyCleaner = {})
        val devices = mutableListOf<Pair<MatrixClient, MatrixStorageScope>>()
        val engines = mutableListOf<io.ktor.client.engine.HttpClientEngine>()
        val models = mutableListOf<PlanViewModel>()
        val lives = mutableListOf<ApplicationLifetime>()
        fun reminder(person:Int,device:FakePlanDevice)=PlanReminders(sources[person],coordinators[person],environment,device,ApplicationLifetime().also {lives+=it})
        Dispatchers.setMain(Dispatchers.Default)
        suspend fun device(person: Int): MatrixClient {
            val account = requireNotNull(coordinators[person].stamp().accountId)
            val bootstrap = MatrixSessionApiService(http[person], environment).create(room).planValue().validated(account, true)
            val scope = MatrixStorageScope(account, Uuid.random().toString().replace("-", ""))
            val engine = Darwin.create().also { engines += it }
            val auth = MatrixClientAuthProviderData.classicLogin(Url(bootstrap.homeserverUrl), token = bootstrap.loginToken,
                loginType = LoginType.Unknown(bootstrap.loginType, JsonObject(emptyMap())), deviceId = store.deviceId(scope),
                initialDeviceDisplayName = "SriSu synthetic Plans check", refreshToken = false, httpClientEngine = engine).getOrThrow()
            val client = MatrixClient.create(store.repositories(scope), store.media(scope), CryptoDriverModule.vodozemac(), auth,
                configuration = { name = "SriSu-plans-QA"; httpClientEngine = engine; storeTimelineEventContentUnencrypted = false }).getOrThrow()
            devices += client to scope
            client.startSync()
            withTimeout(60_000) {
                client.room.getState<EncryptionEventContent>(RoomId(bootstrap.roomId)).filterNotNull().first()
                client.syncState.first { it == SyncState.RUNNING }
            }
            return client
        }
        suspend fun act(person:Int, current:Plan, action:String, values:JsonObject=buildJsonObject {}):Plan =
            sources[person].submit(PlanIntent(Uuid.random().toString(),couple,current.id,current.revision,action,values)).planValue()
        suspend fun settled(vm:PlanViewModel,predicate:(PlanState)->Boolean={true}):PlanState =
            withTimeout(20_000) {vm.state.first {!it.loading && !it.busy && predicate(it)}}
        val future=(Clock.System.now()+2.days).toString()
        fun fields(date:String=future,place:String="Riverside")=buildJsonObject {
            put("title","A walk together");put("starts_at",date);put("place",place);put("time_zone","Asia/Kathmandu")
        }
        try {
            assertEquals(couple,sources[1].home().planValue().couple)
            assertNull(sources[2].home().planValue().couple)
            val draftDevice=FakePlanDevice()
            val draftModel=PlanViewModel(sources[0],FakeSparkMatrix(),coordinators[0],environment,reminder(0,draftDevice),draftDevice).also {models+=it}
            draftModel.open(null);settled(draftModel);draftModel.editPlan()
            draftModel.edit(requireNotNull(draftModel.state.value.draft).copy(title="Our private draft",startsAt=future));draftModel.closeEditor();draftModel.viewModelScope.cancel()
            val restored=PlanViewModel(sources[0],FakeSparkMatrix(),coordinators[0],environment,reminder(0,draftDevice),draftDevice).also {models+=it}
            restored.open(null);settled(restored);assertEquals("Our private draft",restored.state.value.draft?.title);restored.discardDraft()
            var current=sources[0].submit(PlanIntent(Uuid.random().toString(),couple,values=fields())).planValue()
            assertEquals("invited",current.state)
            assertTrue(sources[1].list("invitations").planValue().items.any {it.id==current.id})
            assertEquals(404,(sources[2].get(current.id) as NetworkAPIResult.Error).failure.status)
            val one=device(0);val two=device(1)
            current=sources[0].get(current.id).planValue()
            val eventId=withTimeout(60_000) {sendPlanCard(one,current)}
            assertEquals(eventId,sendPlanCard(one,current))
            current=sources[0].card(current.id,eventId).planValue()
            val raw=two.api.room.getEvent(RoomId(requireNotNull(current.matrixRoomId)),EventId(eventId)).getOrThrow()
            assertTrue(raw is de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent<*>)
            assertTrue(raw.content is MegolmEncryptedMessageEventContent)
            val clear=withTimeout(60_000) {two.di.getAll<RoomEventEncryptionService>().decrypt(raw)?.getOrThrow()}
            assertTrue(clear is RoomMessageEventContent.TextBased.Text)
            assertEquals(PlanReference(current.id,couple),PlanReference.parse(clear.externalUrl))

            val requests=mutableListOf<PlanIntent>();var loseResponse=true
            val unreliable=object:PlanSource by sources[1] {
                override suspend fun submit(intent:PlanIntent):NetworkAPIResult<Plan?> {
                    requests+=intent;val response=sources[1].submit(intent)
                    return if(loseResponse) {loseResponse=false;MomentFixtures.failed(null)} else response
                }
            }
            val deviceTwo=FakePlanDevice();val reminderTwo=reminder(1,deviceTwo)
            val first=PlanViewModel(unreliable,FakeSparkMatrix(),coordinators[1],environment,reminderTwo,deviceTwo).also {models+=it}
            first.open(current.id);settled(first) {it.plan!=null};first.action("accept");settled(first) {it.pending!=null && it.error!=null}
            assertEquals("agreed",sources[0].get(current.id).planValue().state);first.viewModelScope.cancel()
            val recovered=PlanViewModel(unreliable,FakeSparkMatrix(),coordinators[1],environment,reminderTwo,deviceTwo).also {models+=it}
            recovered.open(current.id);settled(recovered) {it.pending!=null};recovered.retry();settled(recovered) {it.pending==null && it.plan?.state=="agreed"}
            assertEquals(2,requests.size);assertEquals(requests.first(),requests.last())
            current=sources[0].get(current.id).planValue()
            reminderTwo.choose(current,60);val oldAlarm=deviceTwo.alarms.single().atMillis
            current=act(0,current,"add_item",buildJsonObject {put("text","Tea");put("assigned_to",coordinators[1].stamp().accountId)})
            val item=current.checklist.single().id
            current=act(1,current,"set_item",buildJsonObject {put("item_id",item);put("checked",true)})
            assertEquals(coordinators[1].stamp().accountId,current.checklist.single().checkedBy)
            current=act(0,current,"set_item",buildJsonObject {put("item_id",item);put("checked",false)})
            val revised=(kotlin.time.Instant.parse(future)+2.hours).toString()
            current=act(0,current,"propose",fields(revised,"Park"))
            assertEquals(future,kotlin.time.Instant.parse(current.startsAt).toString());reminderTwo.refresh();assertEquals(oldAlarm,deviceTwo.alarms.single().atMillis)
            current=act(1,current,"decline");assertEquals("Riverside",current.place);assertEquals("yes",current.response)
            current=act(1,current,"propose",fields(revised,"Park"));current=act(1,current,"withdraw_change");assertNull(current.change)
            current=act(0,current,"propose",fields(revised,"Park"));current=act(1,current,"accept")
            assertEquals("Park",current.place);reminderTwo.refresh();assertEquals(oldAlarm+7_200_000,deviceTwo.alarms.single().atMillis)
            assertEquals(current.id,sources[0].home().planValue().nextPlan?.id)
            current=act(1,current,"remove_item",buildJsonObject {put("item_id",item)});assertTrue(current.checklist.isEmpty())
            current=act(1,current,"cancel");reminderTwo.refresh();assertTrue(deviceTwo.alarms.isEmpty())
            for(source in sources.take(2)) {assertTrue(source.list("past").planValue().items.any {it.id==current.id && it.state=="cancelled"})}
            var declined=sources[1].submit(PlanIntent(Uuid.random().toString(),couple,values=fields())).planValue()
            declined=act(0,declined,"decline");assertEquals("declined",declined.state)
            var counter=sources[0].submit(PlanIntent(Uuid.random().toString(),couple,values=fields())).planValue()
            counter=act(1,counter,"propose",fields(revised));counter=act(0,counter,"accept");assertEquals("agreed",counter.state);act(0,counter,"cancel")
            // A pre-migration-shaped synthetic past record checks preserved identity, note, completion and undo.
            var old=sources[0].get(fixture.getValue("past_plan_id").jsonPrimitive.long).planValue()
            assertEquals("Earlier synthetic reply",old.responseNote)
            if(old.state=="completed") old=act(0,old,"undo_complete")
            old=act(0,old,"complete");assertEquals("completed",old.state)
            old=act(1,old,"undo_complete");assertEquals("elapsed",old.state)
            old=act(1,old,"complete")
            assertTrue(sources[0].list("past").planValue().items.any {it.id==old.id && it.state=="completed"})
            println("PLANS_LIVE: both partners invited, accepted, declined, counterproposed, rescheduled, withdrew, cancelled, completed/undid; shared checklist ownership/check/remove; draft restart and lost-response retry; reminder reconciliation; encrypted Matrix card decrypted by recipient; unrelated-user denial passed")
        } finally {
            models.forEach { it.viewModelScope.cancel() };lives.forEach {it.close()}
            withContext(NonCancellable) {
                for ((client, scope) in devices) {
                    runCatching { withTimeout(10_000) { client.logout().getOrThrow() } }
                    runCatching { client.closeSuspending() }; runCatching { store.deleteAccountData(scope) }
                }
                engines.forEach { it.close() }; http.forEach { it.close() }
            }
            Dispatchers.resetMain()
        }
    }
}
