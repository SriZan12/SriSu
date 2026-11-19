package com.srisu.srisu.notifications

import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicLong

internal interface FirebaseRegistrationClient {
    fun setAutoInit(enabled: Boolean)
    fun register(complete: (Boolean) -> Unit)
    fun unregister(complete: (Boolean) -> Unit)
}

/**
 * FCM's cached-FID check can block even inside setAutoInitEnabled(true).
 * Serialize SDK entry points off the UI thread; obsolete completions cannot
 * overwrite a later logout or registration attempt. No identifiers cross here.
 */
internal class FirebaseRegistrationRunner(
    private val work: Executor,
    private val main: Executor,
    private val client: () -> FirebaseRegistrationClient,
) {
    private val generation = AtomicLong()

    fun register(complete: (Boolean) -> Unit) = submit(true, complete)
    fun unregister(complete: (Boolean) -> Unit) = submit(false, complete)

    private fun submit(enabled: Boolean, complete: (Boolean) -> Unit) {
        val expected = generation.incrementAndGet()
        work.execute {
            if (generation.get() != expected) return@execute
            val finish: (Boolean) -> Unit = { success ->
                main.execute { if (generation.get() == expected) complete(success) }
            }
            try {
                val sdk = client()
                sdk.setAutoInit(enabled)
                if (generation.get() != expected) return@execute
                if (enabled) sdk.register(finish) else sdk.unregister(finish)
            } catch (_: Exception) {
                // Provider exceptions can contain identifiers; expose only success/failure.
                finish(false)
            }
        }
    }
}
