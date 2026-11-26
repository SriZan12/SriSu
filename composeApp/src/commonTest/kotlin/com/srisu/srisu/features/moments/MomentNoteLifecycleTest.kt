@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.srisu.srisu.features.moments

import androidx.lifecycle.viewModelScope
import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.features.moments.data.*
import com.srisu.srisu.features.moments.presentation.MomentViewerViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

class MomentNoteLifecycleTest {
    private val dispatcher=StandardTestDispatcher()
    @BeforeTest fun before() {Dispatchers.setMain(dispatcher)}
    @AfterTest fun after() {Dispatchers.resetMain()}

    @Test fun dismissingPendingNotesCannotReopenTheSheetOrOverwriteAReopenedList()=runTest(dispatcher) {
        val pending=CompletableDeferred<Unit>();var requests=0
        val source=object:FakeMoments() {
            override suspend fun notes(id:Long,page:Int):MomentResult<MomentNotePage> {
                val request=++requests
                if(request==1) withContext(NonCancellable) {pending.await()}
                return NetworkAPIResult.Success(MomentNotePage(listOf(MomentNote(request.toLong(),"Note $request"))))
            }
        }
        val vm=MomentViewerViewModel(source,MomentFixtures.sessions())
        try {
            vm.open(8,3,null);vm.active(true);runCurrent();vm.loadNotes();runCurrent()
            assertTrue(vm.state.value.notesLoading)
            vm.closeNotes();assertNull(vm.state.value.notes);assertFalse(vm.state.value.notesLoading)
            vm.loadNotes();runCurrent()
            assertEquals(listOf(2L),vm.state.value.notes?.map {it.id})
            pending.complete(Unit);runCurrent()
            assertEquals(listOf(2L),vm.state.value.notes?.map {it.id})
            vm.closeNotes();assertNull(vm.state.value.notes)
        } finally {pending.complete(Unit);vm.viewModelScope.cancel()}
    }

    @Test fun refreshCancelsPendingNotesAndAllowsANewLoad()=runTest(dispatcher) {
        val pending=CompletableDeferred<Unit>();var requests=0
        val source=object:FakeMoments() {
            override suspend fun notes(id:Long,page:Int):MomentResult<MomentNotePage> {
                val request=++requests
                if(request==1) withContext(NonCancellable) {pending.await()}
                return NetworkAPIResult.Success(MomentNotePage(listOf(MomentNote(request.toLong(),"Note $request"))))
            }
        }
        val vm=MomentViewerViewModel(source,MomentFixtures.sessions())
        try {
            vm.open(8,3,null);vm.active(true);runCurrent();vm.loadNotes();runCurrent()
            vm.refresh();runCurrent()
            assertTrue(vm.state.value.fresh);assertFalse(vm.state.value.notesLoading);assertNull(vm.state.value.notes)
            vm.loadNotes();runCurrent();assertEquals(2,requests)
            pending.complete(Unit);runCurrent()
            assertEquals(listOf(2L),vm.state.value.notes?.map {it.id})
        } finally {pending.complete(Unit);vm.viewModelScope.cancel()}
    }

    @Test fun dismissedReplyMayCompleteWithoutReopeningNotes()=runTest(dispatcher) {
        val pending=CompletableDeferred<Unit>();var reads=0
        val source=object:FakeMoments() {
            override suspend fun notes(id:Long,page:Int):MomentResult<MomentNotePage> {
                reads++;return NetworkAPIResult.Success(MomentNotePage(listOf(MomentNote(1,"A lovely Moment"))))
            }
            override suspend fun noteReply(id:Long,text:String):MomentResult<MomentNoteReply> {
                pending.await();return NetworkAPIResult.Success(MomentNoteReply(2,text))
            }
        }
        val vm=MomentViewerViewModel(source,MomentFixtures.sessions())
        try {
            vm.open(8,3,null);vm.active(true);runCurrent();vm.loadNotes();runCurrent()
            vm.replyToNote(1,"Thank you");vm.sendNoteReply();runCurrent()
            assertTrue(vm.state.value.busy)
            vm.closeNotes();pending.complete(Unit);runCurrent()
            assertNull(vm.state.value.notes);assertFalse(vm.state.value.busy);assertEquals(1,reads)
        } finally {pending.complete(Unit);vm.viewModelScope.cancel()}
    }

