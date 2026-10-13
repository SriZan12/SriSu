package com.srisu.srisu.features.notifications

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Only fixed diagnostic codes cross the bridge. No Throwable, user ID, text or URL API exists. */
enum class DiagnosticCode { NOTIFICATION_CONTRACT, CHAT_STORAGE, NATIVE_BRIDGE, TEST_NONFATAL }
interface NotificationPlatform {
    fun refresh()
    fun requestPermission()
    fun openSettings()
    fun registration(binding: String): InstallationRequest?
    fun renewRegistration()
    fun clearAccount()
    fun setDiagnostics(enabled: Boolean)
    fun report(code: DiagnosticCode)
    fun dismissPrompt()
}
data class NativeNotificationState(val configured: Boolean = false, val permission: String = "unavailable", val diagnostics: Boolean = false, val promptDismissed: Boolean = false, val revision: Long = 0)
data class NotificationSignal(val deliveryId: String, val expiresAt: Long, val tapped: Boolean)

/** Platform delegates call on the UI thread. Pending taps contain only opaque, bounded identifiers. */
object NativeNotifications {
    var driver: NotificationPlatform? = null
        private set
    private val mutableState = MutableStateFlow(NativeNotificationState())
    val state = mutableState.asStateFlow()
    private val mutableSignal = MutableStateFlow<NotificationSignal?>(null)
    val signal = mutableSignal.asStateFlow()
    private val tapped = linkedSetOf<String>()
    private val shown = linkedSetOf<String>()
    fun install(value: NotificationPlatform) { driver = value; value.refresh() }
    fun update(configured: Boolean, permission: String, diagnostics: Boolean, promptDismissed: Boolean) {
        mutableState.value = NativeNotificationState(configured, permission, diagnostics, promptDismissed, mutableState.value.revision + 1)
    }
    @OptIn(ExperimentalTime::class)
    fun receive(schema: String?, deliveryId: String?, expiresAt: String?, tapped: Boolean) {
        val expiry = expiresAt?.toLongOrNull() ?: return
        if (schema != "1" || !validNotificationId(deliveryId) || expiry <= Clock.System.now().epochSeconds) return
        if (deliveryId in if (tapped) this.tapped else shown) return
        mutableSignal.value = NotificationSignal(deliveryId!!, expiry, tapped)
    }
    fun consume(value: NotificationSignal) {
        val seen = if(value.tapped) tapped else shown
        seen.add(value.deliveryId)
        if(seen.size > 64) seen.remove(seen.first())
        if (mutableSignal.value == value) mutableSignal.value = null
    }
    fun clear() { mutableSignal.value = null; tapped.clear(); shown.clear(); driver?.clearAccount() }
}
