package com.srisu.srisu.features.moments

import com.srisu.srisu.core.MemorySession
import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.core.data.remote.*
import com.srisu.srisu.core.session.Session
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.features.coupleprofile.data.CoupleProfileRepository
import com.srisu.srisu.features.moments.data.*
import com.srisu.srisu.utils.Constants.Auth.SESSION_KEY
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID
import kotlin.test.*

/** Real production Ktor bodies, resumable offsets, worker, publication and partner credentials. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MomentVideoTransportIntegrationTest {
    @Test fun videoTravelsThroughTheRealClientAndWorker(): Unit = runBlocking {
        val path = System.getenv("SRISU_CORE_INTEGRATION_FILE")
        assumeTrue("Requires disposable --moment-video backend", path != null)
        val fixture = ApiJson.parseToJsonElement(File(path!!).readText()).jsonObject
        assumeTrue(fixture.containsKey("video_path"))
        val origin = fixture.getValue("base_url").jsonPrimitive.content
        require(java.net.URI(origin).host == "127.0.0.1")
        val env = ApiEnvironment(origin, development = true)
        fun client(id: String, token: String): Pair<io.ktor.client.HttpClient, MomentRepository> {
            val sessions = SessionCoordinator(MemorySession()).apply {
                saveSession(ApiJson.encodeToString(Session(id = fixture.getValue(id).jsonPrimitive.long,
                    access = fixture.getValue(token).jsonPrimitive.content)), SESSION_KEY)
            }
            val http = HttpClientFactory.create(sessions, env, OkHttp.create())
            return http to MomentRepository(http, env, CoupleProfileRepository(http, env, sessions))
        }
        val (http, mine) = client("account_id", "access")
        val (partnerHttp, partner) = client("partner_id", "partner_access")
        val request = UUID.randomUUID().toString()
        val video = UUID.randomUUID().toString()
        var published: Long? = null
        try { withTimeout(60_000) {
            val file = File(fixture.getValue("video_path").jsonPrimitive.content)
            assertIs<NetworkAPIResult.Success<*>>(mine.createDraft(request, fixture.getValue("couple_id").jsonPrimitive.long))
            var state = assertNotNull(assertIs<NetworkAPIResult.Success<MomentVideoInfo?>>(mine.beginVideo(request, video, file.length())).response)
            file.inputStream().use { source ->
                while (state.receivedBytes < state.sourceBytes) {
                    val bytes = source.readNBytes(minOf(state.chunkBytes.toLong(), state.sourceBytes-state.receivedBytes).toInt())
                    val offset = state.receivedBytes
                    state = assertNotNull(assertIs<NetworkAPIResult.Success<MomentVideoInfo?>>(mine.videoChunk(request, video, offset, bytes)).response)
                    assertEquals(state.receivedBytes, assertNotNull(assertIs<NetworkAPIResult.Success<MomentVideoInfo?>>(
                        mine.videoChunk(request, video, offset, bytes)).response).receivedBytes)
                }
            }
            assertIs<NetworkAPIResult.Success<*>>(mine.completeVideo(request, video))
            do {
                delay(500)
                state = assertNotNull(assertIs<NetworkAPIResult.Success<MomentVideoInfo?>>(mine.videoStatus(request, video)).response)
                assertNotEquals("failed", state.state)
            } while (state.state != "ready")
            val draft = assertNotNull(assertIs<NetworkAPIResult.Success<MomentDraft?>>(mine.draft(request)).response)
            val intent = PublishMoment(draft.revision, "Synthetic video", "private", listOf(video))
            val moment = assertNotNull(assertIs<NetworkAPIResult.Success<Moment?>>(mine.publish(request, intent)).response)
            published = moment.id
            assertEquals(moment.id, assertNotNull(assertIs<NetworkAPIResult.Success<Moment?>>(mine.publish(request, intent)).response).id)
            val received = assertNotNull(assertIs<NetworkAPIResult.Success<Moment?>>(partner.get(moment.id)).response)
            assertEquals(video, received.photos.single().video?.id)
            assertNotNull(partner.image(received.photos.single().image))
            val access = assertNotNull(assertIs<NetworkAPIResult.Success<MomentPlayback?>>(partner.playback(video)).response)
            assertTrue(access.url.endsWith("/master.m3u8"))
            assertFalse(access.url.contains("?"))
            assertTrue(access.cookieValue.isNotBlank())
        } } finally {
            published?.let { mine.delete(it) } ?: mine.discard(request)
            http.close(); partnerHttp.close()
        }
    }
}
