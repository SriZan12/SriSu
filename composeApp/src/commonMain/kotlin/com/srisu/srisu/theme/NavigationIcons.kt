package com.srisu.srisu.theme

import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.unit.dp

/** The approved Pure chevron, shared by all screen-level back actions. */
val NavigationBackIcon = ImageVector.Builder("SriSuBack", 24.dp, 24.dp, 24f, 24f, autoMirror = true).apply {
    path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.65f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(15f, 18f); lineTo(9f, 12f); lineTo(15f, 6f)
    }
}.build()
