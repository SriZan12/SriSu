package com.srisu.srisu.features.challenges

import androidx.lifecycle.viewModelScope
import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.features.challenges.data.*
import com.srisu.srisu.features.challenges.presentation.*
import com.srisu.srisu.features.chat.matrix.MatrixChatMessage
import com.srisu.srisu.features.moments.MomentFixtures
import com.srisu.srisu.features.sparks.FakeSparkMatrix
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ChallengeStateTest {
    private val environment = ApiEnvironment("https://api.example.invalid/")
    @BeforeTest fun before() { Dispatchers.setMain(StandardTestDispatcher()) }
    @AfterTest fun after() { Dispatchers.resetMain() }
    @Test fun lostAcknowledgementAndRestartReuseExactOperation() = runTest {
        val sessions = MomentFixtures.sessions(); val source = FakeChallenges().apply { failNetwork = true }
        val vm = ChallengeViewModel(source, FakeSparkMatrix(), sessions, environment)
        vm.open(ChallengeFixtures.session.id); runCurrent(); vm.action("accept"); runCurrent()
        val intent = vm.state.value.pending
        assertNotNull(intent); assertFalse(vm.state.value.canAct)
        vm.viewModelScope.cancel()
        val restarted = ChallengeViewModel(source, FakeSparkMatrix(), sessions, environment)
        restarted.open(ChallengeFixtures.session.id); runCurrent()
        assertEquals(intent, restarted.state.value.pending)
        source.failNetwork = false; restarted.retry(); runCurrent()
        assertEquals(source.requests.first(), source.requests.last())
        assertEquals("active", restarted.state.value.session?.state); assertNull(restarted.state.value.pending)
        restarted.viewModelScope.cancel()
    }
    @Test fun invitationRetryKeepsOriginalTemplateAndId() = runTest {
        val source = FakeChallenges().apply { failNetwork = true }
        val vm = ChallengeViewModel(source, FakeSparkMatrix(), MomentFixtures.sessions(), environment)
        vm.open(null); runCurrent(); vm.select(ChallengeFixtures.activity); vm.invite(); runCurrent()
        vm.select(ChallengeFixtures.collections.first().activities.first())
        vm.retry(); runCurrent()
        assertEquals(2, source.requests.size); assertEquals(source.requests[0], source.requests[1])
        vm.viewModelScope.cancel()
    }
    @Test fun revisionConflictRefreshesAndClearsRejectedIntent() = runTest {
        val source = FakeChallenges().apply { failure = 409 }
        val vm = ChallengeViewModel(source, FakeSparkMatrix(), MomentFixtures.sessions(), environment)
        vm.open(ChallengeFixtures.session.id); runCurrent()
        source.session = source.session.copy(state = "cancelled", revision = 2)
        vm.action("accept"); runCurrent()
        assertNull(vm.state.value.pending); assertEquals("cancelled", vm.state.value.session?.state)
        assertNotNull(vm.state.value.error); vm.viewModelScope.cancel()
    }
    @Test fun accountSwitchAndDeniedAccessClearPrivateState() = runTest {
        val sessions = MomentFixtures.sessions(); val source = FakeChallenges()
        val vm = ChallengeViewModel(source, FakeSparkMatrix(), sessions, environment)
        vm.open(ChallengeFixtures.session.id); runCurrent(); source.failGet = true; vm.refresh(); runCurrent()
        assertNull(vm.state.value.session); assertNull(vm.state.value.home)
        sessions.clearSession(); runCurrent(); vm.action("accept"); runCurrent()
        assertTrue(source.requests.isEmpty()); vm.viewModelScope.cancel()
    }
    @Test fun filtersAndHistoryPaginationPreserveEarlierRows() = runTest {
        val source = FakeChallenges(); val c = source.session.copy(state = "completed")
        source.historyPage = ChallengeHistory(listOf(c), "page2")
        val vm = ChallengeViewModel(source, FakeSparkMatrix(), MomentFixtures.sessions(), environment)
        vm.open(null); runCurrent(); vm.collection("new"); vm.filter(true,true)
        assertTrue(vm.state.value.activities.all { it.mode != "together" && it.minutes <= 15 })
        vm.browse(true); runCurrent()
        source.historyPage = ChallengeHistory(listOf(c.copy(id="next")))
        vm.moreHistory(); runCurrent(); assertEquals(2, vm.state.value.history.items.size)
        vm.refresh(quiet=true); runCurrent(); assertEquals(2, vm.state.value.history.items.size)
        vm.viewModelScope.cancel()
    }
    @Test fun eachPartnerOwnsTheirPauseAndCompletion() {
        val c = ChallengeFixtures.session
        assertFalse(c.allows("accept",2)); assertTrue(c.allows("accept",1)); assertFalse(c.allows("confirm",1))
        assertFalse(c.allows("cancel",1)); assertTrue(c.allows("cancel",2))
        val paused = c.copy(state="paused",pausedBy=listOf(2),acceptedBy=listOf(1,2))
        assertFalse(paused.allows("resume",1)); assertTrue(paused.allows("resume",2)); assertFalse(paused.allows("confirm",1))
        val active = c.copy(state="active",confirmedBy=listOf(2),acceptedBy=listOf(1,2))
        assertFalse(active.allows("unconfirm",1)); assertTrue(active.allows("unconfirm",2)); assertTrue(active.allows("confirm",1))
    }
    @Test fun retryKeepsTheChallengeRequestedByAChatCard() = runTest {
        var offline = true
        val source = object : FakeChallenges() {
            override suspend fun get(id: String): com.srisu.srisu.core.data.remote.NetworkAPIResult<ChallengeSession?> =
                if (offline) MomentFixtures.failed() else super.get(id)
        }
        val vm = ChallengeViewModel(source, FakeSparkMatrix(), MomentFixtures.sessions(), environment)
        vm.open(ChallengeFixtures.session.id); runCurrent()
        assertNull(vm.state.value.session); assertNotNull(vm.state.value.error)
        offline = false; vm.retry(); runCurrent()
        assertEquals(ChallengeFixtures.session.id, vm.state.value.session?.id)
        vm.viewModelScope.cancel()
    }
    @Test fun leavingDetailCancelsARefreshThatWouldReopenIt() = runTest {
        var holdGet = false
        val gate = CompletableDeferred<Unit>()
        val source = object : FakeChallenges() {
            override suspend fun get(id: String): com.srisu.srisu.core.data.remote.NetworkAPIResult<ChallengeSession?> {
                if (holdGet) gate.await()
                return super.get(id)
            }
        }
        val vm = ChallengeViewModel(source, FakeSparkMatrix(), MomentFixtures.sessions(), environment)
        vm.open(ChallengeFixtures.session.id); runCurrent()
        holdGet = true; vm.refresh(quiet = true); runCurrent()
        vm.browse(); gate.complete(Unit); runCurrent()
        assertNull(vm.state.value.session)
        assertNull(vm.state.value.selected)
        vm.viewModelScope.cancel()
    }
    @Test fun withdrawingAnUnsentInvitationDoesNotPublishItToChat() = runTest {
        var publications = 0
        val matrix = object : com.srisu.srisu.features.chat.matrix.MatrixChatRepository by FakeSparkMatrix() {
            override suspend fun publishChallengeCard(session: ChallengeSession): Result<String> {
                publications++; return Result.success("\$qa-card")
            }
        }
        val source = object : FakeChallenges() {
            override suspend fun submit(intent: ChallengeIntent): com.srisu.srisu.core.data.remote.NetworkAPIResult<ChallengeSession?> {
                session = session.copy(state="cancelled",revision=session.revision+1)
                return com.srisu.srisu.core.data.remote.NetworkAPIResult.Success(session)
            }
        }.apply { session=session.copy(creator=1) }
        val vm = ChallengeViewModel(source, matrix, MomentFixtures.sessions(), environment)
        vm.open(source.session.id); runCurrent(); vm.action("cancel"); runCurrent()
        vm.publishCard(); runCurrent()
        assertEquals("cancelled", vm.state.value.session?.state)
        assertEquals(0, publications)
        vm.viewModelScope.cancel()
    }
    @Test fun successfulBackgroundRefreshClearsAnOldConnectionError() = runTest {
        var offline = false
        val source = object : FakeChallenges() {
            override suspend fun home(): com.srisu.srisu.core.data.remote.NetworkAPIResult<ChallengeHome?> =
                if (offline) MomentFixtures.failed() else super.home()
        }
        val vm = ChallengeViewModel(source, FakeSparkMatrix(), MomentFixtures.sessions(), environment)
        vm.open(ChallengeFixtures.session.id); runCurrent()
        offline = true; vm.refresh(quiet=true); runCurrent(); assertNotNull(vm.state.value.error)
        offline = false; vm.refresh(quiet=true); runCurrent()
        assertNull(vm.state.value.error); assertTrue(vm.state.value.canAct)
        vm.viewModelScope.cancel()
    }
    @Test fun userActionCancelsBackgroundReadWithoutDroppingOrDuplicatingTap() = runTest {
        var holdRead = false
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val source = object : FakeChallenges() {
            override suspend fun home(): com.srisu.srisu.core.data.remote.NetworkAPIResult<ChallengeHome?> {
                if (holdRead) kotlinx.coroutines.awaitCancellation()
                return super.home()
            }
            override suspend fun submit(intent: ChallengeIntent): com.srisu.srisu.core.data.remote.NetworkAPIResult<ChallengeSession?> {
                gate.await(); return super.submit(intent)
            }
        }
        val vm = ChallengeViewModel(source, FakeSparkMatrix(), MomentFixtures.sessions(), environment)
        vm.open(ChallengeFixtures.session.id); runCurrent()
        holdRead = true; vm.refresh(quiet = true); runCurrent()
        vm.action("accept"); runCurrent()
        assertTrue(vm.state.value.busy)
        vm.action("accept"); gate.complete(Unit); runCurrent()
        assertEquals(1, source.requests.size)
        assertEquals("active", vm.state.value.session?.state)
        vm.viewModelScope.cancel()
    }
    @Test fun referencesAreBoundedAndChatIdentitySurvivesRetry() {
        val ref = ChallengeReference(ChallengeFixtures.session.id,3)
        assertEquals(ref, ChallengeReference.parse(ref.url))
        listOf(ref.url+"?progress=done",ref.url.replace("srisu.app","elsewhere.test"),ref.url.replace("/3/","/0/"),ref.url.replace("/3/","/99999999999999999999999/")).forEach { assertNull(ChallengeReference.parse(it)) }
        val message = MatrixChatMessage("one","Activity","@one:test.invalid",true,0,
            com.srisu.srisu.features.chat.matrix.MatrixMessageDelivery.SENT, challengeReference=ref)
        assertEquals(message.stableKey,message.copy(id="retry").stableKey)
        assertNotEquals(message.stableKey,message.copy(senderMatrixId="@two:test.invalid").stableKey)
    }
}
