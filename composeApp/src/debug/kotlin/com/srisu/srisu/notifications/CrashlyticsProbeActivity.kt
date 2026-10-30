package com.srisu.srisu.notifications

/** Exists only in Debug. Run with explicit synthetic test intent after opting into diagnostics. */
class CrashlyticsProbeActivity : android.app.Activity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        if(!com.srisu.srisu.features.notifications.NativeNotifications.state.value.diagnostics) { finish();return }
        if(intent.getBooleanExtra("fatal",false)) error("SriSu controlled test crash")
        com.srisu.srisu.features.notifications.CrashReporter.report(com.srisu.srisu.features.notifications.DiagnosticCode.TEST_NONFATAL)
        finish()
    }
}
