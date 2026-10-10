@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.srisu.srisu.core

import androidx.lifecycle.ViewModelStore
import com.srisu.srisu.core.data.remote.*
import com.srisu.srisu.core.lifecycle.ApplicationLifetime
import com.srisu.srisu.features.coupleprofile.data.*
import com.srisu.srisu.features.coupleprofile.presentation.*
import com.srisu.srisu.features.home.profile.data.InterestCatalogueRepository
import com.srisu.srisu.features.home.profile.data.remote.api.ProfileApiService
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import kotlin.test.*

class CoupleProfileStateTest {
    private val dispatcher=StandardTestDispatcher()
    private val headers=headersOf(HttpHeaders.ContentType,"application/json")
    @BeforeTest fun before(){Dispatchers.setMain(dispatcher)}
    @AfterTest fun after(){Dispatchers.resetMain()}
    private fun fixture(id: Long=1, song: String?=null): String {
        val source=ApiJson.parseToJsonElement(CoreContractFixtures.COUPLE_PROFILE_MEMBER).jsonObject
        val data=source.getValue("data").jsonObject.toMutableMap()
        data["id"]=JsonPrimitive(id)
        song?.let { data["song"]=buildJsonObject{put("title",it)} }
        return JsonObject(source+ ("data" to JsonObject(data))).toString()
    }
    private fun harness(handler: MockRequestHandler): Harness {
        val sessions=session();val lifetime=ApplicationLifetime()
        val client=HttpClientFactory.create(sessions,environment,MockEngine(MockEngineConfig().apply{dispatcher=this@CoupleProfileStateTest.dispatcher;addHandler(handler)}))
        val repo=CoupleProfileRepository(client,environment,sessions)
        val catalogue=InterestCatalogueRepository(MemoryCatalogue(),ProfileApiService(client,environment),environment,sessions)
        val vm=CoupleProfileViewModel(repo,sessions,catalogue,lifetime)
        val store=ViewModelStore();store.put("profile",vm)
        return Harness(vm,sessions){store.clear();lifetime.close();client.close()}
    }
    private data class Harness(val vm: CoupleProfileViewModel,val sessions: com.srisu.srisu.core.session.SessionCoordinator,val close:()->Unit)

