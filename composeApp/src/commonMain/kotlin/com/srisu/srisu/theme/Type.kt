package com.srisu.srisu.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import srisu.composeapp.generated.resources.Res
import srisu.composeapp.generated.resources.satoshi_regular
import srisu.composeapp.generated.resources.satoshi_medium
import srisu.composeapp.generated.resources.satoshi_bold
import srisu.composeapp.generated.resources.instrument_serif_regular

@Composable
fun sriSuTypography(useSerifHeadings: Boolean = false): Typography {
    val sans = FontFamily(
        Font(Res.font.satoshi_regular, FontWeight.Normal),
        Font(Res.font.satoshi_medium, FontWeight.Medium),
        Font(Res.font.satoshi_bold, FontWeight.Bold),
    )
    val serif = FontFamily(Font(Res.font.instrument_serif_regular, FontWeight.Normal))
    fun text(size: Float, line: Float, tracking: Float, weight: FontWeight = FontWeight.Normal) =
        TextStyle(fontFamily = sans, fontWeight = weight, fontSize = size.sp,
            lineHeight = line.sp, letterSpacing = tracking.sp)
    // Written Satoshi scale. Optional serif pairing matches the currently rendered preview.
    fun heading(size: Float, line: Float, tracking: Float) = if (useSerifHeadings)
        text(size, size * 1.1f, -size * 0.01f).copy(fontFamily = serif)
    else text(size, line, tracking, FontWeight.Bold)
    return Typography(
        displayLarge = heading(72f, 79.2f, -2.16f),
        displayMedium = heading(52f, 58.76f, -1.56f),
        displaySmall = heading(40f, 51.6f, -1f),
        headlineLarge = heading(32f, 41.6f, -0.8f),
        headlineMedium = heading(24f, 31.92f, -0.6f),
        headlineSmall = heading(20f, 26.6f, -0.5f),
        titleLarge = text(20f, 26.6f, -0.5f, FontWeight.Bold),
        titleMedium = text(16f, 24f, -0.16f, FontWeight.Bold),
        titleSmall = text(14f, 21f, -0.14f, FontWeight.Medium),
        bodyLarge = text(16f, 24f, -0.16f),
        bodyMedium = text(14f, 21f, -0.14f),
        bodySmall = text(12f, 18f, -0.12f),
        labelLarge = text(16f, 24f, -0.08f, FontWeight.Bold),
        labelMedium = text(14f, 20f, -0.07f, FontWeight.Bold),
        labelSmall = text(12f, 18f, 1.44f, FontWeight.Bold),
    )
}

/** Floating fields have their own documented 17/13 scale. Keep M3's role names
 * inside a local typography scope so label animation uses the correct endpoints. */
fun SriSuFieldTypography(base: Typography): Typography = base.copy(
    bodyLarge = base.bodyLarge.copy(fontSize = 17.sp, lineHeight = 24.2857.sp, letterSpacing = 0.sp),
    bodySmall = base.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.5714.sp, letterSpacing = 0.sp),
)

/** Auth & Partner Linking uses the design system's optional serif heading pairing. */
@Composable
fun SriSuPartnerLinkTypography(): Typography = sriSuTypography(useSerifHeadings = true).let {
    it.copy(
        titleMedium = it.titleMedium.copy(fontWeight = FontWeight.Medium),
        labelSmall = it.bodySmall.copy(fontWeight = FontWeight.Bold),
    )
}
