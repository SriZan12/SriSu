package com.srisu.srisu.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Approved Palette grid picker; no changes to the global type scale or preset colors. */
object ChatAppearanceTokens {
    val previewShape = RoundedCornerShape(18.dp)
    val swatchShape = RoundedCornerShape(13.dp)
    val miniIncomingShape = RoundedCornerShape(7.dp, 7.dp, 7.dp, 2.dp)
    val miniOutgoingShape = RoundedCornerShape(8.dp, 8.dp, 2.dp, 8.dp)
    val swatchHeight = 58.dp
    const val previewMaxBubbleWidth = .91f
}

fun chatAppearanceTypography(base: Typography): Typography {
    val shared = chatShareTypography(base)
    val lineHeight = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
    return shared.copy(
        titleLarge = shared.titleLarge.copy(lineHeightStyle = lineHeight),
        bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.72.sp,
            letterSpacing = 0.sp, lineHeightStyle = lineHeight),
        bodySmall = base.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.4.sp,
            letterSpacing = 0.sp, lineHeightStyle = lineHeight),
        labelLarge = base.labelLarge.copy(fontSize = 15.sp, lineHeight = 21.sp,
            letterSpacing = 0.sp, fontWeight = FontWeight.Medium, lineHeightStyle = lineHeight),
        labelMedium = base.labelMedium.copy(fontSize = 13.sp, lineHeight = 16.9.sp,
            letterSpacing = 0.sp, fontWeight = FontWeight.Normal, lineHeightStyle = lineHeight),
    )
}
