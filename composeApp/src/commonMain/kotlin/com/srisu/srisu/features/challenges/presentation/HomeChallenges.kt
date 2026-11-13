package com.srisu.srisu.features.challenges.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.features.challenges.data.*
import com.srisu.srisu.features.moments.presentation.MomentLifecycle
import com.srisu.srisu.theme.spacing
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.koin.compose.viewmodel.koinViewModel

data class HomeChallengeState(val home: ChallengeHome? = null, val loading: Boolean = true, val failed: Boolean = false)
class HomeChallengesViewModel(private val source: ChallengeSource, private val sessions: SessionCoordinator) : ViewModel() {
    private val stamp = sessions.stamp()
    private val mutable = MutableStateFlow(HomeChallengeState())
    val state = mutable.asStateFlow()
    private var poll: Job? = null
    private var work: Job? = null
    private var active = false
    init {
        viewModelScope.launch { source.changes.drop(1).collect { if (active) refresh() } }
        viewModelScope.launch { sessions.state.collect { if (it != stamp) { poll?.cancel(); work?.cancel(); mutable.value = HomeChallengeState(loading = false) } } }
    }
    fun visible(value: Boolean) {
        active = value; poll?.cancel()
        if (value) { refresh(); poll = viewModelScope.launch { while (isActive) { delay(15_000); refresh() } } }
    }
    fun refresh() {
        if (work?.isActive == true || sessions.stamp() != stamp) return
        work = viewModelScope.launch {
            try { val home = source.home().challengeValue(); sessions.ensureCurrent(stamp); mutable.value = HomeChallengeState(home, loading = false) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (sessions.stamp() == stamp) mutable.value = HomeChallengeState(loading = false, failed = true) }
        }
    }
}
@Composable internal fun HomeChallenges(onOpen: (String?) -> Unit, vm: HomeChallengesViewModel = koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    MomentLifecycle(vm::visible)
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        Row(Modifier.fillMaxWidth()) {
            Text("Your challenge", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(top = MaterialTheme.spacing.compact))
            TextButton(onClick = { onOpen(null) }) { Text("Browse") }
        }
        val current = state.home?.current
        if (current != null) ChallengeSummaryCard(current, state.home?.members.orEmpty(), { onOpen(current.id) })
        else Surface(onClick = { if (state.failed) vm.refresh() else onOpen(null) }, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.medium), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth()) else ChallengeMark()
                Text(if (state.failed) "Challenges couldn’t refresh" else "A little experience, together", style = MaterialTheme.typography.titleMedium)
                Text(if (state.failed) "Tap to try again" else "Find something thoughtful, playful, or new.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
