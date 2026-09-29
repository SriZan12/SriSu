package com.srisu.srisu.core

import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.core.data.remote.*
import com.srisu.srisu.core.session.*
import com.srisu.srisu.features.auth.data.remote.api.AuthApiService
import com.srisu.srisu.features.auth.data.remote.dto.AuthDTO
import com.srisu.srisu.features.auth.data.remote.dto.ProfileSetupDTO
import com.srisu.srisu.features.chat.data.remote.api.ChatApiService
import com.srisu.srisu.features.home.connection.data.remote.api.ConnectionApiService
import com.srisu.srisu.features.home.connection.coupleconnection.data.remote.dto.*
import com.srisu.srisu.features.home.profile.data.dto.ProfileUpdateDTO
import com.srisu.srisu.features.home.profile.data.remote.api.ProfileApiService
import com.srisu.srisu.features.home.suggestions.data.api.SuggestionApiService
import com.srisu.srisu.features.home.suggestions.data.dto.UserPreferenceDTO
import com.srisu.srisu.utils.Constants.Auth.SESSION_KEY
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64
import kotlin.test.*

class ApiRouteContractTest {
    @Test fun everyServiceUsesTheClientOriginAndBackendRouteContract() = runTest {
        // Deliberately differs from the generated build URL. A global BASE_URL
        // lookup must not bypass the environment owning this client's credentials.
        val environment = ApiEnvironment("https://contract.example:8443/")
        val routes = ApiJson.parseToJsonElement(CoreContractFixtures.HTTP_ROUTES).jsonObject
        val sessions = session()
        var expected = ""
        val visited = mutableSetOf<String>()
        val room = "12345678-1234-1234-1234-123456789abc"
        val client = HttpClientFactory.create(sessions, environment, MockEngine { request ->
            val route = routes.getValue(expected).jsonObject
            assertEquals(environment.baseUrl, "${request.url.protocol.name}://${request.url.host}:${request.url.port}/")
            assertEquals(route.getValue("method").jsonPrimitive.content, request.method.value)
            assertEquals(route.getValue("path").jsonPrimitive.content.replace("{id}", "1").replace("{uuid}", room), request.url.encodedPath)
            assertEquals(route.getValue("query").jsonArray.map { it.jsonPrimitive.content }.toSet(), request.url.parameters.names())
            if (expected == "find-partner") assertEquals("+15005550123", request.url.parameters["phone_number"])
            assertEquals("core-1", request.headers["X-SriSu-Contract"])
            assertEquals("auth-1", request.headers["X-SriSu-Auth"])
            if (route.getValue("public").jsonPrimitive.boolean) assertNull(request.headers[HttpHeaders.Authorization])
            else assertEquals("Bearer synthetic-1", request.headers[HttpHeaders.Authorization])
            visited += expected
            respond(if (expected == "refresh") CoreContractFixtures.AUTH_REFRESH else """{"data":null}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        })
        suspend fun check(name: String, call: suspend () -> ResultHandler<*>) {
            expected = name
            assertIs<NetworkAPIResult.Success<*>>(call().result)
        }
        try {
            val auth = AuthApiService(client)
            val profile = ProfileApiService(client)
            val chat = ChatApiService(client)
            val connections = ConnectionApiService(client)
            val suggestions = SuggestionApiService(client)
            check("send-otp") { auth.sendOTPRequest(AuthDTO(phoneNumber = "+15005550123")) }
            check("verify-otp") { auth.sendVerifyOtpRequest("+15005550123", "000000") }
            check("profile-get") { auth.getProfile() }
            check("profile-get") { profile.getProfile() }
            check("profile-patch") { auth.updateName("Synthetic User", "synthetic") }
            check("profile-patch") { auth.sendProfileSetupRequest(ProfileSetupDTO(), null) }
            check("profile-put") { profile.sendUpdateProfileRequest(1, ProfileUpdateDTO(), null, null) }
            check("logout") { auth.revoke("synthetic-refresh") }
            check("interests") { profile.getInterestList() }
            check("rooms") { chat.rooms("synthetic-cursor") }
            check("history") { chat.history(room, 1) }
            check("media-upload") { chat.uploadMedias(emptyList()) }
            check("suggestions") { suggestions.getUserSuggestions(1, 20) }
            check("suggestion-profile") { suggestions.getSuggestionProfile(1) }
            check("preferences-get") { suggestions.getUserPreferences() }
            check("preferences-post") { suggestions.setUserPreferences(UserPreferenceDTO()) }
            check("preferences-put") { suggestions.updateUserPreferences(UserPreferenceDTO(), 1) }
            check("single-post") { connections.sendSingleConnectionRequest("+15005550101", "+15005550102") }
            check("single-post") { profile.sendSingleConnectionRequest("+15005550101", "+15005550102") }
            check("single-put") { connections.updateSingleConnectionRequestStatus(1, SingleConnectionDTO()) }
            check("single-put") { connections.updateCrushRequest(1, SingleConnectionDTO()) }
            check("couple-post") { connections.sendCoupleConnectionRequest("+15005550101", "+15005550102") }
            check("couple-put") { connections.updateCoupleConnectionRequestStatus(1, CoupleConnectionDTO()) }
            check("couple-sent") { connections.getSentLoveRequests(20, 1) }
            check("couple-received") { connections.getLoveRequests(1, 20) }
            check("single-sent") { connections.getMyCrushList(1, 20) }
            check("single-received") { connections.getCrushOnMeList(1, 20) }
            check("find-partner") { connections.sendFindYourPartnerRequest("+15005550123") }
            check("connection-requested") { connections.haveCoupleConnectionRequested() }
            val expired = "synthetic." + Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
                .encode("""{"exp":1,"sid":"synthetic"}""".encodeToByteArray()) + ".synthetic"
            sessions.saveSession(ApiJson.encodeToString(Session(id = 1, access = expired, refresh = "synthetic-refresh")), SESSION_KEY)
            expected = "refresh"
            assertNull(client.refreshSessionIfNeeded())
            assertEquals(routes.keys, visited)
        } finally { client.close() }
    }
}
