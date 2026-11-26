@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.srisu.srisu.features.explore

import androidx.lifecycle.ViewModelStore
import com.srisu.srisu.core.data.remote.ApiJson
import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.core.lifecycle.ApplicationLifetime
import com.srisu.srisu.features.explore.data.*
import com.srisu.srisu.features.explore.presentation.ExploreViewModel
import com.srisu.srisu.features.moments.FakeMoments
import com.srisu.srisu.features.moments.MomentFixtures
import com.srisu.srisu.features.moments.data.MomentPreview
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

class ExploreDiscoveryStateTest {
    private val dispatcher=StandardTestDispatcher()
    @BeforeTest fun before() {Dispatchers.setMain(dispatcher)}
    @AfterTest fun after() {Dispatchers.resetMain()}
    private class Harness(source: ExploreSource) {
        val sessions=MomentFixtures.sessions()
        val lifetime=ApplicationLifetime()
        val faves=FaveStore(source,sessions,lifetime)
        val vm=ExploreViewModel(source,faves,sessions,FakeMoments())
        private val store=ViewModelStore().apply {put("explore",vm)}
        fun close() {store.clear();lifetime.close()}
    }
    private data class Request(val section:String,val cursor:String?,val query:String,val interest:Int?)

    @Test fun searchDebouncesAndCancelledResponsesCannotReplaceNewResults()=runTest(dispatcher) {
        val old=CompletableDeferred<Unit>()
        val requests=mutableListOf<Request>()
        val source=object:FakeExplore() {
            override suspend fun feed(section:String,scope:String,cursor:String?,query:String,interestId:Int?):ExploreResult<ExplorePage> {
                requests+=Request(section,cursor,query,interestId)
                if(query=="A") withContext(NonCancellable) {old.await()}
                return NetworkAPIResult.Success(page.copy(results=listOf(card(if(query=="Alex") 12 else 10))))
            }
        }
        val h=Harness(source)
        try {
            runCurrent();h.vm.active(true);runCurrent()
            h.vm.setQuery("A");runCurrent();advanceTimeBy(299);runCurrent();assertEquals(1,requests.size)
            advanceTimeBy(1);runCurrent();assertEquals("A",requests.last().query)
            h.vm.setQuery("Al");h.vm.setQuery("Alex");runCurrent();advanceTimeBy(300);runCurrent()
            old.complete(Unit);runCurrent()
            assertEquals(listOf("","A","Alex"),requests.map {it.query})
            assertEquals(listOf(12L),h.vm.state.value.global.cards.map {it.couple.id})
            assertFalse(h.vm.state.value.global.refreshing)
        } finally {h.close()}
    }

    @Test fun queryAndInterestAreBoundToPaginationAndDoNotFilterPrivateFaves()=runTest(dispatcher) {
        val requests=mutableListOf<Request>()
        val source=object:FakeExplore() {
            override suspend fun feed(section:String,scope:String,cursor:String?,query:String,interestId:Int?):ExploreResult<ExplorePage> {
                requests+=Request(section,cursor,query,interestId)
                return NetworkAPIResult.Success(page.copy(next=if(cursor==null) "https://example.invalid/?cursor=page-two" else null))
            }
        }
        val h=Harness(source)
        try {
            runCurrent();h.vm.setQuery("  Alex  Sam ");h.vm.selectInterest(4);h.vm.active(true);runCurrent()
            h.vm.more();runCurrent()
            assertEquals(Request("global","page-two","Alex Sam",4),requests.last())
            h.vm.selectInterest(7);runCurrent()
            assertEquals(Request("global",null,"Alex Sam",7),requests.last())
            h.vm.select("faves");runCurrent()
            assertEquals(Request("faves",null,"",null),requests.last())
            assertEquals(7,h.vm.state.value.interestId)
        } finally {h.close()}
    }

    @Test fun returningRevalidatesWithCurrentFiltersAndNeverShowsPullIndicator()=runTest(dispatcher) {
        val checks=mutableListOf<Pair<String,Int?>>()
        val source=object:FakeExplore() {
            override suspend fun validate(ids:List<Long>,section:String,query:String,interestId:Int?):ExploreResult<ExplorePage> {
                checks+=query to interestId
                return super.validate(ids,section,query,interestId)
            }
        }
        val h=Harness(source)
        try {
            runCurrent();h.vm.setQuery("Alex");h.vm.selectInterest(4);h.vm.active(true);runCurrent()
            h.vm.active(false);assertFalse(h.vm.state.value.global.fresh)
            h.vm.active(true);assertFalse(h.vm.state.value.global.refreshing);runCurrent()
            assertEquals(listOf<Pair<String,Int?>>("Alex" to 4),checks);assertTrue(h.vm.state.value.global.fresh)
            advanceTimeBy(30_000);runCurrent();assertEquals(2,checks.size)
            assertFalse(h.vm.state.value.global.refreshing)
        } finally {h.close()}
    }

    @Test fun onlyManualRefreshOwnsIndicatorAndCancellationClearsIt()=runTest(dispatcher) {
        var gate=CompletableDeferred<Unit>()
        val source=object:FakeExplore() {
            override suspend fun feed(section:String,scope:String,cursor:String?,query:String,interestId:Int?):ExploreResult<ExplorePage> {
                gate.await();return super.feed(section,scope,cursor,query,interestId)
            }
        }
        val h=Harness(source)
        try {
            runCurrent();h.vm.active(true);runCurrent()
            assertTrue(h.vm.state.value.global.loading);assertFalse(h.vm.state.value.global.refreshing)
            gate.complete(Unit);runCurrent();gate=CompletableDeferred()
            h.vm.refresh();runCurrent();assertTrue(h.vm.state.value.global.refreshing)
            h.vm.active(false);assertFalse(h.vm.state.value.global.refreshing)
            gate.complete(Unit);runCurrent();assertFalse(h.vm.state.value.global.fresh)
        } finally {h.close()}
    }

