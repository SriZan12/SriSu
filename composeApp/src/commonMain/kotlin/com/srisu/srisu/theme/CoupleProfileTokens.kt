package com.srisu.srisu.theme

import androidx.compose.material3.ColorScheme

/** Reuses the warm plate from the supplied Space design. Dark surfaces follow
 * the existing semantic palette; no private photos are bundled design assets. */
object CoupleProfileTokens {
    fun sectionSurface(colors: ColorScheme) = if (colors.background == LightBackground) IntroductionTokens.iconPlate else colors.surfaceVariant
}
