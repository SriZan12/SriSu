package com.srisu.srisu.features.plans.data

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class PlanAlarm(val id: String, val atMillis: Long, val url: String)
/** Platform operations only. Permission requests occur exclusively after a user tap. */
interface PlanDevice {
    fun permission(result: (String) -> Unit)
    fun requestPermission(result: (String) -> Unit)
    fun replaceReminders(alarms: List<PlanAlarm>, result: (String?) -> Unit)
    /** Reports the actual outcome; external editors must not claim an unconfirmed save. */
    fun calendar(title: String, atMillis: Long, place: String, result: (String) -> Unit)
    fun openPlace(place: String)
}
object NativePlanRuntime {
    var device: PlanDevice? = null
        private set
    fun install(value: PlanDevice) { device = value }
}
suspend fun PlanDevice.permissionStatus(request: Boolean = false): String = suspendCancellableCoroutine { c ->
    val done: (String)->Unit = { if(c.isActive) c.resume(it) }
    if(request) requestPermission(done) else permission(done)
}
suspend fun PlanDevice.replace(alarms: List<PlanAlarm>): String? = suspendCancellableCoroutine { c ->
    replaceReminders(alarms) { if(c.isActive) c.resume(it) }
}
