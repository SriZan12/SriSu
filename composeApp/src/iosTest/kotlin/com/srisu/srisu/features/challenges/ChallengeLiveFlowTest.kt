@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.uuid.ExperimentalUuidApi::class,
    kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.srisu.srisu.features.challenges

import androidx.lifecycle.viewModelScope
import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.core.data.remote.*
import com.srisu.srisu.core.session.*
import com.srisu.srisu.features.challenges.data.*
import com.srisu.srisu.features.challenges.presentation.*
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
class ChallengeLiveFlowTest {
    @Test fun twoPartnersCompleteAndRetryAgainstLiveServices() = runBlocking {
        val path = getenv("SRISU_CHALLENGE_LIVE")?.toKString() ?: return@runBlocking
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
        val sources = http.map { ChallengeRepository(it, environment) }
        val store = IosMatrixPlatformStore(legacyKeyCleaner = {})
        val devices = mutableListOf<Pair<MatrixClient, MatrixStorageScope>>()
        val engines = mutableListOf<io.ktor.client.engine.HttpClientEngine>()
        val models = mutableListOf<ChallengeViewModel>()
        Dispatchers.setMain(Dispatchers.Default)
        suspend fun device(person: Int): MatrixClient {
            val account = requireNotNull(coordinators[person].stamp().accountId)
            val bootstrap = MatrixSessionApiService(http[person], environment).create(room).challengeValue().validated(account, true)
            val scope = MatrixStorageScope(account, Uuid.random().toString().replace("-", ""))
            val engine = Darwin.create().also { engines += it }
            val auth = MatrixClientAuthProviderData.classicLogin(Url(bootstrap.homeserverUrl), token = bootstrap.loginToken,
                loginType = LoginType.Unknown(bootstrap.loginType, JsonObject(emptyMap())), deviceId = store.deviceId(scope),
                initialDeviceDisplayName = "SriSu synthetic Challenges check", refreshToken = false, httpClientEngine = engine).getOrThrow()
            val client = MatrixClient.create(store.repositories(scope), store.media(scope), CryptoDriverModule.vodozemac(), auth,
                configuration = { name = "SriSu-challenges-QA"; httpClientEngine = engine; storeTimelineEventContentUnencrypted = false }).getOrThrow()
            devices += client to scope
            client.startSync()
            withTimeout(60_000) {
                client.room.getState<EncryptionEventContent>(RoomId(bootstrap.roomId)).filterNotNull().first()
                client.syncState.first { it == SyncState.RUNNING }
            }
            return client
        }
        suspend fun act(person: Int, current: ChallengeSession, action: String): ChallengeSession =
            sources[person].submit(ChallengeIntent(Uuid.random().toString(), couple,
                sessionId = current.id, action = action, revision = current.revision)).challengeValue()
        suspend fun settled(vm: ChallengeViewModel, predicate: (ChallengeState) -> Boolean = { true }): ChallengeState =
            withTimeout(20_000) { vm.state.first { !it.loading && !it.busy && predicate(it) } }
        try {
            val home = sources[0].home().challengeValue()
            assertEquals(4, home.collections.size); assertEquals(16, home.collections.sumOf { it.activities.size })
            assertEquals(couple, sources[1].home().challengeValue().couple)
            assertNull(sources[2].home().challengeValue().couple)
            assertNull(home.current, "Use a synthetic couple without a current Challenge")
            var current = sources[0].submit(ChallengeIntent(Uuid.random().toString(), couple, templateId = "new-1-v1")).challengeValue()
            assertEquals("invited", current.state)
            assertEquals(listOf(coordinators[0].stamp().accountId), current.acceptedBy)
            assertEquals(current.id, sources[1].home().challengeValue().current?.id)
            assertEquals(404, (sources[2].get(current.id) as NetworkAPIResult.Error).failure.status)

            val one = device(0); val two = device(1)
            current = sources[0].get(current.id).challengeValue()
            val eventId = withTimeout(60_000) { sendChallengeCard(one, current) }
            assertEquals(eventId, sendChallengeCard(one, current)) // stable Matrix transaction
            current = sources[0].card(current.id, eventId).challengeValue()
            val raw = two.api.room.getEvent(RoomId(requireNotNull(current.matrixRoomId)), EventId(eventId)).getOrThrow()
            assertTrue(raw is de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent<*>)
            assertTrue(raw.content is MegolmEncryptedMessageEventContent)
            val clear = withTimeout(60_000) {
                two.di.getAll<RoomEventEncryptionService>().decrypt(raw)?.getOrThrow()
            }
            assertTrue(clear is RoomMessageEventContent.TextBased.Text)
            assertEquals(ChallengeReference(current.id, couple), ChallengeReference.parse(clear.externalUrl))

            // The real server commits; only the response is lost. Persist then recreate the VM.
            val requests = mutableListOf<ChallengeIntent>()
            var loseResponse = true
            val unreliable = object : ChallengeSource by sources[1] {
                override suspend fun submit(intent: ChallengeIntent): NetworkAPIResult<ChallengeSession?> {
                    requests += intent
                    val response = sources[1].submit(intent)
                    return if (loseResponse) { loseResponse = false; MomentFixtures.failed(null) } else response
                }
            }
            val first = ChallengeViewModel(unreliable, FakeSparkMatrix(), coordinators[1], environment).also { models += it }
            first.open(current.id); settled(first) { it.session != null }
            first.action("accept"); settled(first) { it.pending != null && it.error != null }
            assertEquals("active", sources[0].get(current.id).challengeValue().state)
            first.viewModelScope.cancel()
            val recovered = ChallengeViewModel(unreliable, FakeSparkMatrix(), coordinators[1], environment).also { models += it }
            recovered.open(current.id); settled(recovered) { it.pending != null }
            recovered.retry(); settled(recovered) { it.pending == null && it.session?.state == "active" }
            assertEquals(2, requests.size); assertEquals(requests.first(), requests.last())
            current = sources[0].get(current.id).challengeValue()
            current = act(0, current, "pause"); current = act(1, current, "pause")
            current = act(0, current, "resume")
            assertEquals("paused", current.state); assertEquals(listOf(coordinators[1].stamp().accountId), current.pausedBy)
            current = act(1, current, "resume")
            current = act(0, current, "confirm"); current = act(0, current, "unconfirm")
            assertTrue(current.confirmedBy.isEmpty())
            current = act(0, current, "confirm")
            assertEquals("active", current.state)
            current = act(1, current, "confirm")
            assertEquals("completed", current.state); assertEquals(2, current.confirmedBy.size)
            assertNotNull(current.completedAt)
            for (source in sources.take(2)) {
                assertNull(source.home().challengeValue().current)
                assertEquals(current.id, source.history().challengeValue().items.first().id)
            }
            var declined = sources[1].submit(ChallengeIntent(Uuid.random().toString(), couple, templateId = "play-1-v1")).challengeValue()
            declined = act(0, declined, "decline"); assertEquals("declined", declined.state)
            var cancelled = sources[0].submit(ChallengeIntent(Uuid.random().toString(), couple, templateId = "gestures-1-v1")).challengeValue()
            cancelled = act(0, cancelled, "cancel"); assertEquals("cancelled", cancelled.state)
            assertEquals(setOf("completed", "declined", "cancelled"), sources[1].history().challengeValue().items.map { it.state }.toSet())
            println("CHALLENGES_LIVE: both partners accepted, paused/resumed independently, undid and confirmed completion; lost-response retry survived VM restart; encrypted chat card decrypted by recipient; history and unrelated-user denial passed")
        } finally {
            models.forEach { it.viewModelScope.cancel() }
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
