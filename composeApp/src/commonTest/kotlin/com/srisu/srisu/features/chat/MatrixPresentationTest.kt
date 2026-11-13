package com.srisu.srisu.features.chat

import androidx.lifecycle.viewModelScope
import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.core.data.remote.ApiError
import com.srisu.srisu.core.data.remote.ApiJson
import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.core.lifecycle.ApplicationLifetime
import com.srisu.srisu.core.session.Session
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.core.session.SessionStorage
import com.srisu.srisu.features.chat.matrix.CurrentCoupleRoomResolver
import com.srisu.srisu.features.chat.matrix.MatrixChatAvailability
import com.srisu.srisu.features.chat.matrix.MatrixChatEntryState
import com.srisu.srisu.features.chat.matrix.MatrixChatEntryViewModel
import com.srisu.srisu.features.chat.matrix.MatrixChatRepository
import com.srisu.srisu.features.chat.matrix.MatrixChatRoomOwner
import com.srisu.srisu.features.chat.matrix.MatrixChatRoomState
import com.srisu.srisu.features.chat.matrix.MatrixPlatformStore
import com.srisu.srisu.features.chat.matrix.MatrixSessionDto
import com.srisu.srisu.features.chat.matrix.MatrixSessionRemote
import com.srisu.srisu.features.chat.matrix.MatrixStorageScope
import com.srisu.srisu.features.chat.matrix.TrixnityMatrixChatRepository
import com.srisu.srisu.features.chat.matrix.presentation.SecureChatViewModel
import com.srisu.srisu.utils.Constants.Auth.SESSION_KEY
import de.connect2x.trixnity.client.MediaStoreModule
import de.connect2x.trixnity.client.RepositoriesModule
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class MatrixPresentationTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun before() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun after() = Dispatchers.resetMain()

    @Test
    fun staleRoomResolverCannotNavigateAfterAccountSwitch() = runTest(dispatcher) {
        val sessions = coordinator(1)
        val resolver = SuspendedResolver()
        val viewModel = MatrixChatEntryViewModel(resolver, sessions)
        viewModel.resolve()
        runCurrent()

        sessions.saveSession(ApiJson.encodeToString(Session(id = 2, access = "synthetic-2")), SESSION_KEY)
        runCurrent()
        assertIs<MatrixChatEntryState.Unavailable>(viewModel.state.value)

        resolver.resume(0, NetworkAPIResult.Success(ROOM_A))
        advanceUntilIdle()
        assertIs<MatrixChatEntryState.Unavailable>(viewModel.state.value)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun roomResolverRestartsAfterSameAccountCredentialRotation() = runTest(dispatcher) {
        val sessions = coordinator(1)
        val resolver = SuspendedResolver()
        val viewModel = MatrixChatEntryViewModel(resolver, sessions)
        viewModel.resolve()
        runCurrent()
        assertEquals(1, resolver.callCount)

        sessions.saveSession(ApiJson.encodeToString(Session(id = 1, access = "rotated")), SESSION_KEY)
        runCurrent()
        assertEquals(2, resolver.callCount)
        assertIs<MatrixChatEntryState.Resolving>(viewModel.state.value)

        // A non-cooperative completion from the old credential generation is inert.
        resolver.resume(0, NetworkAPIResult.Success(ROOM_A))
        runCurrent()
        assertIs<MatrixChatEntryState.Resolving>(viewModel.state.value)

        resolver.resume(1, NetworkAPIResult.Success(ROOM_B))
        advanceUntilIdle()
        assertEquals(MatrixChatEntryState.Ready(ROOM_B), viewModel.state.value)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun roomSwitchKeepsNewDraftWhenOldSendCompletesLate() = runTest(dispatcher) {
        val repository = SuspendedSendRepository()
        val viewModel = SecureChatViewModel(repository)
        viewModel.openRoom(ROOM_A)
        viewModel.updateMessageInput("room A")
        viewModel.sendMessage()
        runCurrent()

        viewModel.openRoom(ROOM_B)
        viewModel.updateMessageInput("room B")
        repository.completeSend()
        advanceUntilIdle()

        assertEquals("room B", viewModel.messageInput.value)
        assertEquals(listOf(ROOM_A to "room A"), repository.sends)
        viewModel.closeRoom()
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun oversizedPlaintextIsRejectedWithVisibleValidationError() = runTest(dispatcher) {
        val viewModel = SecureChatViewModel(SuspendedSendRepository())
        viewModel.openRoom(ROOM_A)
        viewModel.updateMessageInput("x".repeat(32 * 1024 + 1))

        assertEquals("", viewModel.messageInput.value)
        assertEquals("Message is too long.", viewModel.composerValidationError.value)

        viewModel.updateMessageInput("within budget")
        assertEquals("within budget", viewModel.messageInput.value)
        assertEquals(null, viewModel.composerValidationError.value)
        viewModel.closeRoom()
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun failedOutboxRetryIsVisibleAndSuccessClearsTheError() = runTest(dispatcher) {
        val repository = SuspendedSendRepository().apply {
            retryResult = Result.failure(IllegalStateException("synthetic retry failure"))
        }
        val viewModel = SecureChatViewModel(repository)
        viewModel.openRoom(ROOM_A)

        viewModel.retryMessage("txn-1")
        advanceUntilIdle()
        assertEquals("Message retry failed. Try again.", viewModel.composerValidationError.value)

        repository.retryResult = Result.success(Unit)
        viewModel.retryMessage("txn-1")
        advanceUntilIdle()
        assertEquals(null, viewModel.composerValidationError.value)
        viewModel.closeRoom()
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun bootstrapTimeoutShowsRetryInsteadOfRemainingLoading() = runTest(dispatcher) {
        val lifetime = ApplicationLifetime()
        var calls = 0
        val repository = TrixnityMatrixChatRepository(
            remote = object : MatrixSessionRemote {
                override suspend fun create(roomId: String): NetworkAPIResult<MatrixSessionDto?> {
                    if (++calls == 1) withTimeout(100) { awaitCancellation() }
                    return NetworkAPIResult.Error(ApiError(
                        kind = NetworkAPIResult.ErrorType.NOT_FOUND,
                        code = "not_found", message = "Conversation unavailable.", status = 404,
                    ))
                }
            },
            sessions = coordinator(1),
            environment = ApiEnvironment("https://api.example.test/"),
            engine = MockEngine { respondOk() },
            platformStore = UnusedPlatformStore,
            lifetime = lifetime,
        )
        try {
            runCurrent()
            val owner = MatrixChatRoomOwner()
            repository.openRoom(ROOM_A, owner)
            advanceUntilIdle()
            val failure = assertIs<MatrixChatAvailability.Error>(repository.observeRoom(ROOM_A).first().availability)
            assertTrue(failure.retryable)
            repository.retryRoom(ROOM_A, owner)
            advanceUntilIdle()
            assertEquals(2, calls)
            assertIs<MatrixChatAvailability.Unavailable>(repository.observeRoom(ROOM_A).first().availability)
        } finally {
            repository.close()
            lifetime.close()
        }
    }

    @Test
    fun djangoCredentialRotationDuringBootstrapNeverLeavesLoadingState() = runTest(dispatcher) {
        val sessions = coordinator(1)
        val lifetime = ApplicationLifetime()
        val repository = TrixnityMatrixChatRepository(
            remote = object : MatrixSessionRemote {
                override suspend fun create(roomId: String): NetworkAPIResult<MatrixSessionDto?> {
                    sessions.saveSession(
                        ApiJson.encodeToString(Session(id = 1, access = "rotated")),
                        SESSION_KEY,
                    )
                    return NetworkAPIResult.Error(
                        ApiError(
                            kind = NetworkAPIResult.ErrorType.SERVER,
                            code = "matrix_unavailable",
                            message = "Matrix is unavailable.",
                            status = 503,
                            retryable = true,
                        )
                    )
                }
            },
            sessions = sessions,
            environment = ApiEnvironment("https://api.example.test/"),
            engine = MockEngine { respondOk() },
            platformStore = UnusedPlatformStore,
            lifetime = lifetime,
        )

        val owner = MatrixChatRoomOwner()
        repository.openRoom(ROOM_A, owner)
        advanceUntilIdle()

        assertIs<MatrixChatAvailability.Error>(repository.observeRoom(ROOM_A).first().availability)
        repository.close()
        lifetime.close()
    }

    @Test
    fun retryable503StaysLoadingUntilBootstrapReachesTheNextPhase() = runTest(dispatcher) {
        val sessions = coordinator(1)
        val lifetime = ApplicationLifetime()
        val platformStore = BootstrapPhasePlatformStore()
        var calls = 0
        val repository = TrixnityMatrixChatRepository(
            remote = object : MatrixSessionRemote {
                override suspend fun create(roomId: String): NetworkAPIResult<MatrixSessionDto?> =
                    if (++calls == 1) retryableBootstrap503()
                    else NetworkAPIResult.Success(validMatrixSession())
            },
            sessions = sessions,
            environment = ApiEnvironment("https://api.example.test/"),
            engine = MockEngine { respondOk() },
            platformStore = platformStore,
            lifetime = lifetime,
        )
        runCurrent()

        val owner = MatrixChatRoomOwner()
        repository.openRoom(ROOM_A, owner)
        runCurrent()
        assertEquals(1, calls)
        assertIs<MatrixChatAvailability.Loading>(repository.observeRoom(ROOM_A).first().availability)

        advanceTimeBy(999)
        runCurrent()
        assertEquals(1, calls)
        assertIs<MatrixChatAvailability.Loading>(repository.observeRoom(ROOM_A).first().availability)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, calls)
        assertEquals(1, platformStore.deviceIdRequests)
        assertIs<MatrixChatAvailability.Error>(repository.observeRoom(ROOM_A).first().availability)

        repository.close()
        lifetime.close()
    }

    @Test
    fun retryable503ExhaustsAfterFourCallsAndManualRetryStartsANewAttempt() = runTest(dispatcher) {
        val sessions = coordinator(1)
        val lifetime = ApplicationLifetime()
        var calls = 0
        val repository = TrixnityMatrixChatRepository(
            remote = object : MatrixSessionRemote {
                override suspend fun create(roomId: String): NetworkAPIResult<MatrixSessionDto?> {
                    calls += 1
                    return retryableBootstrap503()
                }
            },
            sessions = sessions,
            environment = ApiEnvironment("https://api.example.test/"),
            engine = MockEngine { respondOk() },
            platformStore = UnusedPlatformStore,
            lifetime = lifetime,
        )
        runCurrent()

        val owner = MatrixChatRoomOwner()
        repository.openRoom(ROOM_A, owner)
        runCurrent()
        assertEquals(1, calls)
        assertIs<MatrixChatAvailability.Loading>(repository.observeRoom(ROOM_A).first().availability)

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(2, calls)
        advanceTimeBy(1_999)
        runCurrent()
        assertEquals(2, calls)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(3, calls)
        advanceTimeBy(3_999)
        runCurrent()
        assertEquals(3, calls)
        advanceTimeBy(1)
        runCurrent()

        assertEquals(4, calls)
        assertTrue(
            assertIs<MatrixChatAvailability.Error>(repository.observeRoom(ROOM_A).first().availability).retryable
        )

        repository.retryRoom(ROOM_A, owner)
        runCurrent()
        assertEquals(5, calls)
        assertIs<MatrixChatAvailability.Loading>(repository.observeRoom(ROOM_A).first().availability)

        repository.close()
        lifetime.close()
    }

    @Test
    fun non503AndNonRetryableBootstrapFailuresAreNeverAutomaticallyRetried() = runTest(dispatcher) {
        val failures = listOf(
            ApiError(
                kind = NetworkAPIResult.ErrorType.NOT_FOUND,
                code = "not_found",
                message = "Conversation unavailable.",
                status = 404,
                retryable = true,
            ),
            ApiError(
                kind = NetworkAPIResult.ErrorType.BAD_REQUEST,
                code = "validation_failed",
                message = "Invalid bootstrap request.",
                status = 400,
                retryable = true,
            ),
            ApiError(
                kind = NetworkAPIResult.ErrorType.SERVER,
                code = "temporarily_unavailable",
                message = "Provisioning unavailable.",
                status = 503,
                retryable = false,
            ),
        )

        failures.forEach { failure ->
            val sessions = coordinator(1)
            val lifetime = ApplicationLifetime()
            var calls = 0
            val repository = TrixnityMatrixChatRepository(
                remote = object : MatrixSessionRemote {
                    override suspend fun create(roomId: String): NetworkAPIResult<MatrixSessionDto?> {
                        calls += 1
                        return NetworkAPIResult.Error(failure)
                    }
                },
                sessions = sessions,
                environment = ApiEnvironment("https://api.example.test/"),
                engine = MockEngine { respondOk() },
                platformStore = UnusedPlatformStore,
                lifetime = lifetime,
            )
            runCurrent()

            repository.openRoom(ROOM_A, MatrixChatRoomOwner())
            advanceUntilIdle()

            assertEquals(1, calls, "Unexpected retry for status ${failure.status}")
            repository.close()
            lifetime.close()
        }
    }

    @Test
    fun staleScreenOwnerCannotCloseRoomClaimedByNewScreen() = runTest(dispatcher) {
        val sessions = coordinator(1)
        val lifetime = ApplicationLifetime()
        var calls = 0
        val failure = ApiError(
            kind = NetworkAPIResult.ErrorType.BAD_REQUEST,
            code = "synthetic_stop",
            message = "Synthetic bootstrap stop.",
            status = 400,
            retryable = false,
        )
        val repository = TrixnityMatrixChatRepository(
            remote = object : MatrixSessionRemote {
                override suspend fun create(roomId: String): NetworkAPIResult<MatrixSessionDto?> {
                    calls += 1
                    return NetworkAPIResult.Error(failure)
                }
            },
            sessions = sessions,
            environment = ApiEnvironment("https://api.example.test/"),
            engine = MockEngine { respondOk() },
            platformStore = UnusedPlatformStore,
            lifetime = lifetime,
        )
        runCurrent()
        val outgoingOwner = MatrixChatRoomOwner()
        val currentOwner = MatrixChatRoomOwner()

        repository.openRoom(ROOM_A, outgoingOwner)
        advanceUntilIdle()
        repository.openRoom(ROOM_A, currentOwner)
        advanceUntilIdle()
        assertEquals(2, calls)
        val currentState = repository.observeRoom(ROOM_A).first()
        assertIs<MatrixChatAvailability.Error>(currentState.availability)

        repository.closeRoom(ROOM_A, outgoingOwner)
        runCurrent()

        assertEquals(currentState, repository.observeRoom(ROOM_A).first())
        repository.retryRoom(ROOM_A, currentOwner)
        advanceUntilIdle()
        assertEquals(3, calls)
        repository.close()
        lifetime.close()
    }

    @Test
    fun staleRoomAndAccountOperationsCannotContinueBootstrapRetries() = runTest(dispatcher) {
        val sessions = coordinator(1)
        val lifetime = ApplicationLifetime()
        val rooms = mutableListOf<String>()
        val repository = TrixnityMatrixChatRepository(
            remote = object : MatrixSessionRemote {
                override suspend fun create(roomId: String): NetworkAPIResult<MatrixSessionDto?> {
                    rooms += roomId
                    return retryableBootstrap503()
                }
            },
            sessions = sessions,
            environment = ApiEnvironment("https://api.example.test/"),
            engine = MockEngine { respondOk() },
            platformStore = UnusedPlatformStore,
            lifetime = lifetime,
        )
        runCurrent()

        repository.openRoom(ROOM_A, MatrixChatRoomOwner())
        runCurrent()
        assertEquals(listOf(ROOM_A), rooms)

        repository.openRoom(ROOM_B, MatrixChatRoomOwner())
        runCurrent()
        assertEquals(1, rooms.count { it == ROOM_A })
        assertEquals(1, rooms.count { it == ROOM_B })

        sessions.saveSession(ApiJson.encodeToString(Session(id = 2, access = "synthetic-2")), SESSION_KEY)
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(1, rooms.count { it == ROOM_A })
        assertEquals(1, rooms.count { it == ROOM_B })
        repository.close()
        lifetime.close()
    }

    @Test
    fun accountLogoutWipesPersistedScopesWithoutOpeningChat() = runTest(dispatcher) {
        val sessions = coordinator(1)
        val lifetime = ApplicationLifetime()
        val platformStore = RecordingPlatformStore()
        val repository = TrixnityMatrixChatRepository(
            remote = object : MatrixSessionRemote {
                override suspend fun create(roomId: String): NetworkAPIResult<MatrixSessionDto?> =
                    error("Chat must not open in this test")
            },
            sessions = sessions,
            environment = ApiEnvironment("https://api.example.test/"),
            engine = MockEngine { respondOk() },
            platformStore = platformStore,
            lifetime = lifetime,
        )
        runCurrent()

        sessions.clearSession()
        advanceUntilIdle()

        assertTrue(1L in platformStore.deletedAccounts)
        repository.close()
        lifetime.close()
    }

    @Test
    fun cleanupBlockedRetryReconcilesBeforeBootstrap() = runTest(dispatcher) {
        val sessions = coordinator(1)
        val lifetime = ApplicationLifetime()
        val platformStore = RestartCleanupPlatformStore().apply { failCleanup = true }
        var bootstrapCalls = 0
        val repository = TrixnityMatrixChatRepository(
            remote = object : MatrixSessionRemote {
                override suspend fun create(roomId: String): NetworkAPIResult<MatrixSessionDto?> {
                    bootstrapCalls += 1
                    error("synthetic bootstrap stop")
                }
            },
            sessions = sessions,
            environment = ApiEnvironment("https://api.example.test/"),
            engine = MockEngine { respondOk() },
            platformStore = platformStore,
            lifetime = lifetime,
        )
        runCurrent()
        val owner = MatrixChatRoomOwner()
        repository.openRoom(ROOM_A, owner)
        advanceUntilIdle()
        assertIs<MatrixChatAvailability.Error>(repository.observeRoom(ROOM_A).first().availability)
        assertEquals(0, bootstrapCalls)

        platformStore.failCleanup = false
        repository.retryRoom(ROOM_A, owner)
        advanceUntilIdle()

        assertEquals(1, bootstrapCalls)
        repository.close()
        lifetime.close()
    }

    @Test
    fun failedAccountWipeDoesNotKillFutureCleanupTransitions() = runTest(dispatcher) {
        val sessions = coordinator(1)
        val lifetime = ApplicationLifetime()
        val platformStore = FlakyPlatformStore()
        val repository = TrixnityMatrixChatRepository(
            remote = object : MatrixSessionRemote {
                override suspend fun create(roomId: String): NetworkAPIResult<MatrixSessionDto?> =
                    error("Chat must not open in this test")
            },
            sessions = sessions,
            environment = ApiEnvironment("https://api.example.test/"),
            engine = MockEngine { respondOk() },
            platformStore = platformStore,
            lifetime = lifetime,
        )
        runCurrent()
        platformStore.failCleanup = true

        sessions.saveSession(ApiJson.encodeToString(Session(id = 2, access = "synthetic-2")), SESSION_KEY)
        advanceUntilIdle()
        repository.openRoom(ROOM_A, MatrixChatRoomOwner())
        assertIs<MatrixChatAvailability.Error>(repository.observeRoom(ROOM_A).first().availability)
        assertTrue(platformStore.attempts.any { it == 1L })

        platformStore.failCleanup = false
        sessions.saveSession(ApiJson.encodeToString(Session(id = 3, access = "synthetic-3")), SESSION_KEY)
        advanceUntilIdle()
        assertTrue(platformStore.attempts.count { it == 1L } >= 2)
        assertTrue(2L in platformStore.attempts)
        repository.close()
        lifetime.close()
    }

    @Test
    fun durableCleanupJournalReplaysAfterRestartWhileSignedOut() = runTest(dispatcher) {
        val platformStore = RestartCleanupPlatformStore()
        val firstSessions = coordinator(1)
        val firstLifetime = ApplicationLifetime()
        val firstRepository = TrixnityMatrixChatRepository(
            remote = object : MatrixSessionRemote {
                override suspend fun create(roomId: String): NetworkAPIResult<MatrixSessionDto?> = error("unused")
            },
            sessions = firstSessions,
            environment = ApiEnvironment("https://api.example.test/"),
            engine = MockEngine { respondOk() },
            platformStore = platformStore,
            lifetime = firstLifetime,
        )
        runCurrent()
        platformStore.failCleanup = true
        firstSessions.clearSession()
        advanceUntilIdle()
        assertTrue(1L in platformStore.markedAccounts)
        firstRepository.close()
        firstLifetime.close()

        platformStore.failCleanup = false
        val signedOutSessions = SessionCoordinator(MemoryStorage())
        val secondLifetime = ApplicationLifetime()
        val secondRepository = TrixnityMatrixChatRepository(
            remote = object : MatrixSessionRemote {
                override suspend fun create(roomId: String): NetworkAPIResult<MatrixSessionDto?> = error("unused")
            },
            sessions = signedOutSessions,
            environment = ApiEnvironment("https://api.example.test/"),
            engine = MockEngine { respondOk() },
            platformStore = platformStore,
            lifetime = secondLifetime,
        )
        advanceUntilIdle()

        assertTrue(platformStore.markedAccounts.isEmpty())
        assertTrue(1L in platformStore.reconciledAccounts)
        secondRepository.close()
        secondLifetime.close()
    }

    private fun coordinator(accountId: Long): SessionCoordinator =
        SessionCoordinator(MemoryStorage()).apply {
            saveSession(ApiJson.encodeToString(Session(id = accountId, access = "synthetic-$accountId")), SESSION_KEY)
        }

    private class SuspendedResolver : CurrentCoupleRoomResolver {
        private val continuations = mutableListOf<Continuation<NetworkAPIResult<String?>>>()
        val callCount: Int get() = continuations.size
        override suspend fun resolve(): NetworkAPIResult<String?> = suspendCoroutine { continuations += it }
        fun resume(call: Int, result: NetworkAPIResult<String?>) = continuations[call].resume(result)
    }

    private class SuspendedSendRepository : MatrixChatRepository {
        val sends = mutableListOf<Pair<String, String>>()
        var retryResult: Result<Unit> = Result.success(Unit)
        private lateinit var continuation: Continuation<Result<String>>
        private val state = MutableStateFlow(MatrixChatRoomState(ROOM_A, availability = MatrixChatAvailability.Ready()))

        override fun observeRoom(roomId: String): Flow<MatrixChatRoomState> = state
        override fun openRoom(roomId: String, owner: MatrixChatRoomOwner) {
            state.value = MatrixChatRoomState(roomId, availability = MatrixChatAvailability.Ready())
        }
        override fun closeRoom(roomId: String, owner: MatrixChatRoomOwner) = Unit
        override suspend fun sendText(roomId: String, text: String): Result<String> = suspendCoroutine {
            sends += roomId to text
            continuation = it
        }
        fun completeSend() = continuation.resume(Result.success("event"))
        override suspend fun retryMessage(roomId: String, transactionId: String) = retryResult
        override fun retryRoom(roomId: String, owner: MatrixChatRoomOwner) = Unit
        override fun close() = Unit
    }

    private class MemoryStorage : SessionStorage {
        private var value: String? = null
        override fun getSession(sessionKey: String) = value
        override fun saveSession(credentials: String, sessionKey: String) { value = credentials }
        override fun clearSession(): Boolean { value = null; return true }
        override fun clearOnReinstall(key: String) = Unit
    }

    private object UnusedPlatformStore : MatrixPlatformStore {
        override fun deviceId(scope: MatrixStorageScope): String = error("Matrix login must not run in this test")
        override fun repositories(scope: MatrixStorageScope): RepositoriesModule = error("unused")
        override fun media(scope: MatrixStorageScope): MediaStoreModule = error("unused")
        override suspend fun deleteAccountData(scope: MatrixStorageScope) = Unit
        override suspend fun deleteAllAccountData(accountId: Long, preserving: MatrixStorageScope?) = Unit
        override fun recordAccountCleanupRequired(accountId: Long) = Unit
        override suspend fun reconcileAccountData(currentAccountId: Long?) = Unit
        override suspend fun rotateDeviceId(scope: MatrixStorageScope): String = error("unused")
    }

    private class RecordingPlatformStore : MatrixPlatformStore {
        val deletedAccounts = mutableListOf<Long>()
        override fun deviceId(scope: MatrixStorageScope): String = error("unused")
        override fun repositories(scope: MatrixStorageScope): RepositoriesModule = error("unused")
        override fun media(scope: MatrixStorageScope): MediaStoreModule = error("unused")
        override suspend fun deleteAccountData(scope: MatrixStorageScope) = Unit
        override suspend fun deleteAllAccountData(accountId: Long, preserving: MatrixStorageScope?) {
            deletedAccounts += accountId
        }
        override fun recordAccountCleanupRequired(accountId: Long) = Unit
        override suspend fun reconcileAccountData(currentAccountId: Long?) = Unit
        override suspend fun rotateDeviceId(scope: MatrixStorageScope): String = error("unused")
    }

    private class BootstrapPhasePlatformStore : MatrixPlatformStore {
        var deviceIdRequests = 0
        override fun deviceId(scope: MatrixStorageScope): String {
            deviceIdRequests += 1
            error("Synthetic stop after successful bootstrap validation")
        }
        override fun repositories(scope: MatrixStorageScope): RepositoriesModule = error("unused")
        override fun media(scope: MatrixStorageScope): MediaStoreModule = error("unused")
        override suspend fun deleteAccountData(scope: MatrixStorageScope) = Unit
        override suspend fun deleteAllAccountData(accountId: Long, preserving: MatrixStorageScope?) = Unit
        override fun recordAccountCleanupRequired(accountId: Long) = Unit
        override suspend fun reconcileAccountData(currentAccountId: Long?) = Unit
        override suspend fun rotateDeviceId(scope: MatrixStorageScope): String = error("unused")
    }

    private class FlakyPlatformStore : MatrixPlatformStore {
        val attempts = mutableListOf<Long>()
        var failCleanup = false
        override fun deviceId(scope: MatrixStorageScope): String = error("unused")
        override fun repositories(scope: MatrixStorageScope): RepositoriesModule = error("unused")
        override fun media(scope: MatrixStorageScope): MediaStoreModule = error("unused")
        override suspend fun deleteAccountData(scope: MatrixStorageScope) = Unit
        override suspend fun deleteAllAccountData(accountId: Long, preserving: MatrixStorageScope?) {
            attempts += accountId
            if (failCleanup) error("synthetic enumeration failure")
        }
        override fun recordAccountCleanupRequired(accountId: Long) = Unit
        override suspend fun reconcileAccountData(currentAccountId: Long?) {
            if (failCleanup) error("synthetic startup sweep failure")
        }
        override suspend fun rotateDeviceId(scope: MatrixStorageScope): String = error("unused")
    }

    private class RestartCleanupPlatformStore : MatrixPlatformStore {
        val markedAccounts = mutableSetOf<Long>()
        val reconciledAccounts = mutableSetOf<Long>()
        var failCleanup = false
        override fun deviceId(scope: MatrixStorageScope): String = error("unused")
        override fun repositories(scope: MatrixStorageScope): RepositoriesModule = error("unused")
        override fun media(scope: MatrixStorageScope): MediaStoreModule = error("unused")
        override suspend fun deleteAccountData(scope: MatrixStorageScope) = Unit
        override suspend fun deleteAllAccountData(accountId: Long, preserving: MatrixStorageScope?) {
            if (failCleanup) error("synthetic cleanup interruption")
            reconciledAccounts += accountId
        }
        override fun recordAccountCleanupRequired(accountId: Long) {
            markedAccounts += accountId
        }
        override suspend fun reconcileAccountData(currentAccountId: Long?) {
            if (failCleanup) error("synthetic startup sweep interruption")
            reconciledAccounts += markedAccounts
            markedAccounts.clear()
        }
        override suspend fun rotateDeviceId(scope: MatrixStorageScope): String = error("unused")
    }

    private fun retryableBootstrap503(): NetworkAPIResult<MatrixSessionDto?> = NetworkAPIResult.Error(
        ApiError(
            kind = NetworkAPIResult.ErrorType.SERVER,
            code = "temporarily_unavailable",
            message = "Secure chat is still being prepared.",
            status = 503,
            retryable = true,
        )
    )

    private fun validMatrixSession() = MatrixSessionDto(
        protocolId = "matrix-e2ee-v1",
        homeserverUrl = "https://matrix.example.test/",
        roomId = "!couple:matrix.example.test",
        roomAlias = "#couple:matrix.example.test",
        membershipEpoch = 1,
        relationshipId = 9,
        selfUserId = 1,
        selfMatrixUserId = "@srisu_1:matrix.example.test",
        partnerUserId = 2,
        partnerMatrixUserId = "@srisu_2:matrix.example.test",
        initiatorUserId = 1,
        loginType = "org.matrix.login.jwt",
        loginToken = "synthetic.jwt.value",
        loginTokenExpiresAt = "2099-01-01T00:00:00Z",
        loginTokenOneTime = false,
        refreshTokenEnabled = false,
        accessTokenExpiresInSeconds = 300,
    )

    private companion object {
        const val ROOM_A = "123e4567-e89b-42d3-a456-426614174000"
        const val ROOM_B = "123e4567-e89b-42d3-a456-426614174001"
    }
}
