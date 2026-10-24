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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Opt-in live partner driver, test source only. Configuration is a private local
 * file created through development OTP authentication. No credentials or decoded
 * answers are printed. Commands/results live beside that file and are never
 * bundled in the application. Run only against the authorized local dev server.
 */
class SparkLivePartnerTest {
    @Test fun authorizedLocalPartner() = runBlocking {
        val path = getenv("SRISU_SPARK_LIVE_FIXTURE")?.toKString() ?: return@runBlocking
        val fs = FileSystem.SYSTEM
        val configPath = path.toPath()
        val folder = requireNotNull(configPath.parent)
        val json = Json { ignoreUnknownKeys = true }
        val config = json.parseToJsonElement(fs.read(configPath) { readUtf8() }).jsonObject
        val base = config.getValue("base_url").jsonPrimitive.content
        require(base == "http://127.0.0.1:8000/")
        val account = config.getValue("account_id").jsonPrimitive.long
        var token = config.getValue("tokens").jsonObject.getValue("access").jsonPrimitive.content
        var refresh = config.getValue("tokens").jsonObject.getValue("refresh").jsonPrimitive.content
        val http = HttpClient(Darwin)
        suspend fun request(route: String, verb: HttpMethod = HttpMethod.Get, payload: JsonObject? = null): JsonObject {
            suspend fun execute() = http.request(base + route) {
                method = verb; bearerAuth(token); contentType(ContentType.Application.Json)
                payload?.let { setBody(it.toString()) }
            }
            var response = execute()
            if (response.status.value == 401) {
                val rotated = http.post(base + "api/auth/refresh/") {
                    contentType(ContentType.Application.Json)
                    setBody(buildJsonObject { put("refresh", refresh) }.toString())
                }
                check(rotated.status.isSuccess()) { "Local auth refresh failed" }
                val data = json.parseToJsonElement(rotated.bodyAsText()).jsonObject.getValue("data").jsonObject
                val tokens = data["tokens"]?.jsonObject ?: data
                token = tokens.getValue("access").jsonPrimitive.content
                refresh = tokens.getValue("refresh").jsonPrimitive.content
                response = execute()
            }
            check(response.status.isSuccess()) { "Local HTTP status ${response.status.value}" }
            return json.parseToJsonElement(response.bodyAsText()).jsonObject.getValue("data").jsonObject
        }
        val room = config.getValue("room_id").jsonPrimitive.content
        val bootstrap = json.decodeFromJsonElement<MatrixSessionDto>(request("api/chat/v2/rooms/$room/matrix/session/", HttpMethod.Post))
            .validated(account, allowInsecureHomeserver = true)
        val store = IosMatrixPlatformStore(legacyKeyCleaner = {})
        val scope = MatrixStorageScope.create(account, base, bootstrap.homeserverUrl,
            bootstrap.relationshipId, bootstrap.membershipEpoch, bootstrap.roomId)
        val engine = Darwin.create()
        val auth = MatrixClientAuthProviderData.classicLogin(Url(bootstrap.homeserverUrl),
            token = bootstrap.loginToken, loginType = LoginType.Unknown(bootstrap.loginType, JsonObject(emptyMap())),
            deviceId = store.deviceId(scope), initialDeviceDisplayName = "SriSu local Sparks verification",
            refreshToken = false, httpClientEngine = engine).getOrThrow()
        val client = MatrixClient.create(store.repositories(scope), store.media(scope), CryptoDriverModule.vodozemac(), auth,
            configuration = { name = "SriSu-Sparks-live-check"; httpClientEngine = engine; storeTimelineEventContentUnencrypted = false }).getOrThrow()
        fun result(value: JsonObject) {
            val temp = folder / "result.tmp"
            fs.write(temp) { writeUtf8(value.toString()) }
            fs.atomicMove(temp, folder / "result.json")
        }
        try {
            client.startSync()
            withTimeout(60_000) { client.room.getState<EncryptionEventContent>(RoomId(bootstrap.roomId)).filterNotNull().first() }
            result(buildJsonObject { put("ready", true) })
            var sequence = -1
            val envelopes = mutableMapOf<String, JsonObject>()
            withTimeout(30 * 60_000) {
                while (isActive) {
                    delay(300)
                    val commandPath = folder / "command.json"
                    if (!fs.exists(commandPath)) continue
                    val command = json.parseToJsonElement(fs.read(commandPath) { readUtf8() }).jsonObject
                    val next = command.getValue("sequence").jsonPrimitive.int
                    if (next == sequence) continue
                    sequence = next
                    val action = command.getValue("action").jsonPrimitive.content
                    if (action == "stop") break
                    require(action in setOf("inspect", "submit", "retry", "decrypt"))
                    try {
                        val id = command["id"]?.jsonPrimitive?.content ?: config.getValue("spark_id").jsonPrimitive.content
                        var session = json.decodeFromJsonElement<SparkSession>(request("api/social/sparks/$id/"))
                        require(session.couple == bootstrap.relationshipId && session.matrixRoomId == bootstrap.roomId &&
                            session.epoch == bootstrap.membershipEpoch)
                        assertTrue(session.revealed || session.answers.all { it.author == account })
                        if (action == "submit" || action == "retry") {
                            val envelope = envelopes[id] ?: withTimeout(40_000) {
                                val text = command.getValue("text").jsonPrimitive.content
                                require(text.startsWith("QA ")) { "Clearly labelled synthetic content required" }
                                encryptSpark(client.di.getAll<RoomEventEncryptionService>(), session, client.userId.full,
                                    text)
                            }.also { envelopes[id] = it }
                            val submitted = request("api/social/sparks/$id/answer/", HttpMethod.Put,
                                buildJsonObject { put("envelope", envelope) })
                            session = json.decodeFromJsonElement(submitted)
                        }
                        var decrypted = 0
                        if (action == "decrypt") {
                            for (answer in session.answers) {
                                withTimeout(35_000) {
                                    val text = decryptSpark(client.di.getAll<RoomEventEncryptionService>(), client.di.get<OlmStore>(), session, answer)
                                    assertTrue(text.isNotBlank())
                                }
                                decrypted++
                            }
                            assertEquals(if (session.revealed) 2 else session.answers.size, decrypted)
                        }
                        result(buildJsonObject {
                            put("sequence", sequence); put("state", session.state); put("submitted", session.submitted(account))
                            put("visible_envelopes", session.answers.size); put("decrypted", decrypted)
                            put("partner_ciphertext_withheld", session.revealed || session.answers.all { it.author == account })
                            put("revision", session.revision)
                        })
                    } catch (failure: Exception) {
                        result(buildJsonObject { put("sequence", sequence); put("failure_type", failure::class.simpleName ?: "Failure") })
                    }
                }
            }
        } finally {
            withContext(NonCancellable) {
                runCatching { withTimeout(10_000) { client.logout().getOrThrow() } }
                client.closeSuspending(); http.close(); engine.close()
            }
        }
    }
}
