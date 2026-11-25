package com.srisu.srisu.features.chat

import com.srisu.srisu.features.chat.matrix.GatedMatrixOutboxEventHandler
import com.srisu.srisu.features.chat.matrix.MatrixOutboxGate
import com.srisu.srisu.features.chat.matrix.collectWithOutboxGateFailClosed
import com.srisu.srisu.features.chat.matrix.matrixOutboxAllowed
import de.connect2x.trixnity.client.room.OutboxMessageEventHandler
import de.connect2x.trixnity.clientserverapi.client.SyncState
import de.connect2x.trixnity.core.EventHandler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module

@OptIn(ExperimentalCoroutinesApi::class)
class MatrixOutboxGateTest {
    @Test
    fun durableSenderCannotRunBeforeGateAndIsCancelledWhenGateCloses() = runTest {
        val gate = MatrixOutboxGate()
        var starts = 0
        var cancellations = 0
        val delegate = object : EventHandler {
            override fun startInCoroutineScope(scope: CoroutineScope) {
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    starts += 1
                    try {
                        awaitCancellation()
                    } finally {
                        withContext(NonCancellable) {
                            delay(100)
                            cancellations += 1
                        }
                    }
                }
            }
        }
        val workerScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        GatedMatrixOutboxEventHandler(gate, delegate).startInCoroutineScope(workerScope)
        advanceUntilIdle()
        assertEquals(0, starts)

        gate.enable()
        advanceUntilIdle()
        assertEquals(1, starts)

        // Backgrounding, closing, or a security-gate loss takes this path.
        val disable = async { gate.disableAndJoin() }
        runCurrent()
        assertFalse(disable.isCompleted)
        assertEquals(0, cancellations)
        advanceTimeBy(100)
        disable.await()
        assertTrue(disable.isCompleted)
        assertEquals(1, cancellations)

        gate.enable()
        advanceUntilIdle()
        assertEquals(2, starts)
        gate.disableAndJoin()
        workerScope.cancel()
        advanceUntilIdle()
        assertEquals(2, cancellations)
    }

    @Test
    fun laterExactQualifierReplacesUpstreamOutboxHandler() {
        val upstream = object : EventHandler {}
        val gated = object : EventHandler {}
        val qualifier = named<OutboxMessageEventHandler>()
        val application = koinApplication {
            modules(
                module { single<EventHandler>(qualifier) { upstream } },
                module { single<EventHandler>(qualifier) { gated } },
            )
        }

        assertSame(gated, application.koin.getAll<EventHandler>().single())
        application.close()
    }

    @Test
    fun observationFailureAfterReadyStopsSenderBeforeReportingFailure() = runTest {
        val gate = MatrixOutboxGate()
        var starts = 0
        var cancellations = 0
        val delegate = object : EventHandler {
            override fun startInCoroutineScope(scope: CoroutineScope) {
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    starts += 1
                    try {
                        awaitCancellation()
                    } finally {
                        cancellations += 1
                    }
                }
            }
        }
        val workerScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        GatedMatrixOutboxEventHandler(gate, delegate).startInCoroutineScope(workerScope)
        var failureObservedAfterStop = false

        collectWithOutboxGateFailClosed(
            gate = gate,
            source = flow {
                emit(Unit)
                yield()
                error("Room observation failed")
            },
            onEach = { gate.enable() },
            onUnexpectedTermination = {
                failureObservedAfterStop = !gate.isEnabled() && cancellations == 1
            },
        )

        assertEquals(1, starts)
        assertEquals(1, cancellations)
        assertTrue(failureObservedAfterStop)
        workerScope.cancel()
    }

    @Test
    fun senderRunsOnlyWhileSecurityGateAndSyncAreBothCurrent() = runTest {
        val gate = MatrixOutboxGate()
        var starts = 0
        var cancellations = 0
        val delegate = object : EventHandler {
            override fun startInCoroutineScope(scope: CoroutineScope) {
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    starts += 1
                    try {
                        awaitCancellation()
                    } finally {
                        cancellations += 1
                    }
                }
            }
        }
        val workerScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        GatedMatrixOutboxEventHandler(gate, delegate).startInCoroutineScope(workerScope)
        val snapshots = mutableListOf<Triple<SyncState, Boolean, Int>>()

        collectWithOutboxGateFailClosed(
            gate = gate,
            source = flow {
                emit(SyncState.RUNNING)
                yield()
                emit(SyncState.ERROR)
                emit(SyncState.STOPPED)
            },
            onEach = { syncState ->
                if (matrixOutboxAllowed(secure = true, syncState = syncState, foreground = true)) gate.enable()
                else gate.disableAndJoin()
                snapshots += Triple(syncState, gate.isEnabled(), cancellations)
            },
            onUnexpectedTermination = {},
        )

        assertEquals(1, starts)
        assertEquals(
            listOf(
                Triple(SyncState.RUNNING, true, 0),
                Triple(SyncState.ERROR, false, 1),
                Triple(SyncState.STOPPED, false, 1),
            ),
            snapshots,
        )
        assertEquals(1, cancellations)
        workerScope.cancel()
    }
}
