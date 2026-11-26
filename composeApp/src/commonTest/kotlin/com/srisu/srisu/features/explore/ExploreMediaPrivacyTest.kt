@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.srisu.srisu.features.explore

import androidx.lifecycle.ViewModelStore
import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.core.lifecycle.ApplicationLifetime
import com.srisu.srisu.features.explore.data.*
import com.srisu.srisu.features.explore.presentation.ExploreViewModel
import com.srisu.srisu.features.moments.FakeMoments
import com.srisu.srisu.features.moments.MomentFixtures
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

class ExploreMediaPrivacyTest {
    private val dispatcher=StandardTestDispatcher()
    @BeforeTest fun before() {Dispatchers.setMain(dispatcher)}
    @AfterTest fun after() {Dispatchers.resetMain()}
    private class Harness(source:ExploreSource) {
        val sessions=MomentFixtures.sessions();val lifetime=ApplicationLifetime()
        val vm=ExploreViewModel(source,FaveStore(source,sessions,lifetime),sessions,FakeMoments())
        private val owner=ViewModelStore().apply {put("explore",vm)}
        fun close() {owner.clear();lifetime.close()}
    }

    @Test fun stripAndCardCoalesceProtectedImageRequestsButReturnReauthorizes()=runTest(dispatcher) {
        val gate=CompletableDeferred<Unit>();var calls=0
        val source=object:FakeExplore() {
            override suspend fun image(url:String):ByteArray? {calls++;gate.await();return byteArrayOf(calls.toByte())}
        }
        val h=Harness(source)
        try {
            runCurrent();h.vm.active(true);runCurrent()
            val strip=async {h.vm.image("protected")};val card=async {h.vm.image("protected")};runCurrent()
            assertEquals(1,calls);gate.complete(Unit);runCurrent();assertContentEquals(strip.await(),card.await())
            h.vm.image("protected");assertEquals(1,calls)
            h.vm.active(false);assertNull(h.vm.image("protected"));h.vm.active(true);runCurrent()
            assertContentEquals(byteArrayOf(2),h.vm.image("protected"));assertEquals(2,calls)
        } finally {h.close()}
    }

    @Test fun oldGenerationCannotPopulateImageCacheAfterQueryChange()=runTest(dispatcher) {
        val gate=CompletableDeferred<Unit>();var calls=0
        val source=object:FakeExplore() {
            override suspend fun image(url:String):ByteArray? {
                val number=++calls
                if(number==1) withContext(NonCancellable) {gate.await()}
                return byteArrayOf(number.toByte())
            }
        }
        val h=Harness(source)
        try {
            runCurrent();h.vm.active(true);runCurrent()
            val photo=async {h.vm.image("protected")};runCurrent()
            h.vm.setQuery("Alex");runCurrent();gate.complete(Unit);runCurrent()
            assertContentEquals(byteArrayOf(2),photo.await())
            assertContentEquals(byteArrayOf(2),h.vm.image("protected"));assertEquals(2,calls)
        } finally {h.close()}
    }

    @Test fun openSavedLibraryPeriodicallyReauthorizesItsLoadedDepthAndHidesOnFailure()=runTest(dispatcher) {
        var hidden=false;var failed=false;val requests=mutableListOf<String?>()
        val source=object:FakeExplore() {
            override suspend fun saved(cursor:String?):ExploreResult<SavedPage> {
                requests+=cursor
                if(failed) return MomentFixtures.failed(403)
                val id=if(cursor==null) 10L else 11L
                return NetworkAPIResult.Success(SavedPage(listOf(SavedCouple(id,
                    couple=if(hidden) null else ExploreCouple(id,"Alex & Sam"),available=!hidden)),
                    next=if(cursor==null) "https://example.invalid/?cursor=two" else null,totalCount=2))
            }
        }
        val h=Harness(source)
        try {
            runCurrent();h.vm.active(true);h.vm.manage(true);runCurrent();h.vm.library(true);runCurrent()
            assertEquals(2,h.vm.state.value.library.rows.size)
            hidden=true;advanceTimeBy(30_000);runCurrent()
            assertEquals(listOf<String?>(null,"two",null,"two"),requests)
            assertEquals(2,h.vm.state.value.library.rows.size);assertTrue(h.vm.state.value.library.rows.all {it.couple==null})
            failed=true;advanceTimeBy(30_000);runCurrent()
            assertFalse(h.vm.state.value.library.loaded);assertNotNull(h.vm.state.value.library.error)
        } finally {h.close()}
    }

    @Test fun explicitRefreshRecreatesImageEffectsOnlyAfterSuccessAndAutomaticChecksDoNot()=runTest(dispatcher) {
        var gate:CompletableDeferred<Unit>?=null
        val source=object:FakeExplore() {
            override suspend fun feed(section:String,scope:String,cursor:String?,query:String,interestId:Int?):ExploreResult<ExplorePage> {
                gate?.await();return super.feed(section,scope,cursor,query,interestId)
            }
        }
        val h=Harness(source)
        try {
            runCurrent();h.vm.active(true);runCurrent();assertEquals(0L,h.vm.state.value.imageRevision)
            gate=CompletableDeferred();h.vm.refresh();runCurrent()
            assertEquals(0L,h.vm.state.value.imageRevision);assertTrue(h.vm.state.value.global.fresh)
            requireNotNull(gate).complete(Unit);runCurrent();assertEquals(1L,h.vm.state.value.imageRevision)
            advanceTimeBy(30_000);runCurrent();assertEquals(1L,h.vm.state.value.imageRevision)
            h.vm.active(false);h.vm.active(true);runCurrent();assertEquals(1L,h.vm.state.value.imageRevision)
        } finally {h.close()}
    }

    @Test fun repeatingSavedCursorCannotLeaveOldIdentitiesAfterReauthorizationFailure()=runTest(dispatcher) {
        var broken=false;var calls=0
        val source=object:FakeExplore() {
            override suspend fun saved(cursor:String?):ExploreResult<SavedPage> {
                calls++
                return NetworkAPIResult.Success(if(broken) SavedPage(next="https://example.invalid/?cursor=repeated")
                    else SavedPage(listOf(SavedCouple(10,ExploreCouple(10,"Alex & Sam"),true))))
            }
        }
        val h=Harness(source)
        try {
            runCurrent();h.vm.manage(true);runCurrent();assertEquals(1,h.vm.state.value.library.rows.size)
            broken=true;h.vm.library();runCurrent()
            assertEquals(3,calls);assertFalse(h.vm.state.value.library.loaded)
            assertTrue(h.vm.state.value.library.rows.isEmpty());assertNotNull(h.vm.state.value.library.error)
            assertNull(h.vm.state.value.savedTotal)
        } finally {h.close()}
    }
}
