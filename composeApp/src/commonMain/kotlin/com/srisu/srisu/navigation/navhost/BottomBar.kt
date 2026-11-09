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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.srisu.srisu.theme.HomeSpace
import com.srisu.srisu.theme.pill
import com.srisu.srisu.theme.spacing
import com.srisu.srisu.theme.transparent

/** Full labels remain readable at large text; full-screen feature routes have no bottom bar. */
@Composable fun AppBottomBar(onSelect: (BottomDestination) -> Unit, currentDestination: BottomDestination?, visible: Boolean) {
    if (!visible) return
    val space = MaterialTheme.spacing
    val large = LocalDensity.current.fontScale > 1.3f
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = if (large) space.small else space.gutter, vertical = space.small),
        contentAlignment = Alignment.Center) {
        Surface(Modifier.widthIn(max = 480.dp).fillMaxWidth(), shape = if (large) MaterialTheme.shapes.large else MaterialTheme.shapes.pill,
            color = HomeSpace.paper, tonalElevation = 0.dp, shadowElevation = 5.dp) {
            Column(Modifier.selectableGroup().padding(space.tiny)) {
                BottomDestination.entries.chunked(if (large) 2 else 4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        row.forEach { destination -> BottomItem(destination, destination == currentDestination, large, Modifier.weight(1f)) { onSelect(destination) } }
                    }
                }
            }
        }
    }
}
@Composable private fun BottomItem(destination: BottomDestination, selected: Boolean, large: Boolean, modifier: Modifier, click: () -> Unit) {
    val space = MaterialTheme.spacing
    val tint = if (selected) HomeSpace.ink else HomeSpace.muted
    val item = modifier.clip(MaterialTheme.shapes.pill).background(if (selected) HomeSpace.warm else MaterialTheme.colorScheme.transparent)
        .selectable(selected, role = Role.Tab, onClick = { if (!selected) click() }).heightIn(min = space.touchTarget)
        .padding(horizontal = space.tiny, vertical = space.small)
    if (large) Row(item, horizontalArrangement = Arrangement.spacedBy(space.tiny, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        Icon(destination.icon, null, Modifier.size(space.icon), tint)
        Text(destination.label, color = tint, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f, false), textAlign = TextAlign.Center)
    } else Column(item, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)) {
        Icon(destination.icon, null, Modifier.size(space.icon), tint)
        Text(destination.label, color = tint, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
}
