@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.srisu.srisu.features.moments

import androidx.lifecycle.viewModelScope
import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.features.moments.data.*
import com.srisu.srisu.features.moments.presentation.MomentViewerViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

class MomentSelectedSequenceTest {
    private val dispatcher=StandardTestDispatcher()
    @BeforeTest fun before() {Dispatchers.setMain(dispatcher)}
    @AfterTest fun after() {Dispatchers.resetMain()}
    private fun publicMoment(id:Long)=MomentFixtures.partner.copy(id=id,visibility="public",canReply=false)

    private fun paginatedSource()=object:FakeMoments() {
        override suspend fun get(id:Long):MomentResult<Moment> = NetworkAPIResult.Success(publicMoment(id))
        override suspend fun sequence(couple:Long,creator:Long?,cursor:String?):MomentResult<MomentPage> =
            NetworkAPIResult.Success(if(cursor==null) MomentPage(listOf(publicMoment(7)),"https://example.invalid/?cursor=next")
                else MomentPage(listOf(publicMoment(8))))
    }

    @Test fun paginationDoesNotShowAppreciationSentOnAnotherMoment()=runTest(dispatcher) {
        val vm=MomentViewerViewModel(paginatedSource(),MomentFixtures.sessions())
        try {
            vm.open(7,3,null);vm.active(true);runCurrent()
            vm.note("Such a lovely morning");vm.sendNote();runCurrent()
            assertTrue(vm.state.value.noteSent)
            vm.move(1);runCurrent()
            assertEquals(8L,vm.state.value.moment?.id)
            assertFalse(vm.state.value.noteSent)
            assertEquals("",vm.state.value.note)
        } finally {vm.viewModelScope.cancel()}
    }

    @Test fun draftsAndNoteReplyStateBelongOnlyToTheirMomentAcrossBothTransitions()=runTest(dispatcher) {
        val vm=MomentViewerViewModel(paginatedSource(),MomentFixtures.sessions())
        try {
            vm.open(7,3,null);vm.active(true);runCurrent()
            for((direction,expectedId) in listOf(1 to 8L,-1 to 7L)) {
                vm.note("An unsent appreciation");vm.editCaption("An unsaved caption")
                vm.replyToNote(12,"An unsent reply")
                vm.move(direction);runCurrent()
                assertEquals(expectedId,vm.state.value.moment?.id)
                assertEquals("",vm.state.value.note)
                assertNull(vm.state.value.captionDraft)
                assertNull(vm.state.value.noteReplyId)
                assertEquals("",vm.state.value.noteReplyDraft)
                assertNull(vm.state.value.notes)
                assertFalse(vm.state.value.notesLoading)
                assertFalse(vm.state.value.notesNext)
            }
        } finally {vm.viewModelScope.cancel()}
    }

    @Test fun selectedLaterPageStaysSelectedAndCanContinueInBothDirectionsWithoutPrefetchViews()=runTest(dispatcher) {
        val pages=mutableListOf<String?>()
        val source=object:FakeMoments() {
            override suspend fun get(id:Long):MomentResult<Moment> = NetworkAPIResult.Success(publicMoment(id))
            override suspend fun sequence(couple:Long,creator:Long?,cursor:String?):MomentResult<MomentPage> {
                pages+=cursor
                return NetworkAPIResult.Success(if(cursor==null) MomentPage(listOf(publicMoment(7)),"https://example.invalid/?cursor=next")
                    else MomentPage(listOf(publicMoment(8),publicMoment(9))))
            }
        }
        val vm=MomentViewerViewModel(source,MomentFixtures.sessions())
        try {
            vm.open(8,3,null);vm.active(true);runCurrent()
            assertEquals(listOf<String?>(null,"next"),pages);assertEquals(8L,vm.state.value.moment?.id)
            assertTrue(vm.state.value.hasPrevious);assertTrue(vm.state.value.hasNext);assertEquals(0,source.views)
            vm.move(1);runCurrent();assertEquals(9L,vm.state.value.moment?.id)
            vm.move(-1);runCurrent();assertEquals(8L,vm.state.value.moment?.id)
            vm.move(-1);runCurrent();assertEquals(7L,vm.state.value.moment?.id)
        } finally {vm.viewModelScope.cancel()}
    }

