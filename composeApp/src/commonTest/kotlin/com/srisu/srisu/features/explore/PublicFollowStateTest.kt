@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.srisu.srisu.features.explore

import androidx.lifecycle.ViewModelStore
import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.features.explore.data.*
import com.srisu.srisu.features.explore.presentation.PublicFollowViewModel
import com.srisu.srisu.features.moments.MomentFixtures
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

class PublicFollowStateTest {
    private val dispatcher=StandardTestDispatcher()
    @BeforeTest fun before() {Dispatchers.setMain(dispatcher)}
    @AfterTest fun after() {Dispatchers.resetMain()}

    @Test fun profileOnlyLoadsTargetStatusAndRejectsOldProfileResponse()=runTest(dispatcher) {
        val gate=CompletableDeferred<Unit>()
        val source=object:FakeExplore() {
            override suspend fun followState(id:Long):ExploreResult<PublicFollowSnapshot> {
                if(id==10L) withContext(NonCancellable) {gate.await()}
                return super.followState(id)
            }
        }
        val vm=PublicFollowViewModel(source,MomentFixtures.sessions())
        val owner=ViewModelStore().apply {put("follow",vm)}
        try {
            runCurrent();vm.enter(10);vm.active(true);runCurrent()
            vm.enter(11);runCurrent();gate.complete(Unit);runCurrent()
            assertEquals(11L,vm.state.value.snapshot?.coupleId)
            assertTrue(source.feeds.isEmpty())
            vm.active(false);assertNull(vm.state.value.snapshot)
            vm.active(true);runCurrent();assertEquals(11L,vm.state.value.snapshot?.coupleId)
        } finally {owner.clear()}
    }

    @Test fun publicFollowConflictRequiresRetryAndDoesNotChangeFaves()=runTest(dispatcher) {
        var fail=true;var writes=0
        val source=object:FakeExplore() {
            override suspend fun setFollow(id:Long,desired:Boolean,version:Long,shareIdentity:Boolean):ExploreResult<PublicFollowSnapshot> {
                writes++
                return if(fail) MomentFixtures.failed(409) else super.setFollow(id,desired,version,shareIdentity)
            }
        }
        val vm=PublicFollowViewModel(source,MomentFixtures.sessions())
        val owner=ViewModelStore().apply {put("follow",vm)}
        try {
            runCurrent();vm.enter(10);vm.active(true);runCurrent()
            vm.follow(true);runCurrent();assertEquals(0,writes)
            vm.follow(true,true);runCurrent();assertEquals(1,writes)
            assertNotNull(vm.state.value.error);assertFalse(vm.state.value.saving)
            fail=false;vm.follow(true,true);runCurrent();assertEquals(true,vm.state.value.snapshot?.isFollowing)
            assertTrue(source.savedIds.isEmpty());assertTrue(source.writes.isEmpty())
        } finally {owner.clear()}
    }

    @Test fun accountChangeRejectsLatePublicFollowWriteResponse()=runTest(dispatcher) {
        val gate=CompletableDeferred<Unit>()
        val source=object:FakeExplore() {
            override suspend fun setFollow(id:Long,desired:Boolean,version:Long,shareIdentity:Boolean):ExploreResult<PublicFollowSnapshot> =
                withContext(NonCancellable) {gate.await();super.setFollow(id,desired,version,shareIdentity)}
        }
        val sessions=MomentFixtures.sessions();val vm=PublicFollowViewModel(source,sessions)
        val owner=ViewModelStore().apply {put("follow",vm)}
        try {
            runCurrent();vm.enter(10);vm.active(true);runCurrent();vm.follow(true,true);runCurrent()
            sessions.clearSession();runCurrent();gate.complete(Unit);runCurrent()
            assertNull(vm.state.value.snapshot);assertFalse(vm.state.value.saving)
        } finally {owner.clear()}
    }
}
