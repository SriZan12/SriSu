package com.srisu.srisu.features.moments

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.srisu.srisu.core.MemorySession
import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.core.data.remote.*
import com.srisu.srisu.core.session.Session
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.features.coupleprofile.data.CoupleProfileRepository
import com.srisu.srisu.features.moments.data.*
import com.srisu.srisu.utils.Constants.Auth.SESSION_KEY
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlin.test.*

/** Opt-in real Ktor → Django → storage → partner read. Only disposable loopback fixtures. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MomentCropTransportIntegrationTest {
    @Test fun croppedPublicationReordersAndReplaysAcrossThreeAuthenticatedClients(): Unit = runBlocking {
        val path = System.getenv("SRISU_CORE_INTEGRATION_FILE")
        assumeTrue("Requires a disposable loopback backend", path != null)
        val fixture = ApiJson.parseToJsonElement(File(path!!).readText()).jsonObject
        val origin = fixture.getValue("base_url").jsonPrimitive.content
        require(java.net.URI(origin).host == "127.0.0.1")
        val environment = ApiEnvironment(origin, development = true)
        fun client(id: String, token: String): Pair<io.ktor.client.HttpClient, MomentRepository> {
            val sessions = SessionCoordinator(MemorySession()).apply {
                saveSession(ApiJson.encodeToString(Session(id = fixture.getValue(id).jsonPrimitive.long,
                    access = fixture.getValue(token).jsonPrimitive.content)), SESSION_KEY)
            }
            val http = HttpClientFactory.create(sessions, environment, OkHttp.create())
            return http to MomentRepository(http, environment, CoupleProfileRepository(http, environment, sessions))
        }
        val (mineHttp, mine) = client("account_id", "access")
        val (partnerHttp, partner) = client("partner_id", "partner_access")
        val (visitorHttp, visitor) = client("visitor_id", "visitor_access")
        var published: Long? = null
        try { withTimeout(30_000) {
            val bitmap = Bitmap.createBitmap(240, 360, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.rgb(40, 100, 150)) // theme-audit: image pixels — synthetic fixture
            val paint = Paint().also { it.color = Color.rgb(230, 160, 80) } // theme-audit: image pixels — synthetic fixture
            Canvas(bitmap).drawRect(0f, 0f, 240f, 120f, paint)
            val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            bitmap.recycle()
            val request = UUID.randomUUID().toString()
            val full = UUID.randomUUID().toString()
            val crop = UUID.randomUUID().toString()
            val couple = fixture.getValue("couple_id").jsonPrimitive.long
            assertIs<NetworkAPIResult.Success<*>>(mine.createDraft(request, couple))
            assertIs<NetworkAPIResult.Success<*>>(mine.upload(request, full, bytes) {})
            val uploaded = assertNotNull(assertIs<NetworkAPIResult.Success<MomentDraft?>>(mine.upload(request, crop, bytes) {}).response)
            val intent = PublishMoment(uploaded.revision, "Synthetic crop verification", "private", listOf(crop, full),
                mapOf(crop to MomentCrop(0.0, 0.0, 1.0, .5)))
            val created = assertNotNull(assertIs<NetworkAPIResult.Success<Moment?>>(mine.publish(request, intent)).response)
            published = created.id
            assertEquals(listOf(240 to 180, 240 to 360), created.photos.map { it.width to it.height })
            val replay = assertNotNull(assertIs<NetworkAPIResult.Success<Moment?>>(mine.publish(request, intent)).response)
            assertEquals(created.id, replay.id)
            assertEquals(409, assertIs<NetworkAPIResult.Error<*>>(mine.publish(request, intent.copy(crops = emptyMap()))).failure.status)
            val received = assertNotNull(assertIs<NetworkAPIResult.Success<Moment?>>(partner.get(created.id)).response)
            val photo = assertNotNull(partner.image(received.photos.first().image))
            val decoded = assertNotNull(BitmapFactory.decodeByteArray(photo, 0, photo.size))
            try {
                assertEquals(240, decoded.width); assertEquals(180, decoded.height)
                val top = decoded.getPixel(120, 30)
                assertTrue(abs(Color.red(top) - 230) < 8) // theme-audit: image pixels — synthetic fixture
                assertTrue(abs(Color.green(top) - 160) < 8) // theme-audit: image pixels — synthetic fixture
                val bottom = decoded.getPixel(120, 150)
                assertTrue(abs(Color.blue(bottom) - 150) < 8) // theme-audit: image pixels — synthetic fixture
            } finally { decoded.recycle() }
            assertEquals(404, assertIs<NetworkAPIResult.Error<*>>(visitor.get(created.id)).failure.status)
            assertNull(visitor.image(received.photos.first().image))
            assertIs<NetworkAPIResult.Success<*>>(partner.viewed(created.id))
            assertNotNull(assertNotNull(assertIs<NetworkAPIResult.Success<Moment?>>(mine.get(created.id)).response).partnerSeenAt)
        } } finally {
            published?.let { mine.delete(it) }
            mineHttp.close(); partnerHttp.close(); visitorHttp.close()
        }
    }
}