    @Test fun savedCountIncludesUnloadedAndUnavailableSavedCouples()=runTest(dispatcher) {
        val source=object:FakeExplore() {
            override suspend fun saved(cursor:String?):ExploreResult<SavedPage> = NetworkAPIResult.Success(
                SavedPage(listOf(SavedCouple(10,available=false)),next="https://example.invalid/?cursor=next",totalCount=43))
        }
        val h=Harness(source)
        try {
            runCurrent();h.vm.active(true);runCurrent()
            assertEquals(43,h.vm.state.value.savedTotal)
            assertEquals(43,h.vm.state.value.library.totalCount)
            assertEquals(1,h.vm.state.value.library.rows.size)
        } finally {h.close()}
    }

    @Test fun expiredPreviewDoesNotRemoveAnotherEligibleMomentFromTheSameCouple()=runTest(dispatcher) {
        val expired=MomentPreview(1,expiresAt=MomentFixtures.NOW)
        val live=MomentPreview(2,expiresAt="2026-10-23T06:00:00Z")
        val source=FakeExplore().apply {page=page.copy(results=listOf(FakeExplore.card(10).copy(preview=expired,previews=listOf(expired,live))))}
        val h=Harness(source)
        try {
            runCurrent();h.vm.active(true);runCurrent()
            assertEquals(listOf(2L),h.vm.state.value.global.cards.single().visiblePreviews.map {it.id})
            assertEquals(2L,h.vm.state.value.global.cards.single().preview.id)
        } finally {h.close()}
    }

    @Test fun legacyPayloadAndBoundedDistinctPreviewsRemainCompatible() {
        val legacy=ApiJson.decodeFromString<ExploreCard>("""{"couple":{"id":10},"preview":{"id":1,"expires_at":"2026-10-23T06:00:00Z"}}""")
        assertEquals(listOf(1L),legacy.visiblePreviews.map {it.id})
        assertTrue(legacy.couple.members.isEmpty());assertTrue(legacy.couple.sharedInterests.isEmpty())
        assertNull(legacy.preview.createdAt)
        val dated=ApiJson.decodeFromString<MomentPreview>("""{"id":1,"created_at":"2026-10-22T04:00:00Z","expires_at":"2026-10-23T06:00:00Z"}""")
        assertEquals("2026-10-22T04:00:00Z",dated.createdAt)
        val repeated=legacy.copy(previews=listOf(1L,1L,2L,3L,4L).map {legacy.preview.copy(id=it)})
        assertEquals(listOf(1L,2L,3L),repeated.visiblePreviews.map {it.id})
    }

    @Test fun publicFollowingRequiresFreshDisclosureAndNeverChangesPrivateFaves()=runTest(dispatcher) {
        var writes=0
        val source=object:FakeExplore() {
            override suspend fun setFollow(id:Long,desired:Boolean,version:Long,shareIdentity:Boolean):ExploreResult<PublicFollowSnapshot> {
                writes++;assertTrue(shareIdentity)
                return super.setFollow(id,desired,version,shareIdentity)
            }
        }
        val h=Harness(source)
        try {
            runCurrent();h.vm.follow(10,true);runCurrent()
            assertEquals(0,writes);assertNotNull(h.vm.state.value.following[10]?.error)
            h.vm.follow(10,true,shareIdentity=true);runCurrent()
            assertEquals(1,writes);assertEquals(true,h.vm.state.value.following[10]?.snapshot?.isFollowing)
            assertTrue(source.savedIds.isEmpty());assertTrue(source.writes.isEmpty())
        } finally {h.close()}
    }

    @Test fun followersAreRecheckedOnReopenAndNeverSurviveAnAccountChange()=runTest(dispatcher) {
        val gate=CompletableDeferred<Unit>();var delay=false;var fail=false
        val source=object:FakeExplore() {
            override suspend fun followers(cursor:String?):ExploreResult<PublicFollowersPage> {
                if(delay) withContext(NonCancellable) {gate.await()}
                return if(fail) MomentFixtures.failed(403) else NetworkAPIResult.Success(PublicFollowersPage(3,
                    listOf(PublicFollower(5,6,"Alex",followedAt=MomentFixtures.NOW))))
            }
        }
        val h=Harness(source)
        try {
            runCurrent();h.vm.showFollowers(true);runCurrent();assertEquals(1,h.vm.state.value.followers.rows.size)
            h.vm.showFollowers(false);assertTrue(h.vm.state.value.followers.rows.isEmpty())
            fail=true;h.vm.showFollowers(true);runCurrent();assertNotNull(h.vm.state.value.followers.error)
            assertTrue(h.vm.state.value.followers.rows.isEmpty())
            fail=false;delay=true;h.vm.loadFollowers();runCurrent();h.sessions.clearSession();runCurrent()
            gate.complete(Unit);runCurrent();assertTrue(h.vm.state.value.followers.rows.isEmpty())
            assertFalse(h.vm.state.value.followersOpen)
        } finally {h.close()}
    }
}
