@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.srisu.srisu.features.explore

import androidx.lifecycle.ViewModelStore
import com.srisu.srisu.core.data.remote.*
import com.srisu.srisu.core.lifecycle.ApplicationLifetime
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.features.explore.data.*
import com.srisu.srisu.features.explore.presentation.*
import com.srisu.srisu.features.moments.FakeMoments
import com.srisu.srisu.features.moments.MomentFixtures
import com.srisu.srisu.features.moments.data.MomentPreview
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.JsonObject
import kotlin.test.*

open class FakeExplore : ExploreSource {
    var version=0L
    val savedIds=mutableSetOf<Long>()
    val writes=mutableListOf<Pair<Long,Boolean>>()
    val feeds=mutableListOf<String>()
    var checks=0
    var page=ExplorePage(listOf(card(10)),serverTime=MomentFixtures.NOW)
    override suspend fun feed(section:String,scope:String,cursor:String?):ExploreResult<ExplorePage> {
        feeds+=section
        return NetworkAPIResult.Success(response(section))
    }
    private fun response(section:String) = page.copy(
        results=page.results.filter {section=="global" || it.couple.id in savedIds}.map {it.copy(isFaved=it.couple.id in savedIds)},
        emptyReason=if(section=="faves" && savedIds.isEmpty()) "no_faves" else null,faveVersion=version)
    override suspend fun validate(ids:List<Long>,section:String):ExploreResult<ExplorePage> {
        checks++
        return response(section).let {NetworkAPIResult.Success(it.copy(results=it.results.filter {row->row.couple.id in ids}))}
    }
    override suspend fun saved(cursor:String?):ExploreResult<SavedPage> = NetworkAPIResult.Success(SavedPage(savedIds.map {SavedCouple(it,available=true)},faveVersion=version))
    override suspend fun faveState(id:Long):ExploreResult<FaveSnapshot> = NetworkAPIResult.Success(FaveSnapshot(id,id in savedIds,version))
    override suspend fun setFave(id:Long,desired:Boolean,version:Long):ExploreResult<JsonObject> {
        if(version!=this.version) return MomentFixtures.failed(409)
        writes+=id to desired;this.version++
        if(desired) savedIds+=id else savedIds-=id
        return NetworkAPIResult.Success(null)
    }
    override suspend fun image(url:String):ByteArray?=MomentFixtures.image
    companion object {
        fun card(id:Long)=ExploreCard(ExploreCouple(id,"Alex & Sam"),MomentPreview(id,"A little adventure",expiresAt="2026-10-23T06:00:00Z"),hasUnopened=true)
    }
}

