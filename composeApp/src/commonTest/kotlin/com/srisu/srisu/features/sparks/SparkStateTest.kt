package com.srisu.srisu.features.sparks

import androidx.lifecycle.viewModelScope
import com.srisu.srisu.features.chat.matrix.*
import com.srisu.srisu.features.moments.MomentFixtures
import com.srisu.srisu.features.sparks.data.*
import com.srisu.srisu.features.sparks.presentation.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class SparkStateTest {
    @BeforeTest fun before() { Dispatchers.setMain(StandardTestDispatcher()) }
    @AfterTest fun after() { Dispatchers.resetMain() }
    @Test fun submissionRetryAndRestartReuseExactCiphertextAndPreserveDraft() = runTest {
        val storage = mutableMapOf<String, String>(); val sessions = MomentFixtures.sessions(storage)
        val source = FakeSparks().apply { failAnswer = true }; val crypto = FakeSparkMatrix()
        val vm = SparkViewModel(source, crypto, sessions)
        vm.open(SparkFixtures.session.id); runCurrent(); vm.draft("A quiet kind of love"); vm.submit(); runCurrent()
        assertEquals("A quiet kind of love", vm.state.value.draft.text)
        assertFalse(vm.state.value.canEdit)
        assertEquals(1, crypto.encryptions)
        vm.submit(); runCurrent(); assertEquals(source.received[0], source.received[1]); assertEquals(1, crypto.encryptions)
        vm.viewModelScope.cancel()
        val restarted = SparkViewModel(source, crypto, sessions)
        restarted.open(SparkFixtures.session.id); runCurrent()
        assertEquals("A quiet kind of love", restarted.state.value.draft.text)
        source.failAnswer = false; restarted.submit(); runCurrent()
        assertEquals(1, crypto.encryptions); assertEquals(source.received.first(), source.received.last())
        assertTrue(restarted.state.value.session!!.submitted(1)); assertEquals("", restarted.state.value.draft.text)
        restarted.viewModelScope.cancel()
    }
    @Test fun skippedSparkClearsDraftAndAnswers() = runTest {
        val vm = SparkViewModel(FakeSparks(), FakeSparkMatrix(), MomentFixtures.sessions())
        vm.open(SparkFixtures.session.id); runCurrent(); vm.draft("Private draft"); vm.skip(); runCurrent()
        assertEquals("skipped", vm.state.value.session?.state); assertTrue(vm.state.value.answers.isEmpty()); assertEquals("", vm.state.value.draft.text)
        vm.viewModelScope.cancel()
    }
    @Test fun accountSwitchClearsScreenAndBlocksFurtherSubmission() = runTest {
        val source = FakeSparks(); val sessions = MomentFixtures.sessions(); val vm = SparkViewModel(source, FakeSparkMatrix(), sessions)
        vm.open(SparkFixtures.session.id); runCurrent(); vm.draft("Private draft"); sessions.clearSession(); runCurrent(); vm.submit(); runCurrent()
        assertNull(vm.state.value.session); assertEquals("", vm.state.value.draft.text); assertTrue(source.received.isEmpty())
        vm.viewModelScope.cancel()
    }
    @Test fun revealShowsBothOnlyAfterSuccessfulDecryptionAndRetriesMissingKeys() = runTest {
        val source = FakeSparks().apply { session = session.copy(state = "revealed", revision = 3, submittedBy = listOf(1, 2), answers = listOf(SparkFixtures.one, SparkFixtures.two)) }
        val crypto = FakeSparkMatrix().apply { missingPartnerKey = true }; val vm = SparkViewModel(source, crypto, MomentFixtures.sessions())
        vm.visible(true); vm.open(SparkFixtures.session.id); runCurrent()
        assertTrue(vm.state.value.keyError); assertTrue(vm.state.value.answers.isEmpty())
        crypto.missingPartnerKey = false; vm.refresh(); runCurrent()
        assertEquals(setOf(1L, 2L), vm.state.value.answers.keys)
        vm.visible(false); vm.viewModelScope.cancel()
    }
    @Test fun matrixRevocationClearsAlreadyRevealedAnswers() = runTest {
        val source = FakeSparks().apply { session = session.copy(state = "revealed", revision = 3, submittedBy = listOf(1, 2), answers = listOf(SparkFixtures.one, SparkFixtures.two)) }
        val crypto = FakeSparkMatrix(); val vm = SparkViewModel(source, crypto, MomentFixtures.sessions())
        vm.visible(true); vm.open(SparkFixtures.session.id); runCurrent()
        assertEquals(2, vm.state.value.answers.size)
        crypto.revoke(); runCurrent()
        assertTrue(vm.state.value.answers.isEmpty()); assertNull(vm.state.value.session); assertNull(vm.state.value.home)
        vm.visible(false); vm.viewModelScope.cancel()
    }
    @Test fun recoveredStartAcknowledgementClearsDurableIntent() = runTest {
        val sessions = MomentFixtures.sessions()
        sessions.saveSession(Json.encodeToString(SparkStartIntent(SparkFixtures.session.id,3,"deep-1-v1")), "spark-start:1")
        val vm = SparkViewModel(FakeSparks(), FakeSparkMatrix(), sessions)
        vm.open(SparkFixtures.session.id); runCurrent()
        assertFalse(vm.state.value.startPending); assertEquals("", sessions.getSession("spark-start:1"))
        vm.viewModelScope.cancel()
    }
    @Test fun secureConnectionDistinguishesLoadingFailureAndRetryRecoveryWithoutLosingDraft() = runTest {
        val crypto = FakeSparkMatrix().apply { availability(MatrixChatAvailability.Loading) }
        val vm = SparkViewModel(FakeSparks(), crypto, MomentFixtures.sessions())
        vm.open(SparkFixtures.session.id); runCurrent(); vm.draft("A retained draft")
        assertTrue(vm.state.value.secureConnecting); assertFalse(vm.state.value.secure)
        assertNull(vm.state.value.secureMessage)
        crypto.availability(MatrixChatAvailability.Offline("Connection interrupted")); runCurrent()
        assertFalse(vm.state.value.secureConnecting)
        assertEquals("Connection interrupted", vm.state.value.secureMessage)
        vm.retryKeys(); runCurrent()
        assertTrue(vm.state.value.secureConnecting)
        crypto.availability(MatrixChatAvailability.Ready()); runCurrent()
        assertTrue(vm.state.value.secure); assertFalse(vm.state.value.secureConnecting)
        assertNull(vm.state.value.secureMessage)
        assertEquals("A retained draft", vm.state.value.draft.text)
        vm.viewModelScope.cancel()
    }
    @Test fun returningToDecksFetchesTheJustCompletedSparkIntoHistory() = runTest {
        val finished = SparkFixtures.session.copy(state = "revealed", submittedBy = listOf(1, 2))
        var home = SparkFixtures.home.copy(active = null, recent = emptyList())
        val source = object : FakeSparks() {
            override suspend fun home() = com.srisu.srisu.core.data.remote.NetworkAPIResult.Success<SparkHome?>(home)
        }.apply { session = finished }
        val vm = SparkViewModel(source, FakeSparkMatrix(), MomentFixtures.sessions())
        vm.open(finished.id); runCurrent()
        assertTrue(vm.state.value.home!!.recent.isEmpty())
        home = home.copy(recent = listOf(finished))
        vm.browse(); runCurrent()
        assertNull(vm.state.value.session)
        assertEquals(finished.id, vm.state.value.home!!.recent.first().id)
        vm.viewModelScope.cancel()
    }
    @Test fun recoveryKeyIsClearedOnBackgroundAndAccountChange() = runTest {
        val sessions = MomentFixtures.sessions()
        val vm = SparkViewModel(FakeSparks(), FakeSparkMatrix(), sessions)
        vm.open(SparkFixtures.session.id); runCurrent(); vm.openRecovery(); runCurrent()
        vm.recoverExisting(); vm.recoveryKey("synthetic private input")
        vm.visible(false)
        assertEquals("", vm.state.value.recovery.key)
        assertFalse(vm.state.value.recovery.creating)
        vm.recoveryKey("synthetic private input"); sessions.clearSession(); runCurrent()
        assertEquals("", vm.state.value.recovery.key)
        assertFalse(vm.state.value.recovery.open)
        vm.viewModelScope.cancel()
    }
    @Test fun backupScansEveryPageAndReportsPartialFailure() = runTest {
        val source = object : FakeSparks() {
            override suspend fun history(after: String?) = com.srisu.srisu.core.data.remote.NetworkAPIResult.Success<SparkHistoryPage?>(
                if (after == null) SparkHistoryPage(listOf("first"), "cursor") else SparkHistoryPage(listOf("second")))
            override suspend fun get(id: String) = com.srisu.srisu.core.data.remote.NetworkAPIResult.Success<SparkSession?>(
                session.copy(id = id, answers = listOf(SparkFixtures.one)))
        }
        val crypto = FakeSparkMatrix().apply { recoveryStatus = SparkRecoveryStatus.Ready; failBackupId = "second" }
        val vm = SparkViewModel(source, crypto, MomentFixtures.sessions())
        vm.open(SparkFixtures.session.id); runCurrent(); vm.openRecovery(); runCurrent(); vm.backupRecovery(); runCurrent()
        assertEquals(listOf("first", "second"), crypto.backedUp)
        assertTrue(vm.state.value.recovery.message!!.contains("1 answers could not be backed up"))
        assertFalse(vm.state.value.recovery.busy)
        vm.viewModelScope.cancel()
    }
    @Test fun failedUnlockKeepsInputForRetryWithoutReportingSuccess() = runTest {
        val crypto = FakeSparkMatrix().apply { recoveryStatus = SparkRecoveryStatus.Locked }
        val vm = SparkViewModel(FakeSparks(), crypto, MomentFixtures.sessions())
        vm.open(SparkFixtures.session.id); runCurrent(); vm.openRecovery(); runCurrent()
        vm.recoveryKey("synthetic invalid key"); vm.unlockRecovery(); runCurrent()
        assertEquals(SparkRecoveryStatus.Locked, vm.state.value.recovery.status)
        assertEquals("synthetic invalid key", vm.state.value.recovery.key)
        assertNotNull(vm.state.value.recovery.error)
        assertFalse(vm.state.value.recovery.busy)
        vm.viewModelScope.cancel()
    }
    @Test fun plaintextBindingRejectsOtherQuestionsAuthorsAndRelationships() {
        val s = SparkFixtures.session
        val value = SparkPlaintext(spark=s.id,couple=s.couple,epoch=s.epoch,questionId=s.questionId,question=s.question,sender="@one:test.invalid",answer="Synthetic answer")
        assertEquals("Synthetic answer", value.validated(s, "@one:test.invalid"))
        listOf(value.copy(version=2),value.copy(epoch=2),value.copy(spark="other"),value.copy(couple=4),value.copy(question="Different"),value.copy(sender="@other:test.invalid"),value.copy(answer="")).forEach {
            assertFailsWith<IllegalArgumentException> { it.validated(s,"@one:test.invalid") }
        }
    }
    @Test fun retriedChatCardsKeepOneLogicalIdentityAcrossDevicesAndPagination() {
        val reference = SparkReference(SparkFixtures.session.id,3)
        val original = MatrixChatMessage("event-one", "Question", "@one:test.invalid",true,1,MatrixMessageDelivery.SENT,sparkReference=reference)
        val retried = original.copy(id="event-two",timestampMillis=2,transactionId="another-device")
        assertEquals(original.stableKey,retried.stableKey)
        assertEquals(1,listOf(original,retried).distinctBy { it.stableKey }.size)
        assertNotEquals(original.stableKey,retried.copy(sparkReference=reference.copy(id="another-spark")).stableKey)
    }
    @Test fun referencesAreBoundedAndRejectArbitraryNavigation() {
        val ref = SparkReference(SparkFixtures.session.id, 3)
        assertEquals(ref, SparkReference.parse(ref.url))
        listOf(ref.url+"?answer=secret",ref.url.replace("srisu.app","elsewhere.test"),ref.url.replace("/3/","/0/"),ref.url.replace("/3/","/999999999999999999999999/"),"javascript:alert(1)").forEach { assertNull(SparkReference.parse(it)) }
    }
}
internal class FakeSparkMatrix : MatrixChatRepository {
    private val state = MutableStateFlow(MatrixChatRoomState(SparkFixtures.session.roomId, availability=MatrixChatAvailability.Ready(), accountId=1))
    fun revoke() { state.value = state.value.copy(availability=MatrixChatAvailability.Unavailable("Relationship ended")) }
    fun availability(value: MatrixChatAvailability) { state.value = state.value.copy(availability=value) }
    var recoveryStatus = SparkRecoveryStatus.NotSetUp
    var failBackupId: String? = null
    val backedUp = mutableListOf<String>()
    override suspend fun sparkRecoveryStatus(session: SparkSession) = Result.success(recoveryStatus)
    override suspend fun backUpSparkAnswers(session: SparkSession): Result<Int> {
        backedUp += session.id
        return if (session.id == failBackupId) Result.failure(IllegalStateException()) else Result.success(session.answers.size)
    }
    var encryptions=0
    var missingPartnerKey=false
    override fun observeRoom(roomId:String):Flow<MatrixChatRoomState> = state
    override fun openRoom(roomId:String,owner:MatrixChatRoomOwner) = Unit
    override fun closeRoom(roomId:String,owner:MatrixChatRoomOwner) = Unit
    override suspend fun sendText(roomId:String,text:String)=Result.success("unused")
    override suspend fun retryMessage(roomId:String,transactionId:String)=Result.success(Unit)
    override fun retryRoom(roomId:String,owner:MatrixChatRoomOwner) = Unit
    override fun close()=Unit
    override suspend fun encryptSparkAnswer(session:SparkSession,text:String):Result<JsonObject> { encryptions++; return Result.success(SparkFixtures.envelope) }
    override suspend fun decryptSparkAnswer(session:SparkSession,answer:SparkAnswer):Result<String> = if(missingPartnerKey && answer.author==2L) Result.failure(IllegalStateException()) else Result.success("Synthetic answer ${answer.author}")
}
