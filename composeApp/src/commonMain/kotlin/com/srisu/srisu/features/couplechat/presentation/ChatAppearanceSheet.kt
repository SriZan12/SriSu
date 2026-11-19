@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.srisu.srisu.features.couplechat.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.srisu.srisu.features.couplechat.data.ChatThemePreset
import com.srisu.srisu.theme.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import srisu.composeapp.generated.resources.*

@Composable
internal fun ChatAppearanceSheet(saved: String, onApply: (String) -> Unit, onDismiss: () -> Unit) {
    val savedPreset = ChatThemePreset.fromId(saved)
    var selection by rememberSaveable(saved) { mutableStateOf(savedPreset.id) }
    val selected = ChatThemePreset.fromId(selection)
    // Resolve presets against the app palette BEFORE installing neutral Pure picker chrome.
    // Deriving them from the picker theme would silently change their existing colors.
    val base = MaterialTheme.colorScheme
    val palettes = remember(base) { ChatThemePreset.entries.associateWith { chatColors(it, base) } }
    SriSuChatTheme(ChatThemePreset.PURE) {
        MaterialTheme(typography = chatAppearanceTypography(MaterialTheme.typography)) {
            val chrome = MaterialTheme.chatColors
            val scrimAlpha = if (chrome.scheme.surface.luminance() < .5f) ChatShareTokens.darkScrimAlpha else ChatShareTokens.lightScrimAlpha
            ModalBottomSheet(
                onDismissRequest = onDismiss,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                shape = ChatShareTokens.sheetShape,
                containerColor = chrome.scheme.surface,
                contentColor = chrome.text,
                tonalElevation = 0.dp,
                scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = scrimAlpha),
                dragHandle = {
                    Box(Modifier.fillMaxWidth().padding(top = 9.dp, bottom = 10.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(32.dp, 4.dp).clip(CircleShape).background(chrome.scheme.outlineVariant))
                    }
                },
            ) {
                BoxWithConstraints(Modifier.fillMaxWidth().testTag("chat-theme-picker")
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
                    val compact = maxWidth < 370.dp
                    val gutter = if (compact) 16.dp else 20.dp
                    Column(Modifier.fillMaxWidth().padding(horizontal = gutter)) {
                        Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(Res.string.chat_theme_title), Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.width(12.dp))
                            IconButton(onClick = onDismiss, modifier = Modifier.size(PureChatTokens.control)) {
                                Box(Modifier.size(36.dp).clip(CircleShape).background(chrome.incoming), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Outlined.Close, stringResource(Res.string.chat_theme_close), Modifier.size(18.dp))
                                }
                            }
                        }
                        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                            ChatThemePreview(selected, palettes.getValue(selected))
                            Spacer(Modifier.height(20.dp))
                            val fontScale = LocalDensity.current.fontScale
                            val columns = when {
                                fontScale >= 2.5f -> 1
                                fontScale >= ChatShareTokens.largeTextReflow -> 2
                                else -> 3
                            }
                            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                ChatThemePreset.entries.chunked(columns).forEach { row ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp)) {
                                        row.forEach { preset ->
                                            ThemeSwatch(preset, palettes.getValue(preset), selected == preset,
                                                { selection = preset.id }, Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(20.dp))
                        }
                        Button(onClick = { onApply(selection) }, enabled = selected != savedPreset,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).graphicsLayer { alpha = if (selected == savedPreset) .4f else 1f }, shape = MaterialTheme.shapes.pill,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 13.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = chrome.outgoingStart, contentColor = chrome.outgoingText,
                                disabledContainerColor = chrome.outgoingStart,
                                disabledContentColor = chrome.outgoingText)) {
                            Text(stringResource(Res.string.chat_theme_apply), style = MaterialTheme.typography.labelLarge)
                        }
                        Text(stringResource(Res.string.chat_theme_note), Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 18.dp),
                            style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = chrome.metadata)
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeSwatch(preset: ChatThemePreset, palette: ChatColors, selected: Boolean, onSelect: () -> Unit, modifier: Modifier) {
    val chrome = MaterialTheme.chatColors
    val defaultLabel = stringResource(Res.string.chat_theme_default_accessibility)
    Column(modifier.testTag("chat-theme-${preset.id}")
        .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
        .semantics { if (preset == ChatThemePreset.DEFAULT) stateDescription = defaultLabel },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth()) {
            val stroke = if (selected) 2.dp else 1.dp
            Column(Modifier.fillMaxWidth().height(ChatAppearanceTokens.swatchHeight)
                .clip(ChatAppearanceTokens.swatchShape).background(palette.scheme.background)
                .border(stroke, if (selected) chrome.outgoingStart else chrome.scheme.outlineVariant, ChatAppearanceTokens.swatchShape)
                .padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)) {
                Box(Modifier.fillMaxWidth(.5f).height(9.dp).clip(ChatAppearanceTokens.miniIncomingShape).background(palette.incoming))
                Box(Modifier.align(Alignment.End).fillMaxWidth(.66f).height(12.dp)
                    .clip(ChatAppearanceTokens.miniOutgoingShape).background(palette.outgoing))
            }
            if (selected) Box(Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-8).dp)
                .size(26.dp).clip(CircleShape).background(chrome.scheme.surface).padding(3.dp)) {
                Box(Modifier.fillMaxSize().clip(CircleShape).background(chrome.outgoingStart), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Check, null, Modifier.size(12.dp), tint = chrome.outgoingText)
                }
            }
        }
        Text(stringResource(themeLabel(preset)), style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal, textAlign = TextAlign.Center)
    }
}

