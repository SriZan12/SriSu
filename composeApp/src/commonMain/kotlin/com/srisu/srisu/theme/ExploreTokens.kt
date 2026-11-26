package com.srisu.srisu.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/** Derived from Together / Discover 311:8955 and Faves 311:9758.
 * Figma inspection was visual (MCP quota unavailable); dark mode uses approved neutral roles. */
object ExploreTokens {
    val maxWidth = 600.dp
    val avatar = 40.dp
    val pairWidth = 64.dp
    val thumbnailWidth = 108.dp
    val thumbnailHeight = 122.dp
    val filmWidth = 52.dp
    val filmHeight = 62.dp
    fun canvas(colors: ColorScheme) = if (colors.background == LightBackground) Color(0xFFF8F5F2) else colors.background
    fun paper(colors: ColorScheme) = if (colors.background == LightBackground) colors.surface else colors.surfaceContainer
    fun saved(colors: ColorScheme) = if (colors.background == LightBackground) LightSecondaryContainer else colors.surfaceContainerHigh
    @Composable fun editorial(): TextStyle = sriSuTypography(useSerifHeadings = true).headlineMedium
}
