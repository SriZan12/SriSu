package com.srisu.srisu.features.home

import androidx.lifecycle.viewModelScope
import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.features.challenges.FakeChallenges
import com.srisu.srisu.features.chat.ChatFixtureRepository
import com.srisu.srisu.features.chat.ChatFixtures
import com.srisu.srisu.features.chat.matrix.*
import com.srisu.srisu.features.coupleprofile.data.*
import com.srisu.srisu.features.home.dashboard.state.*
import com.srisu.srisu.features.home.dashboard.vm.HomeViewModel
import com.srisu.srisu.features.moments.*
import com.srisu.srisu.features.plans.FakePlans
import com.srisu.srisu.features.plans.data.*
import com.srisu.srisu.features.sparks.FakeSparks
import com.srisu.srisu.features.sparks.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.JsonObject
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private class Ideas : DateIdeaSource {
        var fail = false
        val requests = mutableListOf<Triple<Long,String,String>>()
        val idea = DateIdea("11111111-1111-4111-8111-111111111111", 3, 1, "coffee", listOf(1), "open", 1, MomentFixtures.NOW)
        override suspend fun list(couple: Long) = NetworkAPIResult.Success<DateIdeas?>(DateIdeas(couple))
        override suspend fun get(id: String) = NetworkAPIResult.Success<DateIdea?>(idea)
        override suspend fun create(couple: Long, preset: String, requestId: String): NetworkAPIResult<DateIdea?> {
            requests += Triple(couple, preset, requestId)
            return if (fail) MomentFixtures.failed() else NetworkAPIResult.Success(idea)
        }
        override suspend fun act(id: String, revision: Long, requestId: String, action: String, values: JsonObject) = NetworkAPIResult.Success<DateIdea?>(idea)
    }
    private fun TestScope.collect(vm: HomeViewModel) = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect() }

    @Test fun uncertainIdeaRetrySurvivesRecreationAndReusesTheOriginalIntent() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val sessions = MomentFixtures.sessions()
        val ideas = Ideas().apply { fail = true }
        fun model() = HomeViewModel(FakeMoments(), FakeChallenges(), FakePlans(), sessions, ideas = ideas)
        val first = model(); collect(first)
        try {
            first.visible(true); runCurrent(); first.suggest("coffee"); runCurrent()
            assertTrue(first.state.value.pendingAction); assertEquals(1, ideas.requests.size)
            first.visible(false); first.viewModelScope.cancel()
            val second = model(); collect(second)
            try {
                second.visible(true); runCurrent()
                assertTrue(second.state.value.pendingAction)
                second.suggest("walk"); runCurrent(); assertEquals(1, ideas.requests.size)
                ideas.fail = false; second.retryAction(); runCurrent()
                assertEquals(ideas.requests[0], ideas.requests[1]); assertFalse(second.state.value.pendingAction)
            } finally { second.visible(false); second.viewModelScope.cancel() }
        } finally { first.viewModelScope.cancel(); backgroundScope.cancel(); runCurrent(); Dispatchers.resetMain() }
    }

    @Test fun unpairedNeverAssignsAQuestionAndFailedFetchDoesNotPretendUnpaired() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val moments = FakeMoments().apply { homeResult = NetworkAPIResult.Success(MomentFixtures.home.copy(couple = null)) }
        var dailyCalls = 0
        val sparks = object : FakeSparks() {
            override suspend fun daily(couple: Long, zone: String): NetworkAPIResult<SparkDaily?> { dailyCalls++; return MomentFixtures.failed() }
        }
        val vm = HomeViewModel(moments, FakeChallenges(), FakePlans(), MomentFixtures.sessions(), sparks = sparks); collect(vm)
        try {
            vm.visible(true); runCurrent(); assertNull(vm.state.value.couple); assertEquals(0, dailyCalls)
            moments.homeResult = MomentFixtures.failed(); vm.refresh(); runCurrent()
            assertTrue(vm.state.value.stale); assertEquals(HomeStatus.READY, vm.state.value.status)
        } finally { vm.visible(false); vm.viewModelScope.cancel(); backgroundScope.cancel(); runCurrent(); Dispatchers.resetMain() }
    }

    @Test fun connectionRemovalCancelsLateFeatureResultsAndClearsPrivateData() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val moments = FakeMoments()
        val answer = CompletableDeferred<Unit>()
        val sparks = object : FakeSparks() {
            override suspend fun daily(couple: Long, zone: String): NetworkAPIResult<SparkDaily?> {
                withContext(NonCancellable) { answer.await() }
                return NetworkAPIResult.Success(SparkDaily(session, "2026-11-09", "UTC"))
            }
        }
        val vm = HomeViewModel(moments, FakeChallenges(), FakePlans(), MomentFixtures.sessions(), sparks = sparks); collect(vm)
        try {
            vm.visible(true); runCurrent(); assertNotNull(vm.state.value.couple)
            moments.homeResult = NetworkAPIResult.Success(MomentFixtures.home.copy(couple = null))
            vm.refresh(); runCurrent(); answer.complete(Unit); runCurrent()
            assertNull(vm.state.value.couple); assertEquals(HomeStatus.READY, vm.state.value.status)
        } finally { answer.complete(Unit); vm.visible(false); vm.viewModelScope.cancel(); backgroundScope.cancel(); runCurrent(); Dispatchers.resetMain() }
    }

    @Test fun homeUsesOnlyTheSummaryLeaseAndNeverMarksMessagesRead() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val sessions = MomentFixtures.sessions()
        val base = ChatFixtureRepository(ChatFixtures.conversation().copy(accountId = 1, unread = true))
        var summaries = 0; var closed = 0; var opened = 0; var reads = 0
        val matrix = object : MatrixChatRepository by base {
            override fun openSummary(roomId: String, owner: MatrixChatRoomOwner) { summaries++ }
            override fun closeSummary(owner: MatrixChatRoomOwner) { closed++ }
            override fun openRoom(roomId: String, owner: MatrixChatRoomOwner) { opened++ }
            override suspend fun markRead(roomId: String, messageId: String): Result<Unit> { reads++; return Result.success(Unit) }
        }
        val resolver = object : CurrentCoupleRoomResolver { override suspend fun resolve() = NetworkAPIResult.Success<String?>(ChatFixtures.ROOM_ID) }
        val vm = HomeViewModel(FakeMoments(), FakeChallenges(), FakePlans(), sessions, matrix = matrix, resolver = resolver); collect(vm)
        try {
            vm.visible(true); runCurrent(); assertEquals(1, summaries); assertEquals(0, opened); assertEquals(0, reads)
            assertTrue(vm.state.value.couple!!.unread == true)
            vm.visible(false); runCurrent(); assertTrue(closed > 0)
            sessions.clearSession(); runCurrent(); assertNull(vm.state.value.couple)
        } finally { vm.viewModelScope.cancel(); backgroundScope.cancel(); runCurrent(); Dispatchers.resetMain() }
    }

    @Test fun networkFailureRetainsQuestionButRemovesFreshnessAndAuthFailureClearsEverything() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var failure: Int? = null; var offline = false
        val sparks = object : FakeSparks() {
            override suspend fun daily(couple: Long, zone: String): NetworkAPIResult<SparkDaily?> =
                if (offline || failure != null) MomentFixtures.failed(failure) else NetworkAPIResult.Success(SparkDaily(session, "2026-11-09", "UTC"))
        }
        val vm = HomeViewModel(FakeMoments(), FakeChallenges(), FakePlans(), MomentFixtures.sessions(), sparks = sparks); collect(vm)
        try {
            vm.visible(true); runCurrent(); assertTrue(vm.state.value.couple!!.questionFresh)
            offline = true; vm.refresh(); runCurrent()
            assertIs<HomeSection.Ready<SparkDaily>>(vm.state.value.couple!!.question); assertFalse(vm.state.value.couple!!.questionFresh)
            failure = 404; vm.refresh(); runCurrent(); assertNull(vm.state.value.couple)
            assertEquals(HomeStatus.FAILED, vm.state.value.status)
        } finally { vm.visible(false); vm.viewModelScope.cancel(); backgroundScope.cancel(); runCurrent(); Dispatchers.resetMain() }
    }
}