    @Test fun failedSaveKeepsDraftAndOnlyConfirmedSaveClosesEditor()=runTest(dispatcher){
        var status=HttpStatusCode.Conflict;var calls=0
        val h=harness { request -> if(request.method==HttpMethod.Patch){calls++;respond(if(status==HttpStatusCode.OK) fixture(song="Saved") else "",status,headers)}else respond(fixture(),headers=headers) }
        try {
            h.vm.enter(1);advanceUntilIdle();h.vm.open(ProfilePage.SONG);h.vm.edit("title","My draft")
            h.vm.save();h.vm.save();advanceUntilIdle()
            assertEquals(1,calls);assertEquals(ProfilePage.SONG,h.vm.state.value.page)
            assertEquals("My draft",h.vm.state.value.draft?.values?.get("title"));assertNotNull(h.vm.state.value.error)
            assertNull(h.vm.state.value.profile?.song)
            status=HttpStatusCode.OK;h.vm.save();advanceUntilIdle()
            assertEquals(ProfilePage.PROFILE,h.vm.state.value.page);assertNull(h.vm.state.value.draft)
            assertEquals("Saved",h.vm.state.value.profile?.song?.title)
        } finally {h.close()}
    }
    @Test fun switchProfileRejectsLateResponseAndLogoutClearsDraft()=runTest(dispatcher){
        val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        val h=harness { request -> if(request.url.encodedPath.endsWith("/1/")){entered.complete(Unit);release.await();respond(fixture(1),headers=headers)}else respond(fixture(2),headers=headers) }
        try {
            h.vm.enter(1);runCurrent();entered.await();h.vm.enter(2);runCurrent();release.complete(Unit);advanceUntilIdle()
            assertEquals(2,h.vm.state.value.profile?.id)
            h.vm.open(ProfilePage.STORY_EDIT);h.vm.edit("how_met","Private draft")
            h.sessions.clearSession();runCurrent()
            assertNull(h.vm.state.value.profile);assertNull(h.vm.state.value.draft);assertTrue(h.vm.state.value.images.isEmpty())
        } finally {h.close()}
    }
    @Test fun permissionLossClearsProtectedStateAndCannotOpenEditor()=runTest(dispatcher){
        var owner=true
        val h=harness { respond(if(owner)fixture() else CoreContractFixtures.COUPLE_PROFILE_VISITOR,headers=headers) }
        try {
            h.vm.enter(1);advanceUntilIdle();h.vm.open(ProfilePage.STORY_EDIT);h.vm.edit("how_met","Private")
            owner=false;h.vm.refresh();advanceUntilIdle()
            assertNull(h.vm.state.value.draft);assertNull(h.vm.state.value.profile?.members)
            h.vm.open(ProfilePage.SONG);assertEquals(ProfilePage.PROFILE,h.vm.state.value.page)
        } finally {h.close()}
    }
    @Test fun refreshPreservesDraftAndOriginalRevisionUntilExplicitReload()=runTest(dispatcher){
        var song="Original"
        val h=harness { respond(fixture(song=song),headers=headers) }
        try {
            h.vm.enter(1);advanceUntilIdle();h.vm.open(ProfilePage.SONG);h.vm.edit("title","Draft")
            song="Partner edit";h.vm.refresh();advanceUntilIdle()
            assertEquals("Partner edit",h.vm.state.value.profile?.song?.title)
            assertEquals("Draft",h.vm.state.value.draft?.values?.get("title"))
            assertFalse(h.vm.back());assertTrue(h.vm.state.value.discardPrompt)
            h.vm.keepEditing();h.vm.reloadDraft()
            assertEquals("Partner edit",h.vm.state.value.draft?.values?.get("title"))
        } finally {h.close()}
    }
    @Test fun mismatchedResourceNeverAppearsAndVisitorFixtureHasNoPrivateFields()=runTest(dispatcher){
        val h=harness { respond(fixture(2),headers=headers) }
        try {h.vm.enter(1);advanceUntilIdle();assertNull(h.vm.state.value.profile);assertNotNull(h.vm.state.value.error)}finally{h.close()}
        val visitor=ApiJson.decodeFromJsonElement<CoupleProfile>(ApiJson.parseToJsonElement(CoreContractFixtures.COUPLE_PROFILE_VISITOR).jsonObject.getValue("data"))
        assertFalse(visitor.canEdit);assertNull(visitor.members);assertNull(visitor.story);assertNull(visitor.sharing);assertTrue(visitor.revisions.isEmpty())
    }
    @Test fun discoveryRejectsExpiredRowsAndBackLeavesRoute()=runTest(dispatcher){
        val h=harness { respond("""{"data":{"results":[{"couple":{"id":9},"preview":{"id":4,"title":"Expired","expires_at":"2000-01-01T00:00:00Z"}},{"couple":{"id":8},"preview":{"id":5,"title":"Available","expires_at":"2099-01-01T00:00:00Z"}}],"next":null}}""",headers=headers) }
        try {
            h.vm.enter(null,"DISCOVER");advanceUntilIdle()
            assertEquals(listOf(8L),h.vm.state.value.discovery.map{it.couple.id})
            assertNull(h.vm.state.value.profile);assertTrue(h.vm.back())
        } finally {h.close()}
    }
    @Test fun invalidMediaOriginDoesNotUseAuthenticatedClient()=runTest(dispatcher){
        var calls=0
        val h=harness { calls++;respond(fixture(),headers=headers) }
        try {
            h.vm.loadImage("https://untrusted.invalid/api/social/cover/");advanceUntilIdle()
            assertEquals(0,calls);assertTrue(h.vm.state.value.images.isEmpty())
        } finally {h.close()}
    }

}