    @Test fun failedContinuationRetainsNotesAndRetriesTheSamePage()=runTest(dispatcher) {
        var fail=true;val requests=mutableListOf<Int>()
        val source=object:FakeMoments() {
            override suspend fun notes(id:Long,page:Int):MomentResult<MomentNotePage> {
                requests+=page
                return when {
                    page==1->NetworkAPIResult.Success(MomentNotePage(listOf(MomentNote(1,"First")),"page=2"))
                    fail->MomentFixtures.failed()
                    else->NetworkAPIResult.Success(MomentNotePage(listOf(MomentNote(1,"First"),MomentNote(2,"Second"))))
                }
            }
        }
        val vm=MomentViewerViewModel(source,MomentFixtures.sessions())
        try {
            vm.open(8,3,null);vm.active(true);runCurrent();vm.loadNotes();runCurrent()
            vm.loadNotes(true);runCurrent();assertEquals(listOf(1L),vm.state.value.notes?.map {it.id})
            assertFalse(vm.state.value.notesLoading);assertNotNull(vm.state.value.error)
            assertTrue(vm.state.value.notesNext,"A transient error must keep the More notes action available")
            fail=false;vm.loadNotes(true);runCurrent()
            assertEquals(listOf(1,2,2),requests);assertEquals(listOf(1L,2L),vm.state.value.notes?.map {it.id})
        } finally {vm.viewModelScope.cancel()}
    }

    @Test fun accountChangeDropsLateNoteRows()=runTest(dispatcher) {
        val pending=CompletableDeferred<Unit>()
        val source=object:FakeMoments() {
            override suspend fun notes(id:Long,page:Int):MomentResult<MomentNotePage> = withContext(NonCancellable) {
                pending.await();NetworkAPIResult.Success(MomentNotePage(listOf(MomentNote(1,"Private note"))))
            }
        }
        val sessions=MomentFixtures.sessions();val vm=MomentViewerViewModel(source,sessions)
        try {
            vm.open(8,3,null);vm.active(true);runCurrent();vm.loadNotes();runCurrent()
            sessions.clearSession();runCurrent();pending.complete(Unit);runCurrent()
            assertNull(vm.state.value.notes);assertFalse(vm.state.value.notesLoading);assertFalse(vm.state.value.fresh)
        } finally {pending.complete(Unit);vm.viewModelScope.cancel()}
    }

    @Test fun appreciationPermissionDenialImmediatelyHidesProtectedContent()=runTest(dispatcher) {
        for(status in listOf(401,403,404,410)) {
            val source=object:FakeMoments() {
                init {moment=NetworkAPIResult.Success(MomentFixtures.partner.copy(visibility="public",canReply=false))}
                override suspend fun note(id:Long,text:String):MomentResult<MomentNote> = MomentFixtures.failed(status)
            }
            val vm=MomentViewerViewModel(source,MomentFixtures.sessions())
            try {
                vm.open(8,3,null);vm.active(true);runCurrent();assertNotNull(vm.state.value.bytes)
                vm.note("Lovely morning");vm.sendNote();runCurrent()
                assertFalse(vm.state.value.fresh,"Status $status must invalidate known access")
                assertNull(vm.state.value.moment);assertNull(vm.state.value.bytes);assertNull(vm.state.value.playback)
                assertNull(vm.state.value.notes);assertFalse(vm.state.value.busy);assertFalse(vm.state.value.noteSent)
            } finally {vm.viewModelScope.cancel()}
        }
    }

    @Test fun duplicateSendIsBoundedAndTransientFailurePreservesDraftForRetry()=runTest(dispatcher) {
        val pending=CompletableDeferred<Unit>();var sends=0;var fail=true
        val source=object:FakeMoments() {
            init {moment=NetworkAPIResult.Success(MomentFixtures.partner.copy(visibility="public",canReply=false))}
            override suspend fun note(id:Long,text:String):MomentResult<MomentNote> {
                sends++;pending.await()
                return if(fail) MomentFixtures.failed(503) else NetworkAPIResult.Success(MomentNote(1,text))
            }
        }
        val vm=MomentViewerViewModel(source,MomentFixtures.sessions())
        try {
            vm.open(8,3,null);vm.active(true);runCurrent();vm.note("Lovely morning")
            vm.sendNote();vm.sendNote();runCurrent();assertEquals(1,sends)
            pending.complete(Unit);runCurrent()
            assertTrue(vm.state.value.fresh);assertEquals("Lovely morning",vm.state.value.note)
            assertFalse(vm.state.value.busy);assertNotNull(vm.state.value.error)
            fail=false;vm.sendNote();runCurrent()
            assertEquals(2,sends);assertTrue(vm.state.value.noteSent);assertEquals("",vm.state.value.note)
        } finally {pending.complete(Unit);vm.viewModelScope.cancel()}
    }

