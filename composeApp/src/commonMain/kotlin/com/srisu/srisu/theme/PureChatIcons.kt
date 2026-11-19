package com.srisu.srisu.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Outlined glyphs derived from the approved Pure preview, on a 24-unit grid. */
object PureChatIcons {
    private fun icon(name: String, draw: PathBuilder.() -> Unit) = ImageVector.Builder(name,
        24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.65f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = draw)
    }.build()
    val Back = icon("PureBack") { moveTo(15f, 18f); lineTo(9f, 12f); lineTo(15f, 6f) }
    val Add = icon("PureAdd") { moveTo(12f, 5f); verticalLineTo(19f); moveTo(5f, 12f); horizontalLineTo(19f) }
    val Send = icon("PureSend") { moveTo(12f, 19f); verticalLineTo(5f); moveTo(5f, 12f); lineTo(12f, 5f); lineTo(19f, 12f) }
    val Open = icon("PureOpen") { moveTo(7f, 17f); lineTo(17f, 7f); moveTo(7f, 7f); horizontalLineTo(17f); verticalLineTo(17f) }
    val Play = icon("PurePlay") { moveTo(6f, 3f); lineTo(20f, 12f); lineTo(6f, 21f); close() }
    val Mic = icon("PureMic") {
        moveTo(9f, 5f); curveTo(9f, 1f, 15f, 1f, 15f, 5f); verticalLineTo(12f)
        curveTo(15f, 16f, 9f, 16f, 9f, 12f); close()
        moveTo(5f, 10f); verticalLineTo(12f); curveTo(5f, 21f, 19f, 21f, 19f, 12f); verticalLineTo(10f)
        moveTo(12f, 19f); verticalLineTo(22f)
    }
}
