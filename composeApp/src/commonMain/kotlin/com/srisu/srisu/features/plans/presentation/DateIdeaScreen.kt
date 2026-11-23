package com.srisu.srisu.features.plans.presentation

import com.srisu.srisu.components.NavigationBackButton
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.features.plans.data.*
import com.srisu.srisu.features.moments.presentation.MomentLifecycle
import com.srisu.srisu.theme.spacing
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.koin.compose.viewmodel.koinViewModel

data class DateIdeaDetailState(val idea: DateIdea?=null, val loading: Boolean=false, val error: String?=null, val unavailable: Boolean=false)
class DateIdeaViewModel(private val source: DateIdeaSource, private val sessions: SessionCoordinator): ViewModel() {
    private val stamp=sessions.stamp()
    private val mutable=MutableStateFlow(DateIdeaDetailState())
    val state=mutable.asStateFlow()
    private var work: Job?=null
    private var active=false
    private var id: String?=null
    init { viewModelScope.launch { sessions.state.collect { if(it!=stamp) {
        work?.cancel(); mutable.value=DateIdeaDetailState(unavailable=true)
    } } } }
    fun open(value: String) { if(id!=value) { id=value; if(active) refresh() } }
    fun active(value: Boolean) { active=value; if(value) refresh() else { work?.cancel(); mutable.value=DateIdeaDetailState() } }
    fun refresh() {
        val key=id ?: return
        if(!active || sessions.stamp()!=stamp) return
        work?.cancel(); work=viewModelScope.launch {
            mutable.value=DateIdeaDetailState(loading=true)
            val result=source.get(key)
            if(!active || sessions.stamp()!=stamp) return@launch
            val idea=(result as? NetworkAPIResult.Success)?.response
            val denied=(result as? NetworkAPIResult.Error)?.failure?.status in listOf(401,403,404,410)
            mutable.value=DateIdeaDetailState(idea=idea?.takeIf { it.state=="open" },
                unavailable=denied || (idea!=null && idea.state!="open"),
                error=if(idea==null && !denied) "Couldn’t load this idea. Try again." else null)
        }
    }
}

@Composable fun DateIdeaScreen(id: String, onBack: () -> Unit, onHome: () -> Unit, onPlan: (String) -> Unit,
    vm: DateIdeaViewModel=koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(id) { vm.open(id) }
    MomentLifecycle(vm::active)
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(MaterialTheme.spacing.gutter),
        verticalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
        NavigationBackButton(onBack)
        Text("A shared idea",style=MaterialTheme.typography.headlineSmall)
        if(state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(state.unavailable) Text("This idea is no longer available.")
        state.error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        TextButton(onClick=vm::refresh,enabled=!state.loading) { Text("Refresh") }
        state.idea?.let { idea ->
            Text(idea.title,style=MaterialTheme.typography.titleLarge)
            Text(if(idea.interestedBy.size==2) "You’re both interested. Choose a time when you’re ready." else "One of you has suggested this. Open your shared space to respond.")
            Text("This is an idea, not a booking or an agreed Plan.")
            if(idea.interestedBy.size==2) Button(onClick={ onPlan(idea.id) }) { Text("Choose a time") }
        }
        TextButton(onClick=onHome) { Text("Open shared space") }
    }
}
