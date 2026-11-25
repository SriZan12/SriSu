package com.srisu.srisu.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Typography
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Approved Stage 2 reference, scoped to Home. Chat palettes remain independent. */
object HomeSpace {
    // Resolve from the app theme, including an explicit Settings override of the OS mode.
    internal val palette: HomeSpacePalette @Composable get() =
        if (MaterialTheme.colorScheme.background.luminance() < .5f) DarkHomeSpace else LightHomeSpace
    val paper @Composable get() = palette.paper
    val ink @Composable get() = palette.ink
    val muted @Composable get() = palette.muted
    val warm @Composable get() = palette.warm
    val lilac @Composable get() = palette.lilac
    val purple @Composable get() = palette.purple
    val line @Composable get() = palette.line
    val green @Composable get() = palette.green
    val orange @Composable get() = palette.orange
    val blue @Composable get() = palette.blue
    val pink @Composable get() = palette.pink
    val apricot @Composable get() = palette.apricot
    val sky @Composable get() = palette.sky
    val connector @Composable get() = muted.copy(alpha = .35f)
    // Illustration colours are artwork, not surfaces or selection states.
    val peach = Color(0xFFFFCEB1)
    val lavender = Color(0xFFDCC8ED)
    val artInk = Color(0xFF8150A4)
    val artPaper = Color(0xFFFFFFFF)
    val artLine = Color(0xFFE8E4E5)
    val card = RoundedCornerShape(22.dp)
    val hero = RoundedCornerShape(30.dp)
    val pill = RoundedCornerShape(50)
    val colors @Composable get() = palette.let { p ->
        if (p === LightHomeSpace) lightColorScheme(primary = p.ink, onPrimary = p.paper,
            background = p.paper, onBackground = p.ink, surface = p.paper, onSurface = p.ink,
            surfaceVariant = p.warm, onSurfaceVariant = p.muted, surfaceContainer = p.warm,
            outline = p.line, secondary = p.purple, onSecondary = p.paper)
        else SriSuDarkColorScheme.copy(primary = p.ink, onPrimary = p.paper,
            background = p.paper, onBackground = p.ink, surface = p.paper, onSurface = p.ink,
            surfaceVariant = p.warm, onSurfaceVariant = p.muted, surfaceContainer = p.warm,
            secondary = p.purple, onSecondary = p.paper, secondaryContainer = p.lilac,
            onSecondaryContainer = p.ink, tertiaryContainer = p.lilac, onTertiaryContainer = p.ink,
            outline = p.muted, outlineVariant = p.line)
    }
    fun type(base: Typography) = base.copy(
        headlineMedium = base.headlineMedium.copy(fontSize = 24.sp, lineHeight = 30.24.sp, letterSpacing = (-.65).sp, lineBreak = androidx.compose.ui.text.style.LineBreak.Heading),
        titleLarge = base.titleLarge.copy(fontSize = 19.sp, lineHeight = 25.sp, letterSpacing = (-.45).sp),
        titleMedium = base.titleMedium.copy(fontSize = 15.sp, lineHeight = 20.25.sp, letterSpacing = (-.2).sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 13.sp, lineHeight = 19.sp),
        bodySmall = base.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
        labelLarge = base.labelLarge.copy(fontSize = 13.sp, lineHeight = 18.sp),
        labelMedium = base.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
        labelSmall = base.labelSmall.copy(fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 1.35.sp, fontWeight = FontWeight.Bold),
    )
}

/** Keep the approved light reference exact; derive dark surfaces with restrained warm neutrals. */
@Immutable
internal data class HomeSpacePalette(
    val paper: Color, val ink: Color, val muted: Color, val warm: Color,
    val lilac: Color, val purple: Color, val line: Color, val green: Color,
    val orange: Color, val blue: Color, val pink: Color, val apricot: Color, val sky: Color,
)
internal val LightHomeSpace = HomeSpacePalette(
    paper = Color(0xFFFFFFFF), ink = Color(0xFF29292B), muted = Color(0xFF706C72),
    warm = Color(0xFFF5F1EF), lilac = Color(0xFFEEE6F5), purple = Color(0xFF8150A4),
    line = Color(0xFFE8E4E5), green = Color(0xFF278153), orange = Color(0xFFBA501C),
    blue = Color(0xFF009FE6), pink = Color(0xFFF9E6EB), apricot = Color(0xFFF8E1D5), sky = Color(0xFFDCE8F5),
)
internal val DarkHomeSpace = HomeSpacePalette(
    paper = Color(0xFF141312), ink = Color(0xFFECE9E6), muted = Color(0xFFBDB7B2),
    warm = Color(0xFF211F1E), lilac = Color(0xFF2E2A28), purple = Color(0xFFE2C6B0),
    line = Color(0xFF48423E), green = Color(0xFF85D3AA), orange = Color(0xFFF5B18E),
    blue = Color(0xFF82C7ED), pink = Color(0xFF2D2527), apricot = Color(0xFF302720), sky = Color(0xFF222B30),
)
