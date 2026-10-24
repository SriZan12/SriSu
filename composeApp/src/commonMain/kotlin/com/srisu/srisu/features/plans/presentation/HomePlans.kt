package com.srisu.srisu.features.plans.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.features.plans.data.*
import com.srisu.srisu.features.moments.presentation.MomentLifecycle
import com.srisu.srisu.theme.spacing
import com.srisu.srisu.theme.planner
import androidx.compose.material.icons.outlined.CalendarMonth
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.koin.compose.viewmodel.koinViewModel

data class HomePlanState(val home: PlanHome? = null, val loading: Boolean = true, val failed: Boolean = false)
class HomePlansViewModel(private val source: PlanSource, private val sessions: SessionCoordinator) : ViewModel() {
    private val stamp = sessions.stamp()
    private val mutable = MutableStateFlow(HomePlanState())
    val state = mutable.asStateFlow()
    private var poll: Job? = null
    private var work: Job? = null
    private var active = false
    init {
        viewModelScope.launch { source.changes.drop(1).collect { if (active) refresh() } }
        viewModelScope.launch { sessions.state.collect { if (it != stamp) { poll?.cancel(); work?.cancel(); mutable.value = HomePlanState(loading = false) } } }
    }
    fun visible(value: Boolean) {
        active = value; poll?.cancel()
        if (value) { refresh(); poll = viewModelScope.launch { while (isActive) { delay(15_000); refresh() } } }
    }
    fun refresh() {
        if (work?.isActive == true || sessions.stamp() != stamp) return
        work = viewModelScope.launch {
            try { val home = source.home().planValue(); sessions.ensureCurrent(stamp); mutable.value = HomePlanState(home, loading = false) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (sessions.stamp() == stamp) mutable.value = HomePlanState(loading = false, failed = true) }
        }
    }
}
@Composable internal fun HomePlans(onOpen: (Long?) -> Unit, vm: HomePlansViewModel = koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    MomentLifecycle(vm::visible)
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        Row(Modifier.fillMaxWidth()) {
            Text("Coming up", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(top = MaterialTheme.spacing.compact))
            TextButton(onClick = { onOpen(null) }) { Text("See all") }
        }
        if ((state.home?.invitations ?: 0) > 0) TextButton(onClick = { onOpen(null) }) { Text("${state.home?.invitations} invitation or change${if(state.home?.invitations == 1) "" else "s"} to review") }
        val current = state.home?.nextPlan
        if (current != null) PlanSummary(current, { onOpen(current.id) })
        else Surface(onClick = { if (state.failed) vm.refresh() else onOpen(null) }, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.medium), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth()) else Icon(androidx.compose.material.icons.Icons.Outlined.CalendarMonth, null, tint = MaterialTheme.colorScheme.planner)
                Text(if (state.failed) "Plans couldn’t refresh" else "Something to look forward to", style = MaterialTheme.typography.titleMedium)
                Text(if (state.failed) "Tap to try again" else "Plan a little time for the two of you.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
