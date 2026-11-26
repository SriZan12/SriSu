package com.srisu.srisu.features.explore.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srisu.srisu.features.explore.data.ExploreCouple
import com.srisu.srisu.features.moments.presentation.MomentLifecycle
import org.koin.compose.viewmodel.koinViewModel

/** Public follow never shares identity until the distinct confirmation is accepted. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ExplorePublicFollow(coupleId:Long,vm:PublicFollowViewModel=koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var expanded by remember(coupleId) {mutableStateOf(false)}
    var confirm by remember(coupleId) {mutableStateOf(false)}
    LaunchedEffect(coupleId) {vm.enter(coupleId)}
    MomentLifecycle(vm::active)
    TextButton(onClick={expanded=true;vm.refresh()},modifier=Modifier.fillMaxWidth()) {
        Text(if(state.snapshot?.isFollowing==true) "Following · manage" else "Public follow options")
    }
    if(expanded) ModalBottomSheet(onDismissRequest={expanded=false},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        PublicFollowActions(ExploreCouple(coupleId,displayName="Follow this couple"),state,
            {desired->if(desired) {expanded=false;confirm=true} else vm.follow(false)},vm::refresh)
    }
    if(confirm) PublicFollowConfirmation("this couple",{confirm=false},{confirm=false;expanded=true;vm.follow(true,shareIdentity=true)})
}


@Composable fun PublicMomentActions(coupleId:Long) {
    val source=org.koin.compose.koinInject<com.srisu.srisu.features.explore.data.ExploreSource>()
    val store=org.koin.compose.koinInject<com.srisu.srisu.features.explore.data.FaveStore>()
    val sessions=org.koin.compose.koinInject<com.srisu.srisu.core.session.SessionCoordinator>()
    val preferences by store.state.collectAsStateWithLifecycle()
    var snapshot by remember(coupleId) {mutableStateOf<com.srisu.srisu.features.explore.data.FaveSnapshot?>(null)}
    var failed by remember(coupleId) {mutableStateOf(false)}
    var retry by remember(coupleId) {mutableIntStateOf(0)}
    LaunchedEffect(coupleId,retry) {
        val stamp=sessions.stamp()
        failed=false
        val result=source.faveState(coupleId)
        sessions.ensureCurrent(stamp)
        snapshot=(result as? com.srisu.srisu.core.data.remote.NetworkAPIResult.Success)?.response
        snapshot?.let {store.accept(coupleId,it.isFaved,it.version)}
        failed=snapshot==null
    }
    Column(horizontalAlignment=androidx.compose.ui.Alignment.CenterHorizontally) {
        snapshot?.takeIf {it.available}?.let {FaveControl(coupleId,it.isFaved,preferences[coupleId],store::set,store::retry)}
        if(failed) TextButton(onClick={retry++}) {Text("Retry Fave status")}
        ExplorePublicFollow(coupleId)
    }
}
