package com.srisu.srisu.features.profile

import com.srisu.srisu.core.MemorySession
import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.core.data.remote.*
import com.srisu.srisu.core.session.Session
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.features.home.connection.data.remote.api.ConnectionApiService
import com.srisu.srisu.features.home.profile.data.dto.ProfileUpdateDTO
import com.srisu.srisu.features.home.profile.data.remote.api.ProfileApiService
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
import java.io.File
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PartnerProfileTransportIntegrationTest {
    @Test fun searchLoadOptInInviteAndOptOutAcrossTwoRealHttpClients() = runBlocking {
        val path = System.getenv("SRISU_CORE_INTEGRATION_FILE")
        assumeTrue("Requires a disposable loopback backend", path != null)
        val fixture = ApiJson.parseToJsonElement(File(path!!).readText()).jsonObject
        val origin = fixture.getValue("base_url").jsonPrimitive.content
        require(java.net.URI(origin).host == "127.0.0.1")
        val environment = ApiEnvironment(origin, development = true)
        fun client(prefix: String): io.ktor.client.HttpClient {
            val sessions = SessionCoordinator(MemorySession()).apply {
                saveSession(ApiJson.encodeToString(Session(id = fixture.getValue("${prefix}_id").jsonPrimitive.long,
                    access = fixture.getValue("${prefix}_access").jsonPrimitive.content)), SESSION_KEY)
            }
            return HttpClientFactory.create(sessions, environment, OkHttp.create())
        }
        val viewer = client("profile_viewer")
        val owner = client("profile_target")
        val targetId = fixture.getValue("profile_target_id").jsonPrimitive.long
        val viewerId = fixture.getValue("profile_viewer_id").jsonPrimitive.long
        try { withTimeout(20000) {
            val api = ConnectionApiService(viewer)
            val search = assertNotNull(success(api.sendFindYourPartnerRequest("+15005550105")))
            assertEquals(targetId, search.id?.toLong())
            val grant = assertNotNull(search.profileAccessToken)
            val hidden = assertNotNull(success(api.getPartnerProfile(targetId, grant)))
            assertFalse(hidden.detailsVisible)
            assertNull(hidden.user.bio)
            assertIs<NetworkAPIResult.Error<*>>(api.getPartnerProfile(targetId, null).result)
            val profile = ProfileApiService(owner)
            suspend fun save(sharing: Boolean) = success(profile.sendUpdateProfileRequest(targetId,
                ProfileUpdateDTO(fullName="Profile Maya", username="profile_maya", bio="Coffee and mountain walks.", sharePartnerDetails=sharing), null, null))
            assertEquals(true, save(true)?.user?.sharePartnerDetails)
            val loaded = assertNotNull(success(api.getPartnerProfile(targetId, grant)))
            assertTrue(loaded.detailsVisible)
            assertEquals("Coffee and mountain walks.", loaded.user.bio)
            assertNull(loaded.user.userPhotos)
            assertEquals("available", loaded.connectionState)
            val operation = java.util.UUID.randomUUID().toString()
            val invitation = success(api.sendCoupleConnectionRequest("+15005550104", "+15005550105", operation))
            val replay = success(api.sendCoupleConnectionRequest("+15005550104", "+15005550105", operation))
            assertEquals(assertNotNull(invitation?.id), replay?.id)
            assertEquals("outgoing", success(api.getPartnerProfile(targetId, grant))?.connectionState)
            assertEquals("incoming", success(ConnectionApiService(owner).getPartnerProfile(viewerId, null))?.connectionState)
            assertEquals(false, save(false)?.user?.sharePartnerDetails)
            assertFalse(assertNotNull(success(api.getPartnerProfile(targetId, grant))).detailsVisible)
        } } finally { viewer.close(); owner.close() }
    }
    private fun <T> success(result: ResultHandler<T>): T = assertIs<NetworkAPIResult.Success<T>>(result.result).response
}