class ExploreStateTest {
    private val dispatcher=StandardTestDispatcher()
    @BeforeTest fun before() {Dispatchers.setMain(dispatcher)}
    @AfterTest fun after() {Dispatchers.resetMain()}
    private class Harness(source:ExploreSource) {
        val sessions:SessionCoordinator=MomentFixtures.sessions()
        val lifetime=ApplicationLifetime()
        val faves=FaveStore(source,sessions,lifetime)
        val vm=ExploreViewModel(source,faves,sessions,FakeMoments())
        private val store=ViewModelStore().apply {put("explore",vm)}
        fun close() {store.clear();lifetime.close()}
    }
    @Test fun oppositeActionsConvergeAndStalePagesCannotUndoConfirmedRemoval()=runTest(dispatcher) {
        val gate=CompletableDeferred<Unit>()
        val source=object:FakeExplore() {
            override suspend fun setFave(id:Long,desired:Boolean,version:Long):ExploreResult<JsonObject> {
                if(desired) gate.await()
                return super.setFave(id,desired,version)
            }
        }
        val h=Harness(source)
        try {
            runCurrent();h.faves.accept(10,false,0);h.faves.set(10,true);runCurrent()
            h.faves.set(10,false);gate.complete(Unit);runCurrent()
            assertEquals(listOf(10L to true,10L to false),source.writes)
            assertFalse(10L in source.savedIds);assertFalse(h.faves.state.value.getValue(10).confirmed)
            h.faves.accept(10,true,1)
            assertFalse(h.faves.state.value.getValue(10).confirmed)
            assertNull(h.faves.state.value.getValue(10).desired)
        } finally {h.close()}
    }
    @Test fun lostAcknowledgementShowsRetryAndReconcilesServerBeforeWriting()=runTest(dispatcher) {
        var fail=true
        val source=object:FakeExplore() {
            override suspend fun setFave(id:Long,desired:Boolean,version:Long):ExploreResult<JsonObject> {
                val result=super.setFave(id,desired,version)
                return if(fail) MomentFixtures.failed() else result
            }
        }
        val h=Harness(source)
        try {
            runCurrent();h.faves.set(10,true);runCurrent()
            assertTrue(10L in source.savedIds)
            assertFalse(h.faves.state.value.getValue(10).confirmed)
            assertNotNull(h.faves.state.value.getValue(10).error)
            fail=false;h.faves.retry(10);runCurrent()
            assertTrue(h.faves.state.value.getValue(10).confirmed)
            assertFalse(h.faves.state.value.getValue(10).pending)
            assertNull(h.faves.state.value.getValue(10).error)
        } finally {h.close()}
    }
    @Test fun accountChangeRejectsUncooperativeOldMutationAndFeed()=runTest(dispatcher) {
        val gate=CompletableDeferred<Unit>()
        val source=object:FakeExplore() {
            override suspend fun feed(section:String,scope:String,cursor:String?):ExploreResult<ExplorePage> = withContext(NonCancellable) {gate.await();super.feed(section,scope,cursor)}
            override suspend fun faveState(id:Long):ExploreResult<FaveSnapshot> = withContext(NonCancellable) {gate.await();super.faveState(id)}
        }
        val h=Harness(source)
        try {
            runCurrent();h.vm.active(true);h.faves.set(10,true);runCurrent()
            h.sessions.clearSession();runCurrent();gate.complete(Unit);runCurrent()
            assertTrue(source.writes.isEmpty());assertTrue(h.faves.state.value.isEmpty())
            assertTrue(h.vm.state.value.global.cards.isEmpty());assertFalse(h.vm.state.value.active)
        } finally {h.close()}
    }
    @Test fun tabsRetainSeparatePagesAndReturnReauthorizesWithoutReranking()=runTest(dispatcher) {
        val source=FakeExplore();source.savedIds+=10
        val h=Harness(source)
        try {
            runCurrent();h.vm.active(true);runCurrent();h.vm.select("faves");runCurrent()
            h.vm.select("global");runCurrent();h.vm.select("faves");runCurrent()
            assertEquals(listOf("global","faves"),source.feeds)
            assertEquals(1,h.vm.state.value.global.cards.size);assertEquals(1,h.vm.state.value.faves.cards.size)
            h.vm.active(false);assertFalse(h.vm.state.value.faves.fresh)
            source.page=source.page.copy(results=emptyList());h.vm.active(true);runCurrent()
            assertEquals(3,source.checks);assertTrue(h.vm.state.value.faves.cards.isEmpty())
        } finally {h.close()}
    }
    @Test fun mutationCancelsObsoleteFavesLoadAndLoadsNewMembership()=runTest(dispatcher) {
        val gate=CompletableDeferred<Unit>();var first=true
        val source=object:FakeExplore() {
            override suspend fun feed(section:String,scope:String,cursor:String?):ExploreResult<ExplorePage> {
                if(section=="faves" && first) {first=false;gate.await();return NetworkAPIResult.Success(page.copy(results=emptyList()))}
                return super.feed(section,scope,cursor)
            }
        }
        val h=Harness(source)
        try {
            runCurrent();h.vm.select("faves");h.vm.active(true);runCurrent()
            h.faves.set(10,true);runCurrent();gate.complete(Unit);runCurrent()
            assertEquals(listOf(10L),h.vm.state.value.faves.cards.map {it.couple.id})
            assertFalse(h.vm.state.value.faves.cursorStale)
        } finally {h.close()}
    }
    @Test fun expiredRowsDisappearAndSavedLibraryRemainsManageable()=runTest(dispatcher) {
        val source=FakeExplore().apply {savedIds+=10;page=page.copy(results=listOf(FakeExplore.card(10).copy(preview=FakeExplore.card(10).preview.copy(expiresAt=MomentFixtures.NOW))))}
        val h=Harness(source)
        try {
            runCurrent();h.vm.active(true);runCurrent()
            assertTrue(h.vm.state.value.global.cards.isEmpty())
            h.vm.manage(true);runCurrent()
            assertEquals(listOf(10L),h.vm.state.value.library.rows.map {it.coupleId})
            h.faves.set(10,false);runCurrent();assertTrue(h.vm.state.value.library.rows.isEmpty())
        } finally {h.close()}
    }
    @Test fun returningToLoadedTabReauthorizesHiddenCards()=runTest(dispatcher) {
        val source=FakeExplore().apply {savedIds+=10}
        val h=Harness(source)
        try {
            runCurrent();h.vm.active(true);runCurrent();h.vm.select("faves");runCurrent()
            source.page=source.page.copy(results=emptyList())
            h.vm.select("global")
            assertFalse(h.vm.state.value.global.fresh)
            runCurrent();assertTrue(h.vm.state.value.global.cards.isEmpty())
        } finally {h.close()}
    }
    @Test fun returningToEmptyFeedDiscoversNewContent()=runTest(dispatcher) {
        val source=FakeExplore().apply {page=page.copy(results=emptyList())}
        val h=Harness(source)
        try {
            runCurrent();h.vm.active(true);runCurrent();assertTrue(h.vm.state.value.global.cards.isEmpty())
            h.vm.active(false);source.page=source.page.copy(results=listOf(FakeExplore.card(11)))
            h.vm.active(true);runCurrent()
            assertEquals(listOf(11L),h.vm.state.value.global.cards.map {it.couple.id})
        } finally {h.close()}
    }
    @Test fun remotelyRemovedLastFaveGetsTheCorrectEmptyState()=runTest(dispatcher) {
        val source=FakeExplore().apply {savedIds+=10}
        val h=Harness(source)
        try {
            runCurrent();h.vm.select("faves");h.vm.active(true);runCurrent()
            h.vm.active(false);source.savedIds.clear();source.version++
            h.vm.active(true);runCurrent()
            assertTrue(h.vm.state.value.faves.cards.isEmpty())
            assertEquals("no_faves",h.vm.state.value.faves.emptyReason)
        } finally {h.close()}
    }
    @Test fun savedLibraryRechecksOnReopenAndRetriesFailedContinuation()=runTest(dispatcher) {
        val source=object:FakeExplore() {
            var requests=mutableListOf<String?>()
            var failure=true
            override suspend fun saved(cursor:String?):ExploreResult<SavedPage> {
                requests+=cursor
                if(cursor!=null && failure) return MomentFixtures.failed()
                return NetworkAPIResult.Success(SavedPage(savedIds.map {SavedCouple(it,available=true)},
                    next="https://example.invalid/?cursor=next",faveVersion=version))
            }
        }.apply {savedIds+=10}
        val h=Harness(source)
        try {
            runCurrent();h.vm.active(true);h.vm.manage(true);runCurrent()
            h.vm.library(true);runCurrent();assertNotNull(h.vm.state.value.library.error)
            source.failure=false;h.vm.library(false);runCurrent()
            assertNull(h.vm.state.value.library.error)
            assertEquals(listOf(null,"next",null),source.requests)
            h.vm.manage(false);source.savedIds.clear();source.version++
            h.vm.manage(true);runCurrent();assertTrue(h.vm.state.value.library.rows.isEmpty())
        } finally {h.close()}
    }
    @Test fun savedPaginationRestartsWhenAnotherDeviceChangedMembership()=runTest(dispatcher) {
        val source=object:FakeExplore() {
            override suspend fun saved(cursor:String?):ExploreResult<SavedPage> = NetworkAPIResult.Success(
                SavedPage(listOf(SavedCouple(if(version==0L) 10 else 12,available=true)),
                    next=if(cursor==null) "https://example.invalid/?cursor=next" else null,faveVersion=version))
        }
        val h=Harness(source)
        try {
            runCurrent();h.vm.manage(true);runCurrent()
            assertEquals(listOf(10L),h.vm.state.value.library.rows.map {it.coupleId})
            source.version++;h.vm.library(true);runCurrent()
            assertEquals(listOf(12L),h.vm.state.value.library.rows.map {it.coupleId})
        } finally {h.close()}
    }
    @Test fun lateCancelledLocalRequestCannotReplaceBroaderResults()=runTest(dispatcher) {
        val gate=CompletableDeferred<Unit>()
        val source=object:FakeExplore() {
            override suspend fun feed(section:String,scope:String,cursor:String?):ExploreResult<ExplorePage> {
                if(scope=="local") return withContext(NonCancellable) {gate.await();NetworkAPIResult.Success(page)}
                return NetworkAPIResult.Success(page.copy(results=listOf(FakeExplore.card(12))))
            }
        }
        val h=Harness(source)
        try {
            runCurrent();h.vm.active(true);runCurrent()
            h.vm.broader();runCurrent();gate.complete(Unit);runCurrent()
            assertEquals("broad",h.vm.state.value.scope)
            assertEquals(listOf(12L),h.vm.state.value.global.cards.map {it.couple.id})
        } finally {h.close()}
    }
    @Test fun savedPreferenceEditsRerankGlobalButUnrelatedProfileEditsDoNot()=runTest(dispatcher) {
        val source=FakeExplore()
        val h=Harness(source)
        try {
            runCurrent();h.vm.active(true);runCurrent();h.vm.active(false)
            val session=h.sessions.currentSession()!!
            h.sessions.saveSession(kotlinx.serialization.json.Json.encodeToString(session.copy(country="Nepal",city="Kathmandu")),
                com.srisu.srisu.utils.Constants.Auth.SESSION_KEY)
            source.page=source.page.copy(results=listOf(FakeExplore.card(12)))
            h.vm.active(true);runCurrent()
            assertEquals(listOf("global","global"),source.feeds)
            assertEquals(listOf(12L),h.vm.state.value.global.cards.map {it.couple.id})
            h.vm.active(false)
            h.sessions.saveSession(kotlinx.serialization.json.Json.encodeToString(h.sessions.currentSession()!!.copy(fullName="Updated name")),
                com.srisu.srisu.utils.Constants.Auth.SESSION_KEY)
            h.vm.active(true);runCurrent()
            assertEquals(listOf("global","global"),source.feeds)
            assertEquals(1,source.checks)
        } finally {h.close()}
    }
}
