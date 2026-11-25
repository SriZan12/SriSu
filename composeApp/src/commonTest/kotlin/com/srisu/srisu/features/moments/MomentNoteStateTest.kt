package com.srisu.srisu.features.moments

import androidx.lifecycle.viewModelScope
import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.features.moments.data.*
import com.srisu.srisu.features.moments.presentation.MomentNoteViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class MomentNoteStateTest {
    @Test fun expiredMomentIsNotFetchedAndReplyPermissionIsAuthoritative()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var replies=0
        var note=MomentNote(7,"Synthetic appreciation",canReply=false)
        val source=object:FakeMoments() {
            override suspend fun noteDetail(id:Long):MomentResult<MomentNote> = NetworkAPIResult.Success(note)
            override suspend fun noteReply(id:Long,text:String):MomentResult<MomentNoteReply> {
                replies++;note=note.copy(replies=listOf(MomentNoteReply(1,text)));return NetworkAPIResult.Success(note.replies.first())
            }
        }
        val sessions=MomentFixtures.sessions(); val vm=MomentNoteViewModel(source,sessions)
        try {
            vm.open(7);vm.active(true);runCurrent()
            assertEquals(0,source.gets);assertEquals(7,vm.state.value.note!!.id)
            vm.draft("Hello");vm.reply();runCurrent();assertEquals(0,replies)
            note=note.copy(canReply=true);vm.refresh();runCurrent()
            vm.reply();runCurrent();assertEquals(1,replies);assertEquals("",vm.state.value.draft)
            assertEquals("Hello",vm.state.value.note!!.replies.single().message)
            vm.active(false);assertNull(vm.state.value.note)
            vm.active(true);runCurrent();sessions.clearSession();runCurrent()
            assertNull(vm.state.value.note);assertTrue(vm.state.value.unavailable)
        } finally {vm.viewModelScope.cancel();Dispatchers.resetMain()}
    }
    @Test fun lateOrDeniedResponsesCannotRestorePrivateThread()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val gate=CompletableDeferred<Unit>(); var fail=false
        val source=object:FakeMoments() {
            override suspend fun noteDetail(id:Long):MomentResult<MomentNote> {
                gate.await();return if(fail) MomentFixtures.failed(404) else NetworkAPIResult.Success(MomentNote(7,"Synthetic"))
            }
        }
        val vm=MomentNoteViewModel(source,MomentFixtures.sessions())
        try {
            vm.open(7);vm.active(true);runCurrent();vm.active(false);gate.complete(Unit);runCurrent()
            assertNull(vm.state.value.note)
            fail=true;vm.active(true);runCurrent();assertTrue(vm.state.value.unavailable)
            assertNull(vm.state.value.error)
        } finally {vm.viewModelScope.cancel();Dispatchers.resetMain()}
    }
}
