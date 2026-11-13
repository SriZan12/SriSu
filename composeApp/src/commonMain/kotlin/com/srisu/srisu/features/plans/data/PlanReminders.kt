package com.srisu.srisu.features.plans.data

import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.core.lifecycle.ApplicationLifetime
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock

@Serializable data class ReminderChoice(val plan: Long, val couple: Long, val minutes: Int)
data class ReminderState(val choices: List<ReminderChoice> = emptyList(), val permission: String = "unknown",
    val scheduled: Set<Long> = emptySet(), val error: String? = null)
/** One application owner. A full authorized snapshot removes cancelled/expired/foreign reminders. */
class PlanReminders(private val source: PlanSource, private val sessions: SessionCoordinator,
    private val environment: ApiEnvironment, private val device: PlanDevice, lifetime: ApplicationLifetime) {
    private val scope = lifetime.scope
    private val lock = Mutex()
    private val mutable = MutableStateFlow(ReminderState())
    val state = mutable.asStateFlow()
    private var current = sessions.stamp()
    private fun key() = "plan-reminders:${current.accountId}:${environment.baseUrl}"
    init {
        scope.launch { sessions.state.collectLatest { stamp ->
            lock.withLock {
                if (stamp != current || stamp.accountId == null) { withContext(NonCancellable) {device.replace(emptyList())}; mutable.value = ReminderState(); current = stamp }
                if (stamp.accountId != null) mutable.value = state.value.copy(choices = runCatching {
                    Json.decodeFromString<List<ReminderChoice>>(sessions.getSession(key()).orEmpty())
                }.getOrDefault(emptyList()))
            }
            if(stamp.accountId != null) refresh()
        } }
        scope.launch { combine(lifetime.foreground,source.changes) { visible,_ -> visible }.collectLatest { visible ->
            if(visible) while(isActive) { refresh(); delay(15_000) }
        } }
    }
    private fun save(choices: List<ReminderChoice>) {
        sessions.ensureCurrent(current)
        sessions.saveSession(Json.encodeToString(choices),key())
        mutable.value = state.value.copy(choices=choices)
    }
    suspend fun choose(plan: Plan, minutes: Int?) = lock.withLock {
        if(sessions.stamp()!=current || current.accountId==null) return@withLock
        if(minutes != null && (!plan.allows("cancel",current.accountId) || plan.response!="yes" || plan.instant<=Clock.System.now())) return@withLock
        if(minutes != null && minutes !in listOf(15,60,1440)) return@withLock
        val choices = state.value.choices.filterNot { it.plan==plan.id }
        if(minutes!=null && choices.size>=32) { mutable.value=state.value.copy(error="This phone can remind you about up to 32 plans."); return@withLock }
        if(minutes!=null) {
            val permission=device.permissionStatus(true); sessions.ensureCurrent(current)
            mutable.value=state.value.copy(permission=permission)
            if(permission!="granted") { mutable.value=state.value.copy(error="Notifications are disabled. Enable them in this phone’s settings."); return@withLock }
        }
        save(if(minutes==null) choices else choices+ReminderChoice(plan.id,plan.couple,minutes))
        reconcile()
    }
    suspend fun refresh() = lock.withLock {
        if(current.accountId != null && sessions.stamp()==current) reconcile()
    }
    private suspend fun reconcile() {
        val stamp=current
        try {
            val permission=device.permissionStatus(); sessions.ensureCurrent(stamp)
            val choices=state.value.choices
            val plans=if(choices.isEmpty()) emptyList() else source.reminders(choices.map { it.plan }).planValue().items
            sessions.ensureCurrent(stamp)
            val valid=choices.filter { choice -> plans.any { it.id==choice.plan && it.couple==choice.couple } }
            save(valid)
            val alarms=if(permission=="granted") reminderAlarms(plans,valid) else emptyList()
            // Finish platform replacement before logout can acquire the lock and clear it.
            val error=withContext(NonCancellable) { device.replace(alarms) }
            sessions.ensureCurrent(stamp)
            mutable.value=state.value.copy(permission=permission,scheduled=if(error==null) valid.filter { c -> alarms.any { it.id=="plan-${c.plan}" && it.atMillis>Clock.System.now().toEpochMilliseconds() } }.map { it.plan }.toSet() else emptySet(),error=error)
        } catch(cancelled: CancellationException) { throw cancelled }
        catch(error: Exception) {
            if(sessions.stamp()!=stamp) return
            if((error as? PlanFailure)?.status in listOf(401,403,404)) { device.replace(emptyList()); save(emptyList()); mutable.value=state.value.copy(scheduled=emptySet()) }
            mutable.value=state.value.copy(error="Reminders couldn’t sync. Open SriSu online to update this phone.")
        }
    }
}
fun reminderAlarms(plans: List<Plan>, choices: List<ReminderChoice>): List<PlanAlarm> = choices.mapNotNull { choice ->
    val plan=plans.firstOrNull { it.id==choice.plan && it.couple==choice.couple && !it.ended && it.response=="yes" } ?: return@mapNotNull null
    val at=plan.instant.toEpochMilliseconds()-choice.minutes*60_000L
    // Keep delivered reminder identities until cancellation, logout or the plan ends.
    PlanAlarm("plan-${plan.id}",at,"srisu://plans/${plan.couple}/${plan.id}")
}
