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
import com.srisu.srisu.features.moments.data.MomentHome
import com.srisu.srisu.features.moments.data.MomentResult
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

    private class GatedMoments : FakeMoments() {
        var gate: CompletableDeferred<Unit>? = null
        var nonCancellable = false
        override suspend fun home(): MomentResult<MomentHome> {
            homeCalls++
            if (nonCancellable) withContext(NonCancellable) { gate?.await() } else gate?.await()
            return homeResult
        }
    }

    @Test fun initialEntryReentryPollingAndMomentChangesRefreshSilently() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val moments = GatedMoments().apply { gate = CompletableDeferred() }
        val vm = HomeViewModel(moments, FakeChallenges(), FakePlans(), MomentFixtures.sessions()); collect(vm)
        try {
            vm.visible(true); runCurrent()
            assertEquals(1, moments.homeCalls)
            assertEquals(HomeStatus.LOADING, vm.state.value.status)
            assertFalse(vm.state.value.refreshing, "First entry keeps the initial skeleton, without a pull indicator")
            moments.gate!!.complete(Unit); runCurrent()
            assertEquals(HomeStatus.READY, vm.state.value.status)

            vm.visible(false)
            moments.gate = CompletableDeferred()
            moments.homeResult = NetworkAPIResult.Success(MomentFixtures.home.copy(user = MomentFixtures.home.user.copy(name = "Alex updated")))
            vm.visible(true); runCurrent()
            assertEquals(2, moments.homeCalls)
            assertEquals(HomeStatus.READY, vm.state.value.status)
            assertEquals("Alex", vm.state.value.name)
            assertFalse(vm.state.value.refreshing, "Returning to Home refreshes cached content silently")
            moments.gate!!.complete(Unit); runCurrent()
            assertEquals("Alex updated", vm.state.value.name)

            moments.gate = CompletableDeferred()
            advanceTimeBy(30_000); runCurrent()
            assertEquals(3, moments.homeCalls, "The background poll must still fetch")
            assertFalse(vm.state.value.refreshing)
            moments.gate!!.complete(Unit); runCurrent()

            moments.gate = CompletableDeferred()
            moments.changed(); runCurrent()
            assertEquals(4, moments.homeCalls, "Moment updates must still invalidate Home")
            assertFalse(vm.state.value.refreshing)
            moments.gate!!.complete(Unit); runCurrent()
            assertFalse(vm.state.value.refreshing)
        } finally {
            moments.gate?.complete(Unit)
            vm.visible(false); vm.viewModelScope.cancel(); backgroundScope.cancel(); runCurrent(); Dispatchers.resetMain()
        }
    }

    @Test fun featureChangesUpdateCachedSectionsWithoutPullFeedback() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var gate: CompletableDeferred<Unit>? = null
        var calls = 0
        val challenges = object : FakeChallenges() {
            override suspend fun home(): NetworkAPIResult<com.srisu.srisu.features.challenges.data.ChallengeHome?> {
                calls++; gate?.await(); return super.home()
            }
        }
        val vm = HomeViewModel(FakeMoments(), challenges, FakePlans(), MomentFixtures.sessions()); collect(vm)
        try {
            vm.visible(true); runCurrent()
            val initialCalls = calls
            gate = CompletableDeferred()
            challenges.current = com.srisu.srisu.features.challenges.ChallengeFixtures.session
            challenges.changed(); runCurrent()
            assertEquals(initialCalls + 1, calls)
            assertFalse(vm.state.value.refreshing)
            gate!!.complete(Unit); runCurrent()
            val updated = assertIs<HomeSection.Ready<HomeChallengeUi>>(vm.state.value.couple!!.challenge)
            assertEquals(challenges.current!!.id, updated.value.id)
            assertFalse(vm.state.value.refreshing)
        } finally {
            gate?.complete(Unit)
            vm.visible(false); vm.viewModelScope.cancel(); backgroundScope.cancel(); runCurrent(); Dispatchers.resetMain()
        }
    }

    @Test fun refreshDeduplicatesRequestsKeepsContentAndWaitsForSections() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var momentsGate: CompletableDeferred<Unit>? = null
        var challengeGate: CompletableDeferred<Unit>? = null
        val moments = object : FakeMoments() {
            override suspend fun home(): MomentResult<MomentHome> {
                homeCalls++
                momentsGate?.await()
                return homeResult
            }
        }
        val challenges = object : FakeChallenges() {
            override suspend fun home(): NetworkAPIResult<com.srisu.srisu.features.challenges.data.ChallengeHome?> {
                challengeGate?.await()
                return super.home()
            }
        }
        val vm = HomeViewModel(moments, challenges, FakePlans(), MomentFixtures.sessions()); collect(vm)
        try {
            vm.visible(true); runCurrent()
            assertFalse(vm.state.value.refreshing)
            val previous = assertNotNull(vm.state.value.couple)
            val calls = moments.homeCalls
            momentsGate = CompletableDeferred(); challengeGate = CompletableDeferred()
            repeat(5) { vm.refreshFromUser() }; runCurrent()
            assertEquals(calls + 1, moments.homeCalls)
            assertTrue(vm.state.value.refreshing)
            assertEquals(previous.id, vm.state.value.couple?.id)
            momentsGate!!.complete(Unit); runCurrent()
            assertTrue(vm.state.value.refreshing, "Keep feedback until dependent section requests finish")
            assertEquals(HomeStatus.READY, vm.state.value.status)
            challengeGate!!.complete(Unit); runCurrent()
            assertFalse(vm.state.value.refreshing)
        } finally {
            momentsGate?.complete(Unit); challengeGate?.complete(Unit)
            vm.visible(false); vm.viewModelScope.cancel(); backgroundScope.cancel(); runCurrent(); Dispatchers.resetMain()
        }
    }

    @Test fun userPullAdoptsInFlightAutomaticRefreshAndWaitsForItsSections() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val moments = GatedMoments()
        var sectionGate: CompletableDeferred<Unit>? = null
        val challenges = object : FakeChallenges() {
            override suspend fun home(): NetworkAPIResult<com.srisu.srisu.features.challenges.data.ChallengeHome?> {
                sectionGate?.await(); return super.home()
            }
        }
        val vm = HomeViewModel(moments, challenges, FakePlans(), MomentFixtures.sessions()); collect(vm)
        try {
            vm.visible(true); runCurrent()
            moments.gate = CompletableDeferred(); sectionGate = CompletableDeferred()
            vm.refresh(); runCurrent()
            assertEquals(2, moments.homeCalls)
            assertFalse(vm.state.value.refreshing)
            repeat(5) { vm.refreshFromUser() }; runCurrent()
            assertEquals(2, moments.homeCalls, "Pulling adopts the current read instead of duplicating it")
            assertTrue(vm.state.value.refreshing)

            vm.visible(false); runCurrent()
            assertFalse(vm.state.value.refreshing, "Leaving Home ends visible pull feedback")
            vm.visible(true); runCurrent()
            assertEquals(2, moments.homeCalls)
            assertFalse(vm.state.value.refreshing, "Re-entry must not restore a previous pull indicator")
            vm.refreshFromUser(); runCurrent()
            assertTrue(vm.state.value.refreshing)
            moments.gate!!.complete(Unit); runCurrent()
            assertTrue(vm.state.value.refreshing, "The earlier read completing must not end the new pull before its sections")
            assertEquals(HomeStatus.READY, vm.state.value.status)
            sectionGate!!.complete(Unit); runCurrent()
            assertFalse(vm.state.value.refreshing)
        } finally {
            moments.gate?.complete(Unit); sectionGate?.complete(Unit)
            vm.visible(false); vm.viewModelScope.cancel(); backgroundScope.cancel(); runCurrent(); Dispatchers.resetMain()
        }
    }

    @Test fun failedUserRefreshStopsFeedbackAndSilentRetryRetainsCachedHome() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val moments = GatedMoments()
        val vm = HomeViewModel(moments, FakeChallenges(), FakePlans(), MomentFixtures.sessions()); collect(vm)
        try {
            vm.visible(true); runCurrent()
            val couple = assertNotNull(vm.state.value.couple)
            moments.gate = CompletableDeferred(); moments.homeResult = MomentFixtures.failed()
            vm.refreshFromUser(); runCurrent(); assertTrue(vm.state.value.refreshing)
            moments.gate!!.complete(Unit); runCurrent()
            assertFalse(vm.state.value.refreshing)
            assertTrue(vm.state.value.stale)
            assertEquals(couple.id, vm.state.value.couple?.id)
            assertEquals(HomeStatus.READY, vm.state.value.status)

            moments.gate = CompletableDeferred(); moments.homeResult = NetworkAPIResult.Success(MomentFixtures.home)
            vm.refresh(); runCurrent()
            assertFalse(vm.state.value.refreshing, "A retry read is separate from the pull gesture")
            moments.gate!!.complete(Unit); runCurrent()
            assertFalse(vm.state.value.stale)

            moments.gate = CompletableDeferred(); moments.homeResult = MomentFixtures.failed(401)
            vm.refreshFromUser(); runCurrent(); assertTrue(vm.state.value.refreshing)
            moments.gate!!.complete(Unit); runCurrent()
            assertFalse(vm.state.value.refreshing)
            assertNull(vm.state.value.couple)
            assertEquals(HomeStatus.FAILED, vm.state.value.status)
        } finally {
            moments.gate?.complete(Unit)
            vm.visible(false); vm.viewModelScope.cancel(); backgroundScope.cancel(); runCurrent(); Dispatchers.resetMain()
        }
    }

    @Test fun sessionChangeStopsPullFeedbackBeforeAnUncancellableReadFinishes() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val moments = GatedMoments()
        val sessions = MomentFixtures.sessions()
        val vm = HomeViewModel(moments, FakeChallenges(), FakePlans(), sessions); collect(vm)
        try {
            vm.visible(true); runCurrent()
            moments.gate = CompletableDeferred(); moments.nonCancellable = true
            vm.refreshFromUser(); runCurrent(); assertTrue(vm.state.value.refreshing)
            sessions.clearSession(); runCurrent()
            assertFalse(vm.state.value.refreshing)
            assertNull(vm.state.value.couple)
            moments.gate!!.complete(Unit); runCurrent()
            assertFalse(vm.state.value.refreshing)
            assertNull(vm.state.value.couple, "Late data must not revive a previous account")
            vm.refreshFromUser(); runCurrent()
            assertFalse(vm.state.value.refreshing, "A stale ViewModel cannot start pull feedback")
        } finally {
            moments.gate?.complete(Unit)
            vm.visible(false); vm.viewModelScope.cancel(); backgroundScope.cancel(); runCurrent(); Dispatchers.resetMain()
        }
    }

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
            assertFalse(vm.state.value.refreshing)
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
