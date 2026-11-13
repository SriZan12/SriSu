package com.srisu.srisu.notifications

import java.util.concurrent.Executor
import org.junit.Assert.*
import org.junit.Test

class FirebaseRegistrationRunnerTest {
    private class Queue : Executor {
        val tasks = ArrayDeque<Runnable>()
        var running = false
        override fun execute(command: Runnable) { tasks.addLast(command) }
        fun drain() {
            running = true
            try { while (tasks.isNotEmpty()) tasks.removeFirst().run() } finally { running = false }
        }
    }
    private class Fixture {
        val work = Queue()
        val main = Queue()
        val calls = mutableListOf<String>()
        val pending = mutableListOf<(Boolean) -> Unit>()
        var failEnable = false
        var failRegister = false
        var failUnregister = false
        var duringEnable: (() -> Unit)? = null
        val runner = FirebaseRegistrationRunner(work, main) {
            object : FirebaseRegistrationClient {
                override fun setAutoInit(enabled: Boolean) {
                    check(work.running && !main.running) { "Cached-FID access must run off main" }
                    calls += "auto:$enabled"
                    if (enabled && failEnable) error("synthetic provider failure")
                    if (enabled) duringEnable?.invoke()
                }
                override fun register(complete: (Boolean) -> Unit) {
                    calls += "register"
                    if (failRegister) error("synthetic registration failure")
                    pending += complete
                }
                override fun unregister(complete: (Boolean) -> Unit) {
                    calls += "unregister"
                    if (failUnregister) error("synthetic unregistration failure")
                    pending += complete
                }
            }
        }
    }

    @Test fun sdkWorkRunsOffMainAndCompletionReturnsToMain() {
        val f = Fixture();var success: Boolean? = null
        f.runner.register { assertTrue(f.main.running);success = it }
        assertTrue(f.calls.isEmpty())
        f.work.drain();assertEquals(listOf("auto:true", "register"), f.calls)
        f.pending.single()(true);assertNull(success)
        f.main.drain();assertEquals(true, success)
    }

    @Test fun synchronousProviderFailureFinishesAndCanRetry() {
        val f = Fixture();val results = mutableListOf<Boolean>()
        f.failEnable = true
        f.runner.register { results += it };f.work.drain();f.main.drain()
        assertEquals(listOf(false), results)
        f.failEnable = false
        f.runner.register { results += it };f.work.drain();f.pending.single()(true);f.main.drain()
        assertEquals(listOf(false, true), results)
    }

    @Test fun synchronousRegisterFailureFinishesAfterAutoInitSucceeds() {
        val f = Fixture()
        var success: Boolean? = null
        f.failRegister = true
        f.runner.register { success = it }
        f.work.drain()
        f.main.drain()
        assertEquals(listOf("auto:true", "register"), f.calls)
        assertEquals(false, success)
    }

    @Test fun failedUnregisterStillFinishesOnMain() {
        val f = Fixture()
        var success: Boolean? = null
        f.failUnregister = true
        f.runner.unregister { assertTrue(f.main.running); success = it }
        f.work.drain()
        f.main.drain()
        assertEquals(listOf("auto:false", "unregister"), f.calls)
        assertEquals(false, success)
    }

    @Test fun asynchronousFailureFinishesAndAllowsAnotherRegistration() {
        val f = Fixture()
        val results = mutableListOf<Boolean>()
        f.runner.register { results += it }
        f.work.drain()
        f.pending[0](false)
        f.main.drain()
        f.runner.register { results += it }
        f.work.drain()
        f.pending[1](true)
        f.main.drain()
        assertEquals(listOf(false, true), results)
    }

    @Test fun logoutBeforeQueuedRegistrationNeverEnablesIt() {
        val f = Fixture();var loggedOut = false
        f.runner.register { fail("Obsolete registration completion") }
        f.runner.unregister { loggedOut = true };f.work.drain()
        assertEquals(listOf("auto:false", "unregister"), f.calls)
        f.pending.single()(true);f.main.drain();assertTrue(loggedOut)
    }

    @Test fun logoutDuringEnableRunsLastAndPreventsRegistration() {
        val f = Fixture()
        f.duringEnable = { f.runner.unregister {} }
        f.runner.register { fail("Obsolete registration completion") };f.work.drain()
        assertEquals(listOf("auto:true", "auto:false", "unregister"), f.calls)
    }

    @Test fun oldCompletionCannotReplaceLogoutOrTheNextLogin() {
        val f = Fixture();val results = mutableListOf<String>()
        f.runner.register { results += "old" };f.work.drain()
        f.pending[0](true) // Completion already queued on main when logout occurs.
        f.runner.unregister { results += "logout" };f.work.drain();f.main.drain()
        assertTrue(results.isEmpty())
        f.pending[1](true);f.main.drain();assertEquals(listOf("logout"), results)
        f.runner.register { results += "new" };f.work.drain()
        f.pending[0](true);f.pending[2](true);f.main.drain()
        assertEquals(listOf("logout", "new"), results)
    }
}
