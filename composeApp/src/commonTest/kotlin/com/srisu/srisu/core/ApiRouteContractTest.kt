package com.srisu.srisu.core

import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.core.data.remote.*
import com.srisu.srisu.core.session.*
import com.srisu.srisu.features.auth.data.remote.api.AuthApiService
import com.srisu.srisu.features.auth.data.remote.dto.AuthDTO
import com.srisu.srisu.features.auth.data.remote.dto.ProfileSetupDTO
import com.srisu.srisu.features.home.connection.data.remote.api.ConnectionApiService
import com.srisu.srisu.features.home.connection.coupleconnection.data.remote.dto.*
import com.srisu.srisu.features.home.profile.data.dto.ProfileUpdateDTO
import com.srisu.srisu.features.home.profile.data.remote.api.ProfileApiService
import com.srisu.srisu.features.chat.matrix.MatrixSessionApiService
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
        val room = "12345678-1234-4234-a234-123456789abc"
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
            respond(if (expected == "refresh") CoreContractFixtures.AUTH_REFRESH else if(expected=="moment-get") CoreContractFixtures.MOMENT_PRIVATE else """{"data":null}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        })
        suspend fun check(name: String, call: suspend () -> ResultHandler<*>) {
            expected = name
            assertIs<NetworkAPIResult.Success<*>>(call().result)
        }
        try {
            val auth = AuthApiService(client)
            val profile = ProfileApiService(client)
            val connections = ConnectionApiService(client)
            check("send-otp") { auth.sendOTPRequest(AuthDTO(phoneNumber = "+15005550123")) }
            check("verify-otp") { auth.sendVerifyOtpRequest("+15005550123", "000000") }
            check("profile-get") { auth.getProfile() }
            check("profile-get") { profile.getProfile() }
            check("profile-patch") { auth.updateName("Synthetic User", "synthetic") }
            check("profile-patch") { auth.sendProfileSetupRequest(ProfileSetupDTO(), null) }
            check("profile-put") { profile.sendUpdateProfileRequest(1, ProfileUpdateDTO(), null, null) }
            check("logout") { auth.revoke("synthetic-refresh") }
            check("interests") { profile.getInterestList() }
            check("matrix-session") { ResultHandler(MatrixSessionApiService(client, environment).create(room)) }
            check("couple-post") { connections.sendCoupleConnectionRequest("+15005550101", "+15005550102") }
            check("couple-put") { connections.updateCoupleConnectionRequestStatus(1, CoupleConnectionDTO()) }
            check("couple-sent") { connections.getSentLoveRequests(20, 1) }
            check("couple-received") { connections.getLoveRequests(1, 20) }
            check("find-partner") { connections.sendFindYourPartnerRequest("+15005550123") }
            check("connection-requested") { connections.haveCoupleConnectionRequested() }
            val couple = com.srisu.srisu.features.coupleprofile.data.CoupleProfileRepository(client, environment, sessions)
            check("couple-profile-discovery") { ResultHandler(couple.discovery("synthetic-cursor")) }
            check("couple-profile-self") { ResultHandler(couple.load(null)) }
            check("couple-profile-detail") { ResultHandler(couple.load(1)) }
            for(section in listOf("story", "song", "date", "interests", "sharing")) check("couple-profile-$section") { ResultHandler(couple.save(1,section,buildJsonObject {})) }
            check("couple-profile-cover-save") { ResultHandler(couple.cover(1,"0".repeat(64),.5f,null,null)) }
            check("couple-profile-history") { ResultHandler(couple.history(1,1)) }
            check("couple-profile-cover-choices") { ResultHandler(couple.covers(1,1)) }
            check("couple-profile-plans") { ResultHandler(couple.plans(1,false,1)) }
            check("couple-profile-plan-create") { ResultHandler(couple.createPlan(1,buildJsonObject {})) }
            check("couple-profile-plan") { ResultHandler(couple.plan(1,1)) }
            check("couple-profile-plan-respond") { ResultHandler(couple.respond(1,1,buildJsonObject {})) }
            check("couple-profile-invite") { ResultHandler(couple.invite(1,"how_met",room)) }
            check("couple-fave-add") { ResultHandler(couple.fave(1,true)) }
            check("couple-fave-remove") { ResultHandler(couple.fave(1,false)) }
            expected = "couple-profile-cover-read"; couple.image(environment.baseUrl + "api/social/profiles/1/cover/")
            expected = "couple-profile-member-photo"; couple.image(environment.baseUrl + "api/social/profiles/1/members/1/photo/")
            val explore = com.srisu.srisu.features.explore.data.ExploreRepository(client,environment,couple)
            check("explore-feed") {ResultHandler(explore.feed("global","local",null))}
            check("explore-visible") {ResultHandler(explore.validate(listOf(1),"global"))}
            check("fave-library") {ResultHandler(explore.saved(null))}
            check("fave-state") {ResultHandler(explore.faveState(1))}
            val moments = com.srisu.srisu.features.moments.data.MomentRepository(client,environment,couple)
            check("moment-home") {ResultHandler(moments.home())}
            check("moment-get") {ResultHandler(moments.get(1))}
            check("moment-sequence") {ResultHandler(moments.sequence(1,1,null))}
            check("moment-feed") {ResultHandler(moments.discover("global",null))}
            check("moment-viewed") {ResultHandler(moments.viewed(1))}
            check("moment-react") {ResultHandler(moments.react(1,true))}
            check("moment-caption") {ResultHandler(moments.edit(1,1,"Caption"))}
            check("moment-delete") {ResultHandler(moments.delete(1))}
            check("moment-note") {ResultHandler(moments.note(1,"Appreciation"))}
            check("moment-notes") {ResultHandler(moments.notes(1,1))}
            check("moment-note-reply") {ResultHandler(moments.noteReply(1,"Thank you"))}
            check("moment-draft-create") {ResultHandler(moments.createDraft(room,1))}
            check("moment-draft-get") {ResultHandler(moments.draft(room))}
            check("moment-draft-delete") {ResultHandler(moments.discard(room))}
            check("moment-upload") {ResultHandler(moments.upload(room,room,byteArrayOf(1)) {})}
            check("moment-upload-remove") {ResultHandler(moments.removePhoto(room,room))}
            check("moment-publish") {ResultHandler(moments.publish(room,com.srisu.srisu.features.moments.data.PublishMoment(1,"Caption","private",listOf(room))))}
            expected="moment-photo"; moments.image(environment.baseUrl+"api/social/couple-moments/1/photos/1/")
            expected="moment-creator-photo"; moments.image(environment.baseUrl+"api/social/couple-moments/1/creator-photo/")
            expected="moment-staged-photo"; moments.image(environment.baseUrl+"api/social/moment-drafts/$room/photos/$room/")
            val expired = "synthetic." + Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
                .encode("""{"exp":1,"sid":"synthetic"}""".encodeToByteArray()) + ".synthetic"
            sessions.saveSession(ApiJson.encodeToString(Session(id = 1, access = expired, refresh = "synthetic-refresh")), SESSION_KEY)
            expected = "refresh"
            assertNull(client.refreshSessionIfNeeded())
            assertEquals(routes.keys, visited)
        } finally { client.close() }
    }
}
