package com.srisu.srisu.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp

object SriSuSpacing {
    val none = 0.dp
    val hairline = 1.dp
    val tiny = 4.dp
    val small = 8.dp
    val compact = 12.dp
    val medium = 16.dp
    val gutter = 20.dp
    val large = 24.dp
    val section = 28.dp
    val extraLarge = 32.dp
    val spacious = 40.dp
    val touchTarget = 48.dp
    val huge = 64.dp
    val cardPadding = medium
    val listGap = small
    val buttonSmall = 32.dp
    val buttonMedium = 36.dp
    val buttonLarge = 40.dp
    val fieldHeight = 64.dp
    val otpHeight = 56.dp
    val icon = 20.dp
    val chromeIcon = 24.dp
}
val MaterialTheme.spacing: SriSuSpacing get() = SriSuSpacing
