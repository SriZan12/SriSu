@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.srisu.srisu.features.couplechat.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.srisu.srisu.features.couplechat.data.*
import com.srisu.srisu.theme.*

@Composable internal fun ChatAppearanceSheet(saved: String, onApply: (String) -> Unit, onDismiss: () -> Unit) {
    var selection by rememberSaveable { mutableStateOf(saved) }
    ModalBottomSheet(onDismissRequest=onDismiss, sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.92f).navigationBarsPadding()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal=MaterialTheme.spacing.gutter), verticalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
                Text("Chat appearance", style=MaterialTheme.typography.titleLarge)
                Text("Only changes your view. Follows your device’s light or dark appearance.", style=MaterialTheme.typography.bodyMedium,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                ChatThemePreset.entries.sortedBy { it != ChatThemePreset.DEFAULT }.forEach { preset ->
                    SriSuChatTheme(preset) {
                        OutlinedCard(onClick={selection=preset.id}, modifier=Modifier.fillMaxWidth().semantics {
                            selected=ChatThemePreset.fromId(selection)==preset; contentDescription=preset.label
                        }, colors=CardDefaults.outlinedCardColors(containerColor=MaterialTheme.colorScheme.background)) {
                            Column(Modifier.padding(MaterialTheme.spacing.medium), verticalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                                Row(verticalAlignment=Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(preset.label, style=MaterialTheme.typography.titleMedium)
                                        if(preset==ChatThemePreset.DEFAULT) Text("Default",style=MaterialTheme.typography.labelSmall,
                                            color=MaterialTheme.colorScheme.primary)
                                        Text(preset.description, style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if(ChatThemePreset.fromId(selection)==preset) Icon(Icons.Outlined.CheckCircle,"Selected theme",tint=MaterialTheme.colorScheme.secondary)
                                }
                                ChatThemePreview()
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.medium), horizontalArrangement=Arrangement.End) {
                TextButton(onClick=onDismiss) {Text("Cancel")}
                Spacer(Modifier.width(MaterialTheme.spacing.small))
                Button(onClick={onApply(selection)}, enabled=selection!=saved) {Text("Apply theme")}
            }
        }
    }
}

/** Miniature example only; never inserted into repository state. */
@Composable internal fun ChatThemePreview() {
    Column(Modifier.fillMaxWidth().clearAndSetSemantics {}, verticalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        Surface(shape=MaterialTheme.shapes.medium, color=MaterialTheme.chatColors.incoming) {
            Text("A little time together?",Modifier.padding(MaterialTheme.spacing.small),style=MaterialTheme.typography.bodySmall)
        }
        Surface(Modifier.align(Alignment.End).fillMaxWidth(.82f),shape=MaterialTheme.shapes.medium) {
            Column(Modifier.background(MaterialTheme.chatColors.outgoing).padding(MaterialTheme.spacing.small)) {
                QuotePreview(ChatQuote("preview",2,"A little time together?"),1,"Partner")
                Text("Always. See you soon.",style=MaterialTheme.typography.bodySmall)
                MessageMetadata(LocalChatMessage("preview","preview",1,"2026-10-12T10:00:00Z",transport=ChatTransport.SENT,
                    read=true,timelineKey="preview"),true,Modifier.align(Alignment.End))
            }
        }
    }
}