    @Test fun latePhotoCannotRepopulateBytesAfterAppreciationAccessDenial()=runTest(dispatcher) {
        val pending=CompletableDeferred<Unit>()
        val source=object:FakeMoments() {
            init {moment=NetworkAPIResult.Success(MomentFixtures.partner.copy(visibility="public",canReply=false))}
            override suspend fun image(url:String):ByteArray? = withContext(NonCancellable) {pending.await();MomentFixtures.image}
            override suspend fun note(id:Long,text:String):MomentResult<MomentNote> = MomentFixtures.failed(404)
        }
        val vm=MomentViewerViewModel(source,MomentFixtures.sessions())
        try {
            vm.open(8,3,null);vm.active(true);runCurrent();assertTrue(vm.state.value.imageLoading)
            vm.note("Lovely morning");vm.sendNote();runCurrent()
            pending.complete(Unit);runCurrent()
            assertFalse(vm.state.value.fresh);assertNull(vm.state.value.moment);assertNull(vm.state.value.bytes)
            assertFalse(vm.state.value.imageLoading)
        } finally {pending.complete(Unit);vm.viewModelScope.cancel()}
    }

    @Test fun deniedReplyReauthorizesParentInsteadOfAssumingTheMomentWasDeleted()=runTest(dispatcher) {
        for(status in listOf(403,404,410)) for(parentAllowed in listOf(true,false)) {
            val pending=CompletableDeferred<Unit>()
            val source=object:FakeMoments() {
                override suspend fun get(id:Long):MomentResult<Moment> {
                    if(++gets>1) {
                        pending.await()
                        if(!parentAllowed) return MomentFixtures.failed(404)
                    }
                    return moment
                }
                override suspend fun notes(id:Long,page:Int):MomentResult<MomentNotePage> = NetworkAPIResult.Success(
                    MomentNotePage(listOf(MomentNote(1,"Private appreciation"))))
                override suspend fun noteReply(id:Long,text:String):MomentResult<MomentNoteReply> = MomentFixtures.failed(status)
            }
            val vm=MomentViewerViewModel(source,MomentFixtures.sessions())
            try {
                vm.open(8,3,null);vm.active(true);runCurrent();vm.loadNotes();runCurrent()
                vm.replyToNote(1,"Thank you");vm.sendNoteReply();runCurrent()
                assertEquals(2,source.gets);assertNull(vm.state.value.notes)
                assertNull(vm.state.value.noteReplyId);assertEquals("",vm.state.value.noteReplyDraft)
                assertFalse(vm.state.value.fresh);assertNull(vm.state.value.bytes)
                assertFalse(vm.state.value.busy);assertTrue(vm.state.value.loading)
                pending.complete(Unit);runCurrent()
                assertEquals(parentAllowed,vm.state.value.fresh)
                if(parentAllowed) {
                    assertEquals(8L,vm.state.value.moment?.id);assertNull(vm.state.value.unavailable)
                } else {
                    assertNull(vm.state.value.moment);assertNull(vm.state.value.bytes)
                    assertEquals("unavailable",vm.state.value.unavailable)
                }
                assertNull(vm.state.value.notes)
            } finally {pending.complete(Unit);vm.viewModelScope.cancel()}
        }
    }

