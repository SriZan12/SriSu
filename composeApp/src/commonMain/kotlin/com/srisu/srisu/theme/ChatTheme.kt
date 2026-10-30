package com.srisu.srisu.theme

import com.srisu.srisu.features.couplechat.data.ChatThemePreset
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

@Immutable
data class ChatColors(val scheme: ColorScheme, val incoming: Color, val outgoingStart: Color,
    val outgoingEnd: Color, val text: Color, val metadata: Color, val receipt: Color,
    val outgoingText: Color = text, val pure: Boolean = false,
    val sparkSurface: Color = incoming, val sparkInk: Color = text) {
    val outgoing: Brush get() = Brush.linearGradient(listOf(outgoingStart, outgoingEnd))
}

/** All blending is centralized here. No gradient is applied to header, background or composer. */
fun chatColors(preset: ChatThemePreset, base: ColorScheme): ChatColors {
    val dark = base.background.luminance() < .5f
    if (preset == ChatThemePreset.PURE) return pureChatColors(base, dark)
    val accent = when (preset) {
        ChatThemePreset.CALM -> if (dark) SriSuPalette.Ask300 else SriSuPalette.Ask800
        ChatThemePreset.DAWN -> if (dark) SriSuPalette.Reminder300 else SriSuPalette.Reminder800
        else -> base.secondary
    }
    val background = when (preset) {
        ChatThemePreset.PURE, ChatThemePreset.ORIGINAL -> lerp(base.surface, base.primaryContainer, .12f)
        ChatThemePreset.BLEND, ChatThemePreset.IRIS -> lerp(base.surface, base.secondaryContainer, .08f)
        ChatThemePreset.DAWN -> lerp(base.surface, if (dark) SriSuPalette.Planner900 else SriSuPalette.Planner100, .12f)
        ChatThemePreset.CALM -> lerp(base.surface, if (dark) SriSuPalette.Ask900 else SriSuPalette.Ask100, .12f)
    }
    val start = when (preset) {
        ChatThemePreset.PURE, ChatThemePreset.ORIGINAL -> lerp(base.surface, base.secondaryContainer, if (dark) .40f else .80f)
        ChatThemePreset.BLEND -> if (dark) lerp(base.surface, base.secondaryContainer, .50f) else SriSuPalette.Spark100
        ChatThemePreset.CALM -> if (dark) lerp(base.surface, SriSuPalette.Ask900, .65f) else SriSuPalette.Ask100
        ChatThemePreset.IRIS -> if (dark) lerp(base.surface, SriSuPalette.Spark800, .70f)
            else lerp(SriSuPalette.Spark100, SriSuPalette.Spark200, .35f)
        ChatThemePreset.DAWN -> if (dark) lerp(base.surface, SriSuPalette.Reminder900, .65f) else SriSuPalette.Reminder100
    }
    val end = when (preset) {
        ChatThemePreset.BLEND -> if (dark) lerp(base.surface, SriSuPalette.Reminder900, .45f) else SriSuPalette.Reminder100
        ChatThemePreset.IRIS -> if (dark) lerp(base.surface, SriSuPalette.Ask900, .60f) else SriSuPalette.Ask100
        ChatThemePreset.DAWN -> if (dark) lerp(base.surface, SriSuPalette.Planner900, .55f) else SriSuPalette.Planner100
        else -> start
    }
    val scheme = base.copy(background=background, primary=accent, onPrimary=if (dark) base.surface else base.onSecondary,
        primaryContainer=start, onPrimaryContainer=base.onSurface, secondary=accent,
        secondaryContainer=start, onSecondaryContainer=base.onSurface,
        surfaceContainerLow=lerp(base.surface, base.primaryContainer, .45f))
    return ChatColors(scheme, base.surfaceContainerLow, start, end, base.onSurface, base.onSurfaceVariant, accent)
}
private val LocalChatColors = staticCompositionLocalOf<ChatColors?> { null }
val MaterialTheme.chatColors: ChatColors @Composable get() = LocalChatColors.current ?: chatColors(ChatThemePreset.ORIGINAL, colorScheme)

@Composable fun SriSuChatTheme(preset: ChatThemePreset, content: @Composable () -> Unit) {
    val base = MaterialTheme.colorScheme
    val colors = remember(preset, base) { chatColors(preset, base) }
    CompositionLocalProvider(LocalChatColors provides colors,
        androidx.compose.material3.LocalMinimumInteractiveComponentSize provides PureChatTokens.control) {
        MaterialTheme(colorScheme=colors.scheme, typography=pureChatTypography(MaterialTheme.typography), shapes=MaterialTheme.shapes, content=content)
    }
}

/** Exact light/dark swatches from the approved srisu-chat-options.html Pure variant. */
private fun pureChatColors(base: ColorScheme, dark: Boolean): ChatColors {
    val background = if (dark) Color(0xFF17181B) else Color(0xFFFFFFFF)
    val text = if (dark) Color(0xFFF1F2F4) else Color(0xFF252629)
    val muted = if (dark) Color(0xFFAEB1B9) else Color(0xFF707278)
    // The preview muted ink is 4.33:1 on gray. A tiny surface-only adjustment clears 4.5:1.
    val surfaceMuted = if (dark) muted else Color(0xFF6D6F75)
    val line = if (dark) Color(0xFF35363C) else Color(0xFFE9EAED)
    val incoming = if (dark) Color(0xFF2B2D33) else Color(0xFFF2F3F5)
    val outgoing = if (dark) Color(0xFFE3E5EC) else Color(0xFF2C2E34)
    val outgoingText = if (dark) Color(0xFF25262B) else Color(0xFFFFFFFF)
    val spark = if (dark) Color(0xFF302C39) else Color(0xFFF7F5FB)
    val sparkInk = if (dark) Color(0xFFD7C7E9) else Color(0xFF6A587E)
    val scheme = base.copy(background = background, onBackground = text, surface = background,
        onSurface = text, onSurfaceVariant = surfaceMuted, surfaceContainerLow = incoming,
        surfaceContainer = incoming, surfaceContainerHigh = incoming, surfaceContainerLowest = background,
        primary = outgoing, onPrimary = outgoingText, secondary = sparkInk,
        secondaryContainer = incoming, onSecondaryContainer = text,
        primaryContainer = incoming, onPrimaryContainer = text, outlineVariant = line)
    return ChatColors(scheme, incoming, outgoing, outgoing, text, muted, muted,
        outgoingText, pure = true, sparkSurface = spark, sparkInk = sparkInk)
}
