@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.srisu.srisu.features.sparks

import com.srisu.srisu.features.chat.matrix.*
import com.srisu.srisu.features.sparks.data.*
import de.connect2x.trixnity.client.*
import de.connect2x.trixnity.client.cryptodriver.vodozemac.vodozemac
import de.connect2x.trixnity.client.room.RoomEventEncryptionService
import de.connect2x.trixnity.client.room.getState
import de.connect2x.trixnity.clientserverapi.client.*
import de.connect2x.trixnity.clientserverapi.model.authentication.LoginType
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.room.EncryptionEventContent
import de.connect2x.trixnity.crypto.olm.OlmStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.posix.getenv
import kotlinx.cinterop.toKString
import kotlin.test.*
import kotlin.uuid.Uuid

/** Opt-in, synthetic local couple. Old clients are logged out (stores erased)
 * BEFORE replacement devices are created. Only user-held recovery keys survive.
 * No credentials, recovery keys, ciphertext, or answer text enter test output.
 */
@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
class SparkRecoveryLiveTest {
    @Test fun twoPartnersRecoverOnFreshDevicesThroughRealSynapse() = runBlocking {
        val path = getenv("SRISU_SPARK_RECOVERY_LIVE")?.toKString() ?: return@runBlocking
        val json = Json { ignoreUnknownKeys = true }
        val fixture = json.parseToJsonElement(FileSystem.SYSTEM.read(path.toPath()) { readUtf8() }).jsonObject
        val base = fixture.getValue("base_url").jsonPrimitive.content
        require(base == "http://127.0.0.1:8000/")
        val people = fixture.getValue("people").jsonArray.map { it.jsonObject }
        val room = fixture.getValue("room_id").jsonPrimitive.content
        val couple = fixture.getValue("couple_id").jsonPrimitive.long
        val http = HttpClient(Darwin)
        val store = IosMatrixPlatformStore(legacyKeyCleaner = {})
        val created = mutableListOf<Pair<MatrixClient, MatrixStorageScope>>()
        val engines = mutableListOf<io.ktor.client.engine.HttpClientEngine>()
        suspend fun request(person: Int, route: String, method: HttpMethod = HttpMethod.Get, body: JsonObject? = null): JsonObject {
            val response = http.request(base + route) {
                this.method = method
                bearerAuth(people[person].getValue("tokens").jsonObject.getValue("access").jsonPrimitive.content)
                contentType(ContentType.Application.Json); body?.let { setBody(it.toString()) }
            }
            check(response.status.isSuccess()) { "Local request failed: ${response.status.value}" }
            return json.parseToJsonElement(response.bodyAsText()).jsonObject.getValue("data").jsonObject
        }
        suspend fun device(person: Int): MatrixClient {
            val account = people[person].getValue("id").jsonPrimitive.long
            val bootstrap = json.decodeFromJsonElement<MatrixSessionDto>(request(person, "api/chat/v2/rooms/$room/matrix/session/", HttpMethod.Post)).validated(account, true)
            val scope = MatrixStorageScope(account, Uuid.random().toString().replace("-", ""))
            val engine = Darwin.create().also { engines += it }
            val auth = MatrixClientAuthProviderData.classicLogin(Url(bootstrap.homeserverUrl), token = bootstrap.loginToken,
                loginType = LoginType.Unknown(bootstrap.loginType, JsonObject(emptyMap())), deviceId = store.deviceId(scope),
                initialDeviceDisplayName = "SriSu synthetic recovery check", refreshToken = false, httpClientEngine = engine).getOrThrow()
            val client = MatrixClient.create(store.repositories(scope), store.media(scope), CryptoDriverModule.vodozemac(), auth,
                configuration = { name = "SriSu-recovery-QA"; httpClientEngine = engine; storeTimelineEventContentUnencrypted = false }).getOrThrow()
            created += client to scope
            client.startSync()
            withTimeout(60_000) {
                client.room.getState<EncryptionEventContent>(RoomId(bootstrap.roomId)).filterNotNull().first()
                client.syncState.first { it == SyncState.RUNNING }
            }
            return client
        }
        try {
            val one = device(0); val two = device(1)
            for (client in listOf(one, two)) {
                check(SparkRecoveryVault(MatrixSparkRecoveryTransport(client), client.userId.full).status(null) == SparkRecoveryStatus.NotSetUp) {
                    "Use a fresh synthetic couple fixture for this live test. Existing recovery must not be replaced."
                }
            }
            var spark = json.decodeFromJsonElement<SparkSession>(request(0, "api/social/sparks/", HttpMethod.Post,
                buildJsonObject { put("request_id", Uuid.random().toString()); put("couple", couple); put("question_id", "deep-1-v1") }))
            val answers = listOf("QA recovery: a shared cup of tea.", "QA recovery: a quiet walk together.")
            val envelope = withTimeout(45_000) { encryptSpark(one.di.getAll<RoomEventEncryptionService>(), spark, one.userId.full, answers[0]) }
            request(0, "api/social/sparks/${spark.id}/answer/", HttpMethod.Put, buildJsonObject { put("envelope", envelope) })
            val waiting = json.decodeFromJsonElement<SparkSession>(request(1, "api/social/sparks/${spark.id}/"))
            assertTrue(waiting.answers.isEmpty())
            val second = withTimeout(45_000) { encryptSpark(two.di.getAll<RoomEventEncryptionService>(), spark, two.userId.full, answers[1]) }
            spark = json.decodeFromJsonElement<SparkSession>(request(1, "api/social/sparks/${spark.id}/answer/", HttpMethod.Put, buildJsonObject { put("envelope", second) }))
            assertTrue(spark.revealed)
            val keys = listOf(newSparkRecoveryKey(), newSparkRecoveryKey())
            for ((index, client) in listOf(one, two).withIndex()) {
                val vault = SparkRecoveryVault(MatrixSparkRecoveryTransport(client), client.userId.full)
                vault.create(keys[index])
                for (answer in spark.answers) {
                    val text = withTimeout(60_000) { decryptSpark(client.di.getAll<RoomEventEncryptionService>(), client.di.get<OlmStore>(), spark, answer) }
                    assertTrue(text in answers)
                    vault.backup(keys[index], spark, answer, text)
                    vault.backup(keys[index], spark, answer, text) // exact retry
                }
            }
            val denied = two.api.user.getAccountData(SPARK_RECOVERY_ROOT, one.userId)
            assertTrue(denied.isFailure) // another account cannot read the backup
            one.logout().getOrThrow(); one.closeSuspending()
            two.logout().getOrThrow(); two.closeSuspending()
            for ((_, scope) in created) store.deleteAccountData(scope)
            created.clear() // logout erased the old SDK stores; no key export/import
            var restored = 0
            for (person in 0..1) {
                val fresh = device(person)
                val authorized = json.decodeFromJsonElement<SparkSession>(request(person, "api/social/sparks/${spark.id}/"))
                for (answer in authorized.answers) {
                    val sessionId = answer.envelope.getValue("session_id").jsonPrimitive.content
                    assertNull(fresh.di.get<OlmStore>().getInboundMegolmSession(sessionId, RoomId(spark.matrixRoomId)))
                }
                val vault = SparkRecoveryVault(MatrixSparkRecoveryTransport(fresh), fresh.userId.full)
                assertEquals(SparkRecoveryStatus.Locked, vault.status(null))
                assertTrue(runCatching { vault.unlock(newSparkRecoveryKey()) }.isFailure)
                vault.unlock(keys[person])
                for (answer in authorized.answers) {
                    val recovered = vault.recover(keys[person], authorized, answer)
                    assertTrue(recovered in answers)
                    restored++
                }
            }
            assertEquals(4, restored)
            println("SPARK_RECOVERY_LIVE: 2 replacement devices, 4 answers restored; old stores erased; wrong key and cross-account access rejected; mutual reveal preserved")
        } finally {
            withContext(NonCancellable) {
                for ((client, scope) in created) {
                    runCatching { withTimeout(10_000) { client.logout().getOrThrow() } }
                    runCatching { client.closeSuspending() }
                    runCatching { store.deleteAccountData(scope) }
                }
                engines.forEach { it.close() }; http.close()
            }
        }
    }
}
