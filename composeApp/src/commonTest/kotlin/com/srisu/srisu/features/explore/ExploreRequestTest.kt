package com.srisu.srisu.features.explore

import com.srisu.srisu.core.environment
import com.srisu.srisu.core.session
import com.srisu.srisu.core.data.remote.*
import com.srisu.srisu.features.coupleprofile.data.CoupleProfileRepository
import com.srisu.srisu.features.explore.data.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class ExploreRequestTest {
    @Test fun continuationAndReauthorizationCarryTheServerSearchAndCatalogueFilter()=runTest {
        val paths=mutableListOf<String>()
        val sessions=session()
        val client=HttpClientFactory.create(sessions,environment,MockEngine {request->
            paths+=request.url.encodedPath
            assertEquals("Alex Sam",request.url.parameters["q"])
            assertEquals("4",request.url.parameters["interest_id"])
            when(paths.size) {
                1->{assertEquals("global",request.url.parameters["section"]);assertEquals("10",request.url.parameters["page_size"])}
                2->{assertEquals("opaque-next",request.url.parameters["cursor"]);assertNull(request.url.parameters["scope"])}
                3->{assertTrue(request.url.encodedPath.endsWith("/visible/"));assertEquals("10,11",request.url.parameters["couples"])}
            }
            respond("""{"data":{"results":[],"server_time":"2026-10-23T00:00:00Z","q":"alex sam","interest_id":4}}""",
                headers=headersOf(HttpHeaders.ContentType,"application/json"))
        })
        try {
            val source=ExploreRepository(client,environment,CoupleProfileRepository(client,environment,sessions))
            assertIs<NetworkAPIResult.Success<ExplorePage?>>(source.feed("global","local",null,"Alex Sam",4))
            source.feed("global","local","opaque-next","Alex Sam",4)
            source.validate(listOf(10,11),"global","Alex Sam",4)
            assertEquals(3,paths.size)
        } finally {client.close()}
    }

    @Test fun publicFollowRequiresExplicitConsentAndConditionalRequestWhileDeleteDoesNotReshare()=runTest {
        var calls=0
        val sessions=session()
        val client=HttpClientFactory.create(sessions,environment,MockEngine {request->
            calls++
            assertTrue(request.url.encodedPath.endsWith("/couple-follows/10/"))
            assertEquals("4",request.headers[HttpHeaders.IfMatch])
            if(calls==1) {
                assertEquals(HttpMethod.Put,request.method)
                assertEquals(JsonPrimitive(true),ApiJson.parseToJsonElement((request.body as TextContent).text).jsonObject["share_identity"])
            } else assertEquals(HttpMethod.Delete,request.method)
            respond("""{"data":{"couple_id":10,"is_following":${calls==1},"can_follow":true,"version":5}}""",
                headers=headersOf(HttpHeaders.ContentType,"application/json"))
        })
        try {
            val source=ExploreRepository(client,environment,CoupleProfileRepository(client,environment,sessions))
            assertIs<NetworkAPIResult.Error<PublicFollowSnapshot?>>(source.setFollow(10,true,4,false))
            assertEquals(0,calls)
            assertIs<NetworkAPIResult.Success<PublicFollowSnapshot?>>(source.setFollow(10,true,4,true))
            assertIs<NetworkAPIResult.Success<PublicFollowSnapshot?>>(source.setFollow(10,false,4,false))
            assertEquals(2,calls)
        } finally {client.close()}
    }
}
