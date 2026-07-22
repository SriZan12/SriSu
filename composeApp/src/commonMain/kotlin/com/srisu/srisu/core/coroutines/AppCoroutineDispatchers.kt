package com.srisu.srisu.core.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.coroutines.CoroutineContext

/**
 * Injectable dispatchers keep thread selection at the infrastructure boundary and make tests
 * deterministic. Suspending data APIs remain responsible for being main-safe.
 */
class AppCoroutineDispatchers(
    val io: CoroutineDispatcher = Dispatchers.IO,
    val default: CoroutineDispatcher = Dispatchers.Default,
    val main: CoroutineDispatcher = Dispatchers.Main,
)

/**
 * Process-level scope for work that intentionally outlives a screen.
 * Screen-specific work must remain a child of viewModelScope.
 */
class ApplicationCoroutineScope(
    dispatcher: CoroutineDispatcher,
) : CoroutineScope {
    override val coroutineContext: CoroutineContext = SupervisorJob() + dispatcher

    fun close() {
        cancel()
    }
}
