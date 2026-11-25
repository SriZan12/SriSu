package com.srisu.srisu.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Geometry and type from the approved Quick Share illustrated preview. Chat scope only. */
object ChatShareTokens {
    val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val actionShape = RoundedCornerShape(14.dp)
    val mediaSlot = 54.dp
    val mediaArt = 62.dp
    val activitySlot = 42.dp
    val activityArt = 51.dp
    const val largeTextReflow = 1.3f
    const val lightScrimAlpha = .15f
    const val darkScrimAlpha = .40f
}

fun chatShareTypography(base: Typography) = base.copy(
    titleLarge = base.titleLarge.copy(fontSize = 21.sp, lineHeight = 27.3.sp,
        letterSpacing = (-.45).sp, fontWeight = FontWeight.Medium),
    titleMedium = base.titleMedium.copy(fontSize = 16.sp, lineHeight = 22.4.sp,
        letterSpacing = 0.sp, fontWeight = FontWeight.Medium),
    bodySmall = base.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.8.sp, letterSpacing = 0.sp),
    labelMedium = base.labelMedium.copy(fontSize = 13.sp, lineHeight = 18.2.sp,
        letterSpacing = 0.sp, fontWeight = FontWeight.Normal),
    labelSmall = base.labelSmall.copy(fontSize = 11.sp, lineHeight = 15.4.sp,
        letterSpacing = .8.sp, fontWeight = FontWeight.Medium),
)
