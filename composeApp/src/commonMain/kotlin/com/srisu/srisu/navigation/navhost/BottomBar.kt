package com.srisu.srisu.navigation.navhost

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.srisu.srisu.theme.pill
import com.srisu.srisu.theme.spacing
import com.srisu.srisu.theme.transparent

/** Shared Home chrome. Conversation/composer/viewer routes keep their full-screen layout. */
@Composable
fun AppBottomBar(
    onSelect: (BottomDestination) -> Unit,
    currentDestination: BottomDestination?,
    visible: Boolean
) {
    if (!visible) return
    val colors=MaterialTheme.colorScheme
    val space=MaterialTheme.spacing
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=space.gutter,vertical=space.small),
        contentAlignment=Alignment.Center) {
        Surface(modifier=Modifier.widthIn(max=480.dp).fillMaxWidth(),shape=MaterialTheme.shapes.pill,
            color=colors.surfaceContainer,tonalElevation=0.dp,shadowElevation=2.dp) {
            Row(Modifier.selectableGroup().padding(space.tiny).heightIn(min=space.touchTarget),
                verticalAlignment=Alignment.CenterVertically) {
                BottomDestination.entries.forEach { destination ->
                    val selected=currentDestination==destination
                    val tint=if(selected) colors.onSurface else colors.onSurfaceVariant
                    Column(Modifier.weight(1f).clip(MaterialTheme.shapes.pill)
                        .background(if(selected) colors.surface else colors.transparent)
                        .selectable(selected=selected,role=Role.Tab,onClick={if(!selected) onSelect(destination)})
                        .heightIn(min=space.touchTarget).padding(horizontal=space.small,vertical=space.tiny),
                        horizontalAlignment=Alignment.CenterHorizontally,
                        verticalArrangement=Arrangement.spacedBy(space.tiny,Alignment.CenterVertically)) {
                        Icon(destination.icon,null,Modifier.size(space.icon),tint=tint)
                        Text(destination.label,color=tint,style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
