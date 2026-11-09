package com.srisu.srisu.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Approved Stage 2 reference, scoped to Home. Chat palettes remain independent. */
object HomeSpace {
    val paper = Color(0xFFFFFFFF)
    val ink = Color(0xFF29292B)
    val muted = Color(0xFF706C72)
    val warm = Color(0xFFF5F1EF)
    val lilac = Color(0xFFEEE6F5)
    val purple = Color(0xFF8150A4)
    val line = Color(0xFFE8E4E5)
    val green = Color(0xFF278153)
    val orange = Color(0xFFBA501C)
    val blue = Color(0xFF009FE6)
    val peach = Color(0xFFFFCEB1)
    val lavender = Color(0xFFDCC8ED)
    val pink = Color(0xFFF9E6EB)
    val apricot = Color(0xFFF8E1D5)
    val sky = Color(0xFFDCE8F5)
    val card = RoundedCornerShape(22.dp)
    val hero = RoundedCornerShape(30.dp)
    val pill = RoundedCornerShape(50)
    val colors = lightColorScheme(primary = ink, onPrimary = paper, background = paper, onBackground = ink,
        surface = paper, onSurface = ink, surfaceVariant = warm, onSurfaceVariant = muted,
        surfaceContainer = warm, outline = line, secondary = purple, onSecondary = paper)
    fun type(base: Typography) = base.copy(
        headlineMedium = base.headlineMedium.copy(fontSize = 24.sp, lineHeight = 30.24.sp, letterSpacing = (-.65).sp, lineBreak = androidx.compose.ui.text.style.LineBreak.Heading),
        titleLarge = base.titleLarge.copy(fontSize = 19.sp, lineHeight = 25.sp, letterSpacing = (-.45).sp),
        titleMedium = base.titleMedium.copy(fontSize = 15.sp, lineHeight = 20.25.sp, letterSpacing = (-.2).sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 13.sp, lineHeight = 19.sp),
        bodySmall = base.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
        labelLarge = base.labelLarge.copy(fontSize = 13.sp, lineHeight = 18.sp),
        labelMedium = base.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
        labelSmall = base.labelSmall.copy(fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 1.35.sp, fontWeight = FontWeight.Bold),
    )
}
