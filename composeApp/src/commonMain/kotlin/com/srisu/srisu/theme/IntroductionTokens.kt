package com.srisu.srisu.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Geometry/colors derived from the user-supplied Onboarding and Space exports (2026-09-28).
 * Dark containers use the established theme; the decorative badges retain their artwork colors. */
object IntroductionTokens {
    val brandDotSize = 14.dp
    val brandDot = Color(0xFFFC7A2A)
    val moments = Color(0xFFFF9DCC)
    val plans = Color(0xFF00A9F4)
    val memories = Color(0xFFFFCB00)
    private val momentsLight = Color(0xFFF9E6EC)
    private val plansLight = Color(0xFFD8E7F5)
    private val memoriesLight = Color(0xFFF7ECD4)
    val iconPlate = Color(0xFFF6F1EF)
    val iconInk = Color(0xFFFFFFFF)
    val featureIconPlate = 56.dp
    val featureBadge = 28.dp
    val featureGlyph = 22.dp
    val progressWidth = 20.dp
    val progressHeight = 4.dp
    val landscapeAspectRatio = 457.39f / 230f
    fun momentsContainer(colors: ColorScheme) = if (colors.background == LightBackground) momentsLight else colors.secondaryContainer
    fun plansContainer(colors: ColorScheme) = if (colors.background == LightBackground) plansLight else colors.tertiaryContainer
    fun memoriesContainer(colors: ColorScheme) = if (colors.background == LightBackground) memoriesLight else colors.primaryContainer
}