/** Synthetic preview text only. Choosing a swatch never changes the live room or repository. */
@Composable
private fun ChatThemePreview(preset: ChatThemePreset, colors: ChatColors) {
    val chrome = MaterialTheme.chatColors
    val name = stringResource(themeLabel(preset))
    Surface(color = colors.scheme.background, contentColor = colors.text,
        shape = ChatAppearanceTokens.previewShape, border = BorderStroke(1.dp, chrome.scheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(start = 15.dp, end = 15.dp, top = 14.dp, bottom = 15.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Text(stringResource(Res.string.chat_theme_preview), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = colors.metadata)
                Text(if (preset == ChatThemePreset.DEFAULT) stringResource(Res.string.chat_theme_default, name) else name,
                    Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }, textAlign = TextAlign.End,
                    style = MaterialTheme.typography.bodySmall, color = colors.metadata)
            }
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth().clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(9.dp)) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    Surface(shape = PureChatTokens.bubbleShape(false, false), color = colors.incoming, contentColor = colors.text,
                        modifier = Modifier.widthIn(max = maxWidth * ChatAppearanceTokens.previewMaxBubbleWidth)) {
                        Text(stringResource(Res.string.chat_theme_incoming), Modifier.padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    Column(Modifier.widthIn(max = maxWidth * ChatAppearanceTokens.previewMaxBubbleWidth), horizontalAlignment = Alignment.End) {
                        Box(Modifier.clip(PureChatTokens.bubbleShape(true, false)).background(colors.outgoing).padding(horizontal = 12.dp, vertical = 7.dp)) {
                            Text(stringResource(Res.string.chat_theme_outgoing), style = MaterialTheme.typography.bodyMedium, color = colors.outgoingText)
                        }
                        Text(stringResource(Res.string.chat_theme_time), Modifier.padding(top = 5.dp, end = 4.dp), style = MaterialTheme.typography.bodySmall, color = colors.metadata)
                    }
                }
            }
        }
    }
}

private fun themeLabel(preset: ChatThemePreset): StringResource = when (preset) {
    ChatThemePreset.PURE -> Res.string.chat_theme_pure
    ChatThemePreset.ORIGINAL -> Res.string.chat_theme_original
    ChatThemePreset.BLEND -> Res.string.chat_theme_blend
    ChatThemePreset.CALM -> Res.string.chat_theme_calm
    ChatThemePreset.IRIS -> Res.string.chat_theme_iris
    ChatThemePreset.DAWN -> Res.string.chat_theme_dawn
}
