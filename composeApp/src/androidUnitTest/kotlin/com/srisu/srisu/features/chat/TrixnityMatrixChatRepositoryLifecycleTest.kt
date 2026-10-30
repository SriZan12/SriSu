package com.srisu.srisu.features.chat

import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.core.data.remote.ApiJson
import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.core.lifecycle.ApplicationLifetime
import com.srisu.srisu.core.session.Session
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.core.session.SessionStorage
import com.srisu.srisu.features.chat.matrix.MatrixChatAvailability
import com.srisu.srisu.features.chat.matrix.MatrixChatRoomOwner
import com.srisu.srisu.features.chat.matrix.MatrixPlatformStore
import com.srisu.srisu.features.chat.matrix.MatrixSessionDto
import com.srisu.srisu.features.chat.matrix.MatrixSessionRemote
import com.srisu.srisu.features.chat.matrix.MatrixStorageScope
import com.srisu.srisu.features.chat.matrix.TrixnityMatrixChatRepository
import com.srisu.srisu.utils.Constants.Auth.SESSION_KEY
import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.client.MediaStoreModule
import de.connect2x.trixnity.client.RepositoriesModule
import de.connect2x.trixnity.core.model.UserId
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ktor.http.Url
import java.lang.reflect.Proxy
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.koin.dsl.koinApplication
import org.koin.dsl.module

@OptIn(ExperimentalCoroutinesApi::class)
class TrixnityMatrixChatRepositoryLifecycleTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun before() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun after() = Dispatchers.resetMain()

    @Test
    fun rejectedUnstoppableCandidateBlocksProtectedStoreCleanupAcrossRetryAndAccountSwitch() =
        runTest(dispatcher) {
            val sessions = coordinator(1)
            val lifetime = ApplicationLifetime()
            val platformStore = RecordingPlatformStore()
            val clientJob = Job()
            var suspendingCloseAttempts = 0
            var fallbackCloseAttempts = 0
            val clientScope = CoroutineScope(clientJob)
            val clientDi = koinApplication {
                modules(module { single<CoroutineScope> { clientScope } })
            }.koin
            val rejectedClient = Proxy.newProxyInstance(
                MatrixClient::class.java.classLoader,
                arrayOf(MatrixClient::class.java),
            ) { proxy, method, args ->
                when (method.name) {
                    "getUserId" -> UserId("@wrong-user:matrix.example.test")
                    "getDeviceId" -> DEVICE_ID
                    "getBaseUrl" -> Url(MATRIX_BASE_URL)
                    "getDi" -> clientDi
                    "closeSuspending" -> {
                        suspendingCloseAttempts += 1
                        error("synthetic suspending close failure")
                    }
                    "close" -> {
                        fallbackCloseAttempts += 1
                        Unit
                    }
                    "logout" -> error("synthetic logout failure")
                    "stopSync" -> Unit
                    "toString" -> "RejectedUnstoppableMatrixClient"
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.firstOrNull()
                    else -> error("Unexpected MatrixClient call: ${method.name}")
                }
            } as MatrixClient
            val repository = TrixnityMatrixChatRepository(
                remote = object : MatrixSessionRemote {
                    override suspend fun create(roomId: String): NetworkAPIResult<MatrixSessionDto?> =
                        NetworkAPIResult.Success(validMatrixSession())
                },
                sessions = sessions,
                environment = ApiEnvironment("https://api.example.test/"),
                engine = MockEngine { respondOk() },
                platformStore = platformStore,
                lifetime = lifetime,
                matrixClientCreator = { rejectedClient },
            )
            runCurrent()
            val owner = MatrixChatRoomOwner()

            repository.openRoom(ROOM_ID, owner)
            advanceUntilIdle()

            assertIs<MatrixChatAvailability.Error>(repository.observeRoom(ROOM_ID).first().availability)
            assertEquals(listOf(1L to true), platformStore.accountDeleteAttempts)
            assertEquals(0, platformStore.scopeDeleteAttempts)
            assertTrue(suspendingCloseAttempts >= 1)
            assertTrue(fallbackCloseAttempts >= 1)

            repository.retryRoom(ROOM_ID, owner)
            advanceUntilIdle()
            assertEquals(
                listOf(1L to true),
                platformStore.accountDeleteAttempts,
                "Retry must not wipe a store whose rejected client may still be writing.",
            )
            assertEquals(0, platformStore.scopeDeleteAttempts)

            sessions.saveSession(
                ApiJson.encodeToString(Session(id = 2, access = "synthetic-2")),
                SESSION_KEY,
            )
            advanceUntilIdle()
            assertEquals(
                listOf(1L to true),
                platformStore.accountDeleteAttempts,
                "Account transition must preserve the blocked store until shutdown is confirmed.",
            )
            assertEquals(0, platformStore.scopeDeleteAttempts)
            assertTrue(1L in platformStore.cleanupMarkers)
            assertTrue(suspendingCloseAttempts >= 3)

            repository.close()
            lifetime.close()
            clientJob.cancel()
        }

    private fun coordinator(accountId: Long): SessionCoordinator =
        SessionCoordinator(MemoryStorage()).apply {
            saveSession(
                ApiJson.encodeToString(Session(id = accountId, access = "synthetic-$accountId")),
                SESSION_KEY,
            )
        }

    private class RecordingPlatformStore : MatrixPlatformStore {
        val accountDeleteAttempts = mutableListOf<Pair<Long, Boolean>>()
        val cleanupMarkers = mutableSetOf<Long>()
        var scopeDeleteAttempts = 0

        override fun deviceId(scope: MatrixStorageScope): String = DEVICE_ID

        override fun repositories(scope: MatrixStorageScope): RepositoriesModule =
            RepositoriesModule { module {} }

        override fun media(scope: MatrixStorageScope): MediaStoreModule =
            MediaStoreModule { module {} }

        override suspend fun deleteAccountData(scope: MatrixStorageScope) {
            scopeDeleteAttempts += 1
        }

        override suspend fun deleteAllAccountData(accountId: Long, preserving: MatrixStorageScope?) {
            accountDeleteAttempts += accountId to (preserving != null)
        }

        override fun recordAccountCleanupRequired(accountId: Long) {
            cleanupMarkers += accountId
        }

        override suspend fun reconcileAccountData(currentAccountId: Long?) = Unit

        override suspend fun rotateDeviceId(scope: MatrixStorageScope): String = DEVICE_ID
    }

    private class MemoryStorage : SessionStorage {
        private var value: String? = null

        override fun getSession(sessionKey: String) = value

        override fun saveSession(credentials: String, sessionKey: String) {
            value = credentials
        }

        override fun clearSession(): Boolean {
            value = null
            return true
        }

        override fun clearOnReinstall(key: String) = Unit
    }

    private fun validMatrixSession() = MatrixSessionDto(
        protocolId = "matrix-e2ee-v1",
        homeserverUrl = MATRIX_BASE_URL,
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
        const val ROOM_ID = "123e4567-e89b-42d3-a456-426614174000"
        const val MATRIX_BASE_URL = "https://matrix.example.test/"
        const val DEVICE_ID = "SRISU_TEST_DEVICE"
    }
}
