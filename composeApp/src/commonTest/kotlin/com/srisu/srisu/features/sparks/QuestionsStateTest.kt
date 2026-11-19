package com.srisu.srisu.features.sparks

import androidx.lifecycle.viewModelScope
import com.srisu.srisu.core.data.remote.ApiError
import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.features.moments.MomentFixtures
import com.srisu.srisu.features.sparks.data.*
import com.srisu.srisu.features.sparks.presentation.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class QuestionsStateTest {
    @BeforeTest fun before() { Dispatchers.setMain(StandardTestDispatcher()) }
    @AfterTest fun after() { Dispatchers.resetMain() }
    private class Source : FakeSparks() {
        var snapshot = SparkFixtures.home
        var dailyCalls = 0
        var details = 0
        var dailyResult: NetworkAPIResult<SparkDaily?> = NetworkAPIResult.Success(SparkDaily(session, "2026-11-13", "Asia/Kathmandu", carried = true))
        var failDetail = false
        override suspend fun home() = NetworkAPIResult.Success<SparkHome?>(snapshot)
        override suspend fun daily(couple: Long, zone: String): NetworkAPIResult<SparkDaily?> {
            assertEquals(snapshot.couple, couple); assertTrue(zone.isNotBlank()); dailyCalls++
            return dailyResult
        }
        override suspend fun get(id: String): NetworkAPIResult.Success<SparkSession?> {
            details++
            assertEquals(session.id, id)
            return if (failDetail) throw SparkRequestFailure(null, "Offline") else NetworkAPIResult.Success(session)
        }
    }
    @Test fun sharedDailyDoesNotOpenPrivateEditorAndPollsOnlyWhileVisible() = runTest {
        val source = Source(); val vm = SparkViewModel(source, FakeSparkMatrix(), MomentFixtures.sessions())
        vm.open(null, daily = true); runCurrent()
        assertEquals(source.session.id, vm.state.value.daily?.session?.id)
        assertTrue(vm.state.value.daily!!.carried); assertNull(vm.state.value.session); assertEquals(0, source.details)
        vm.visible(true); runCurrent(); val before = source.dailyCalls
        advanceTimeBy(29_999); runCurrent(); assertEquals(before, source.dailyCalls)
        advanceTimeBy(1); runCurrent(); assertEquals(before + 1, source.dailyCalls)
        vm.visible(false); advanceTimeBy(60_000); runCurrent(); assertEquals(before + 1, source.dailyCalls)
        vm.viewModelScope.cancel()
    }
    @Test fun dailyFailureRetainsCatalogueWithoutPretendingItIsEmpty() = runTest {
        val source = Source().apply { dailyResult = NetworkAPIResult.Error("Offline") }
        val vm = SparkViewModel(source, FakeSparkMatrix(), MomentFixtures.sessions())
        vm.open(null, daily = true); runCurrent()
        assertNotNull(vm.state.value.home); assertNotNull(vm.state.value.dailyError); assertTrue(vm.state.value.fresh)
        assertNull(vm.state.value.daily); assertNull(vm.state.value.session)
        vm.viewModelScope.cancel()
    }
    @Test fun noDailyAllocationWithoutPartnerOrWhileOpeningDetail() = runTest {
        val source = Source().apply { snapshot = snapshot.copy(couple = null) }
        val vm = SparkViewModel(source, FakeSparkMatrix(), MomentFixtures.sessions())
        vm.open(null, daily = true); runCurrent(); assertEquals(0, source.dailyCalls); vm.viewModelScope.cancel()
        source.snapshot = SparkFixtures.home
        val detail = SparkViewModel(source, FakeSparkMatrix(), MomentFixtures.sessions())
        detail.open(source.session.id, daily = true); runCurrent(); assertEquals(0, source.dailyCalls)
        assertEquals(source.session.id, detail.state.value.session?.id); detail.viewModelScope.cancel()
    }
    @Test fun detailRetryKeepsTheRequestedQuestion() = runTest {
        val source = Source().apply { failDetail = true }
        val vm = SparkViewModel(source, FakeSparkMatrix(), MomentFixtures.sessions())
        vm.open(source.session.id); runCurrent(); assertNotNull(vm.state.value.error)
        source.failDetail = false; vm.refresh(); runCurrent()
        assertEquals(source.session.id, vm.state.value.session?.id); assertEquals(2, source.details)
        vm.viewModelScope.cancel()
    }
    @Test fun accessDeniedClearsDailyAndCatalogue() = runTest {
        val source = Source(); val vm = SparkViewModel(source, FakeSparkMatrix(), MomentFixtures.sessions())
        vm.open(null, daily = true); runCurrent(); assertNotNull(vm.state.value.daily)
        source.dailyResult = NetworkAPIResult.Error(ApiError(NetworkAPIResult.ErrorType.FORBIDDEN,"forbidden","No longer available",status=403))
        vm.refresh(); runCurrent(); assertNull(vm.state.value.daily); assertNull(vm.state.value.home)
        vm.viewModelScope.cancel()
    }
    @Test fun changedCoupleClearsPrivateAnswersAndDraft() = runTest {
        val source = Source(); val vm = SparkViewModel(source, FakeSparkMatrix(), MomentFixtures.sessions())
        vm.open(source.session.id); runCurrent(); vm.draft("Private synthetic draft")
        source.snapshot = source.snapshot.copy(couple = null, members = emptyList())
        vm.refresh(); runCurrent(); assertNull(vm.state.value.session); assertTrue(vm.state.value.answers.isEmpty()); assertEquals("",vm.state.value.draft.text)
        vm.viewModelScope.cancel()
    }
    @Test fun catalogueSearchIsCaseInsensitiveAndMatchesEveryTerm() {
        val decks = SparkFixtures.home.decks
        assertEquals(decks.sumOf { it.questions.size }, questionMatches(decks,"  ").size)
        assertEquals(listOf("memories-2-v1"), questionMatches(decks,"  MEMORIES   conversation  ").map { it.id })
        assertTrue(questionMatches(decks,"absent-word").isEmpty())
    }
    @Test fun previewSelectsExactQuestionInItsDeck() = runTest {
        val vm = SparkViewModel(Source(), FakeSparkMatrix(), MomentFixtures.sessions())
        vm.open(null); runCurrent(); vm.selectQuestion("future-4-v1")
        assertEquals("future",vm.state.value.selectedDeck?.id); assertEquals("future-4-v1",vm.state.value.selectedQuestion?.id)
        vm.selectQuestion("missing"); assertEquals("future-4-v1",vm.state.value.selectedQuestion?.id)
        vm.viewModelScope.cancel()
    }
}