    @Test fun missingContinuationPageRechecksMomentWithoutFalselyExpiringIt()=runTest(dispatcher) {
        val source=object:FakeMoments() {
            override suspend fun notes(id:Long,page:Int):MomentResult<MomentNotePage> = if(page==1)
                NetworkAPIResult.Success(MomentNotePage(listOf(MomentNote(1,"Now deleted")),"page=2"))
                else MomentFixtures.failed(404)
        }
        val vm=MomentViewerViewModel(source,MomentFixtures.sessions())
        try {
            vm.open(8,3,null);vm.active(true);runCurrent();vm.loadNotes();runCurrent()
            vm.loadNotes(true);runCurrent()
            assertEquals(2,source.gets);assertTrue(vm.state.value.fresh)
            assertEquals(8L,vm.state.value.moment?.id);assertNull(vm.state.value.unavailable)
            assertNull(vm.state.value.notes);assertFalse(vm.state.value.notesNext)
            assertFalse(vm.state.value.notesLoading)
        } finally {vm.viewModelScope.cancel()}
    }

    @Test fun unauthorizedNoteRequestsImmediatelyHideWithoutRetryingParent()=runTest(dispatcher) {
        for(reply in listOf(true,false)) {
            val source=object:FakeMoments() {
                override suspend fun notes(id:Long,page:Int):MomentResult<MomentNotePage> = if(reply)
                    NetworkAPIResult.Success(MomentNotePage(listOf(MomentNote(1,"Private appreciation"))))
                    else MomentFixtures.failed(401)
                override suspend fun noteReply(id:Long,text:String):MomentResult<MomentNoteReply> = MomentFixtures.failed(401)
            }
            val vm=MomentViewerViewModel(source,MomentFixtures.sessions())
            try {
                vm.open(8,3,null);vm.active(true);runCurrent();vm.loadNotes();runCurrent()
                if(reply) {vm.replyToNote(1,"Thank you");vm.sendNoteReply();runCurrent()}
                assertEquals(1,source.gets);assertFalse(vm.state.value.fresh)
                assertNull(vm.state.value.notes);assertNull(vm.state.value.moment);assertNull(vm.state.value.bytes)
                assertFalse(vm.state.value.busy);assertFalse(vm.state.value.notesLoading)
                assertEquals("unavailable",vm.state.value.unavailable)
            } finally {vm.viewModelScope.cancel()}
        }
    }

    @Test fun emptyNoteListDoesNotInvalidateAnAuthorizedMoment()=runTest(dispatcher) {
        val source=FakeMoments();val vm=MomentViewerViewModel(source,MomentFixtures.sessions())
        try {
            vm.open(8,3,null);vm.active(true);runCurrent();vm.loadNotes();runCurrent()
            assertEquals(1,source.gets);assertTrue(vm.state.value.fresh)
            assertEquals(8L,vm.state.value.moment?.id);assertEquals(emptyList(),vm.state.value.notes)
            assertFalse(vm.state.value.notesLoading);assertFalse(vm.state.value.notesNext)
        } finally {vm.viewModelScope.cancel()}
    }

    @Test fun transientReplyFailureKeepsPrivateNotesAndDraftForRetry()=runTest(dispatcher) {
        var fail=true
        val source=object:FakeMoments() {
            override suspend fun notes(id:Long,page:Int):MomentResult<MomentNotePage> = NetworkAPIResult.Success(
                MomentNotePage(listOf(MomentNote(1,"Private appreciation"))))
            override suspend fun noteReply(id:Long,text:String):MomentResult<MomentNoteReply> = if(fail)
                MomentFixtures.failed(503) else NetworkAPIResult.Success(MomentNoteReply(2,text))
        }
        val vm=MomentViewerViewModel(source,MomentFixtures.sessions())
        try {
            vm.open(8,3,null);vm.active(true);runCurrent();vm.loadNotes();runCurrent()
            vm.replyToNote(1,"Thank you");vm.sendNoteReply();runCurrent()
            assertEquals(1,source.gets);assertTrue(vm.state.value.fresh)
            assertEquals(listOf(1L),vm.state.value.notes?.map {it.id});assertEquals("Thank you",vm.state.value.noteReplyDraft)
            assertEquals(1L,vm.state.value.noteReplyId);assertFalse(vm.state.value.busy)
            fail=false;vm.sendNoteReply();runCurrent()
            assertEquals("",vm.state.value.noteReplyDraft);assertNull(vm.state.value.noteReplyId)
            assertTrue(vm.state.value.fresh);assertEquals(1,source.gets)
        } finally {vm.viewModelScope.cancel()}
    }
}
