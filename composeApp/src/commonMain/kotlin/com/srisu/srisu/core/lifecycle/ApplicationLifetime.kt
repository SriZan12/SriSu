package com.srisu.srisu.core.lifecycle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Owned by the Koin application; feature/session jobs must be children of this scope. */
class ApplicationLifetime {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _foreground = MutableStateFlow(false)
    val foreground = _foreground.asStateFlow()
    fun setForeground(value: Boolean) { _foreground.value = value }
    fun close() { scope.cancel() }
}
