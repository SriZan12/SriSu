@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class,kotlinx.coroutines.DelicateCoroutinesApi::class)
package com.srisu.srisu.features.notifications

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.srisu.srisu.core.MemorySession
import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.core.data.remote.*
import com.srisu.srisu.core.lifecycle.ApplicationLifetime
import com.srisu.srisu.core.session.*
import com.srisu.srisu.features.coupleprofile.data.CoupleProfileRepository
import com.srisu.srisu.features.moments.data.*
import com.srisu.srisu.utils.Constants.Auth.SESSION_KEY
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
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
import kotlin.test.*

/** Real Ktor/Django/media/read-state contract. Only synthetic loopback accounts. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NotificationInboxTransportIntegrationTest {
    @Test fun privateMediaReadBoundaryAndDeletedResourceAcrossThreeClients() {
        val path=System.getenv("SRISU_CORE_INTEGRATION_FILE")
        assumeTrue("Requires tools/core_integration.py and a disposable backend",path!=null)
        val fixture=ApiJson.parseToJsonElement(File(path!!).readText()).jsonObject
        val origin=fixture.getValue("base_url").jsonPrimitive.content
        require(java.net.URI(origin).host=="127.0.0.1")
        val environment=ApiEnvironment(origin,development=true)
        val dispatcher=newSingleThreadContext("inbox-integration-main")
        Dispatchers.setMain(dispatcher)
        val lifetime=ApplicationLifetime()
        fun account(id:String,token:String):Triple<io.ktor.client.HttpClient,NotificationInboxRepository,MomentRepository> {
            val sessions=SessionCoordinator(MemorySession()).apply {
                saveSession(ApiJson.encodeToString(Session(id=fixture.getValue(id).jsonPrimitive.long,
                    access=fixture.getValue(token).jsonPrimitive.content)),SESSION_KEY)
            }
            val client=HttpClientFactory.create(sessions,environment,OkHttp.create())
            return Triple(client,NotificationInboxRepository(client,environment,sessions,lifetime),
                MomentRepository(client,environment,CoupleProfileRepository(client,environment,sessions)))
        }
        val mine=account("account_id","access")
        val partner=account("partner_id","partner_access")
        val stranger=account("visitor_id","visitor_access")
        fun <T> NetworkAPIResult<T?>.value():T=assertNotNull(assertIs<NetworkAPIResult.Success<T?>>(this).response)
        try {runBlocking {withTimeout(60_000) {
            val bitmap=Bitmap.createBitmap(240,360,Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.BLUE) // theme-audit: image pixels — synthetic fixture
            val bytes=ByteArrayOutputStream().also {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray()
            bitmap.recycle()
            suspend fun publish():Moment {
                val request=UUID.randomUUID().toString();val photo=UUID.randomUUID().toString()
                mine.third.createDraft(request,fixture.getValue("couple_id").jsonPrimitive.long).value()
                val draft=mine.third.upload(request,photo,bytes) {}.value()
                return mine.third.publish(request,PublishMoment(draft.revision,"Synthetic inbox QA","private",listOf(photo))).value()
            }
            val first=publish()
            val pageStart=System.nanoTime()
            val initial=partner.second.page(null,false).value()
            val pageMs=(System.nanoTime()-pageStart)/1_000_000
            val item=initial.items.single {it.kind=="moment.published"}
            assertTrue(item.available);assertTrue(item.thumbnail);assertNotNull(item.actorName)
            assertTrue(item.segments.isNotEmpty());assertNull(item.readAt)
            val mediaStart=System.nanoTime()
            val image=assertNotNull(partner.second.image(item.id,"thumbnail"))
            val mediaMs=(System.nanoTime()-mediaStart)/1_000_000
            val decoded=assertNotNull(BitmapFactory.decodeByteArray(image,0,image.size))
            assertTrue(decoded.width<=160 && decoded.height<=160);decoded.recycle()
            assertNull(stranger.second.image(item.id,"thumbnail"))
            assertEquals(404,assertIs<NetworkAPIResult.Error<*>>(stranger.second.open(item.id)).failure.status)
            assertTrue(stranger.second.page(null,false).value().items.isEmpty())
            assertNull(partner.second.page(null,false).value().items.first {it.id==item.id}.readAt)

            publish() // Arrives after the first page's read boundary.
            repeat(2) {assertEquals(1,partner.second.readAll(initial.readToken).value().unreadCount)}
            val unread=partner.second.page(null,true).value()
            assertEquals(1,unread.items.size);assertNotEquals(item.id,unread.items.single().id)
            assertEquals(first.id,partner.second.open(item.id).value().destination?.momentId)
            mine.third.delete(first.id)
            val unavailable=partner.second.open(item.id).value()
            assertFalse(unavailable.available);assertNull(unavailable.destination)
            assertNull(partner.second.image(item.id,"thumbnail"))
            val removed=partner.second.page(null,false).value().items.first {it.id==item.id}
            assertFalse(removed.available);assertFalse(removed.thumbnail);assertNull(removed.actorName)
            assertTrue(removed.segments.isEmpty());assertFalse(removed.body.contains("Synthetic inbox QA"))
            println("INBOX_HTTP_QA page_ms=$pageMs thumbnail_ms=$mediaMs image_bytes=${image.size}; synthetic loopback only")
        }}} finally {
            for(account in listOf(mine,partner,stranger)) {account.second.close();account.first.close()}
            lifetime.close();Dispatchers.resetMain();dispatcher.close()
        }
    }
}
