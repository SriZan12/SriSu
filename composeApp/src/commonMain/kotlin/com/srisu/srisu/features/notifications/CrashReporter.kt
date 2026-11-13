package com.srisu.srisu.features.notifications

object CrashReporter {
    val environment: String get() = com.srisu.srisu.core.config.BuildEnvironment.name
    private val reported = mutableSetOf<DiagnosticCode>()
    fun report(code: DiagnosticCode) {
        if (NativeNotifications.state.value.diagnostics && reported.add(code)) NativeNotifications.driver?.report(code)
    }
}
