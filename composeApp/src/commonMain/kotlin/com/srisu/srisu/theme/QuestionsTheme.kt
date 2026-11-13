package com.srisu.srisu.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Questions page 1:6: scoped estimates from the inspected Figma and approved Stage 1 study.
 * Reuses the approved Home/Pure surfaces; export quota prevents exact asset/token extraction. */
internal object QuestionsStyle {
    val hero = RoundedCornerShape(28.dp)
    val inset = RoundedCornerShape(16.dp)
    val row = RoundedCornerShape(20.dp)
    val topic = RoundedCornerShape(24.dp)
    val tile = RoundedCornerShape(22.dp)
    val received = RoundedCornerShape(18.dp,18.dp,18.dp,4.dp)
    val sent = RoundedCornerShape(18.dp,18.dp,4.dp,18.dp)
    val invisible = Color.Transparent
    fun type(base: Typography) = HomeSpace.type(base).copy(
        bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 23.sp),
        titleSmall = base.titleSmall.copy(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold),
    )
}
