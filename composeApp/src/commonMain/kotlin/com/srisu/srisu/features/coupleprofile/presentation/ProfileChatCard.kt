package com.srisu.srisu.features.coupleprofile.presentation

import androidx.compose.runtime.*
import androidx.compose.material3.*
import com.srisu.srisu.features.coupleprofile.data.ProfileChatAction
import org.jetbrains.compose.resources.stringResource
import srisu.composeapp.generated.resources.*

val LocalProfileNavigation = staticCompositionLocalOf<((ProfileChatAction) -> Unit)?> { null }
@Composable fun ProfileChatActionButton(action: ProfileChatAction) {
    val navigate = LocalProfileNavigation.current
    if(navigate!=null && action.kind in setOf("story","plan")) TextButton(onClick={navigate(action)}) {
        Text(stringResource(if(action.kind=="story") Res.string.cp_answer else Res.string.cp_view_plan))
    }
}