    @Test fun missingSequenceAnchorNeverJumpsToAnUnrelatedFirstMoment()=runTest(dispatcher) {
        val source=object:FakeMoments() {
            override suspend fun get(id:Long):MomentResult<Moment> = NetworkAPIResult.Success(publicMoment(id))
            override suspend fun sequence(couple:Long,creator:Long?,cursor:String?):MomentResult<MomentPage> =
                NetworkAPIResult.Success(MomentPage(listOf(publicMoment(7))))
        }
        val vm=MomentViewerViewModel(source,MomentFixtures.sessions())
        try {
            vm.open(8,3,null);vm.active(true);runCurrent();vm.move(1);runCurrent()
            assertEquals(8L,vm.state.value.moment?.id);assertFalse(vm.state.value.hasNext);assertFalse(vm.state.value.hasPrevious)
        } finally {vm.viewModelScope.cancel()}
    }

    @Test fun unchangedPublicAuthorizationPreservesPixelsAndRevocationClearsEverything()=runTest(dispatcher) {
        var gets=0;var images=0;var revoked=false
        val source=object:FakeMoments() {
            override suspend fun get(id:Long):MomentResult<Moment> {gets++;return if(revoked) MomentFixtures.failed(404) else NetworkAPIResult.Success(publicMoment(id))}
            override suspend fun image(url:String):ByteArray? {images++;return MomentFixtures.image}
        }
        val vm=MomentViewerViewModel(source,MomentFixtures.sessions())
        try {
            vm.open(8,3,null);vm.active(true);runCurrent();val bytes=vm.state.value.bytes
            assertNotNull(bytes);advanceTimeBy(30_000);runCurrent()
            assertEquals(2,gets);assertEquals(1,images);assertSame(bytes,vm.state.value.bytes);assertEquals(0,source.views)
            revoked=true;advanceTimeBy(30_000);runCurrent()
            assertNull(vm.state.value.bytes);assertNull(vm.state.value.playback);assertNull(vm.state.value.notes)
            assertFalse(vm.state.value.fresh);assertEquals("unavailable",vm.state.value.unavailable)
        } finally {vm.viewModelScope.cancel()}
    }

    @Test fun skippingAStillLoadingPhotoCannotDisablePublicAccessRevalidation()=runTest(dispatcher) {
        var revoked=false
        var firstImageCancelled=false
        val first=MomentFixtures.photo
        val second=first.copy(id=22,image="https://api.example.invalid/second-photo",order=1)
        val moment=publicMoment(8).copy(photos=listOf(first,second))
        val source=object:FakeMoments() {
            override suspend fun get(id:Long):MomentResult<Moment> =
                if(revoked) MomentFixtures.failed(404) else NetworkAPIResult.Success(moment)
            override suspend fun image(url:String):ByteArray? {
                if(url==first.image) try {awaitCancellation()} finally {firstImageCancelled=true}
                return MomentFixtures.image
            }
        }
        val vm=MomentViewerViewModel(source,MomentFixtures.sessions())
        try {
            vm.open(8,3,null);vm.active(true);runCurrent()
            assertTrue(vm.state.value.imageLoading)
            vm.move(1);runCurrent()
            assertTrue(firstImageCancelled);assertEquals(1,vm.state.value.photo)
            assertNotNull(vm.state.value.bytes)
            revoked=true;advanceTimeBy(30_000);runCurrent()
            assertEquals("unavailable",vm.state.value.unavailable)
            assertNull(vm.state.value.bytes);assertFalse(vm.state.value.fresh)
        } finally {vm.viewModelScope.cancel()}
    }

    @Test fun publicAuthorizationNetworkFailureHidesCachedMediaAndBackgroundStopsPolling()=runTest(dispatcher) {
        var gets=0;var fail=false
        val source=object:FakeMoments() {
            override suspend fun get(id:Long):MomentResult<Moment> {gets++;return if(fail) MomentFixtures.failed() else NetworkAPIResult.Success(publicMoment(id))}
        }
        val vm=MomentViewerViewModel(source,MomentFixtures.sessions())
        try {
            vm.open(8,3,null);vm.active(true);runCurrent();vm.active(false)
            advanceTimeBy(30_000);runCurrent();assertEquals(1,gets)
            vm.active(true);runCurrent();fail=true;advanceTimeBy(30_000);runCurrent()
            assertEquals(3,gets);assertNull(vm.state.value.bytes);assertFalse(vm.state.value.fresh);assertNotNull(vm.state.value.error)
        } finally {vm.viewModelScope.cancel()}
    }
}
