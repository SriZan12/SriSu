package com.srisu.srisu.features.sparks.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.features.moments.presentation.MomentLifecycle
import com.srisu.srisu.features.sparks.data.*
import com.srisu.srisu.theme.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.koin.compose.viewmodel.koinViewModel

class HomeSparksViewModel(private val source: SparkSource, private val sessions: SessionCoordinator) : ViewModel() {
    private val stamp = sessions.stamp()
    private val mutable = MutableStateFlow<SparkHome?>(null)
    val home = mutable.asStateFlow()
    private var load: Job? = null
    private var polling: Job? = null
    private var active = false
    init {
        viewModelScope.launch { source.changes.drop(1).collect { if (active) refresh() } }
        viewModelScope.launch { sessions.state.collect { if (it != stamp) { load?.cancel(); polling?.cancel(); mutable.value = null } } }
    }
    fun visible(value: Boolean) {
        active = value; polling?.cancel()
        if (value) {
            refresh()
            polling = viewModelScope.launch { while (isActive) { delay(15_000); refresh() } }
        }
    }
    private fun refresh() {
        if (load?.isActive == true || sessions.stamp() != stamp) return
        load = viewModelScope.launch {
            try { val home = source.home().required(); sessions.ensureCurrent(stamp); mutable.value = home }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.value = null }
        }
    }
}
@Composable internal fun HomeSparks(onOpen: (String?) -> Unit, vm: HomeSparksViewModel = koinViewModel()) {
    val home by vm.home.collectAsStateWithLifecycle()
    MomentLifecycle(vm::visible)
    val latest = home?.active ?: home?.recent?.firstOrNull { it.revealed }
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Sparks", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { onOpen(null) }) { Text("Show more") }
        }
        HomeSparkCard(latest, { onOpen(latest?.id) })
    }
}
@Composable internal fun HomeSparkCard(active: SparkSession?, onOpen: () -> Unit) {
    Surface(onClick = onOpen, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.large), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.spark)
                Text(if (active == null) "Sparks" else if (active.revealed) "Your latest Spark" else "Continue your Spark", style = MaterialTheme.typography.titleMedium)
            }
            Text(active?.question ?: "One question. Two perspectives. A little closer.", style = if (active == null) MaterialTheme.typography.bodyLarge else SriSuPartnerLinkTypography().headlineMedium)
            Text(if (active == null) "Deep · Memories · Playful · Future" else if (active.revealed) "Both answers are open" else "Open your private question", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
