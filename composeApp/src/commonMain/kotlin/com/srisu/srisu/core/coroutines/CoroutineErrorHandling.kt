package com.srisu.srisu.core.coroutines

import kotlinx.coroutines.CancellationException

/**
 * Broad exception handlers must never convert cooperative coroutine cancellation into an error.
 */
fun Throwable.rethrowIfCancellation() {
    if (this is CancellationException) throw this
}
