package com.srisu.srisu.notifications

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.PersistableBundle

internal enum class FirebaseIdState {
    HIDDEN, NOT_CONFIGURED, SIGN_IN_REQUIRED, PERMISSION_REQUIRED, REGISTERING, WAITING, FAILED, READY,
}

/** Availability only. Never put a provider identifier into a data class, logs or Compose semantics. */
internal data class FirebaseIdAccess(
    val debugBuild: Boolean,
    val configured: Boolean,
    val signedIn: Boolean,
    val permissionGranted: Boolean,
    val unregistering: Boolean = false,
    val registering: Boolean = false,
    val registrationFailed: Boolean = false,
    val hasId: Boolean = false,
) {
    val state: FirebaseIdState get() = when {
        !debugBuild -> FirebaseIdState.HIDDEN
        !configured -> FirebaseIdState.NOT_CONFIGURED
        !signedIn || unregistering -> FirebaseIdState.SIGN_IN_REQUIRED
        !permissionGranted -> FirebaseIdState.PERMISSION_REQUIRED
        hasId -> FirebaseIdState.READY
        registering -> FirebaseIdState.REGISTERING
        registrationFailed -> FirebaseIdState.FAILED
        else -> FirebaseIdState.WAITING
    }
}

internal fun isNotificationDebugBuild(context: Context): Boolean =
    context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

internal fun copyFirebaseRegistrationId(context: Context, access: FirebaseIdAccess, id: String?): Boolean {
    // Recheck the installed build too, even if a caller supplied an old availability snapshot.
    if (!isNotificationDebugBuild(context) || access.state != FirebaseIdState.READY || id.isNullOrBlank()) return false
    return try {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val clip = ClipData.newPlainText("SriSu Firebase registration ID", id)
        clip.description.extras = PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
        clipboard.setPrimaryClip(clip)
        true
    } catch (_: Exception) {
        // Clipboard/provider exceptions must not become telemetry with a registration target.
        false
    }
}
