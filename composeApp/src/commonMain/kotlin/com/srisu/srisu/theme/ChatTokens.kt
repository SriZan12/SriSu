package com.srisu.srisu.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Couple Chat geometry, scoped to the conversation so global shape and type tokens stay unchanged.
 * Bubble radii follow the Figma thread (`184:274`): soft 20 dp corners, tightened to 6 dp where
 * consecutive messages from the same sender join.
 */
object ChatTokens {
    val bubbleRadius = 20.dp
    val bubbleJoinedRadius = 6.dp
    val bubbleHorizontalPadding = 14.dp
    val bubbleVerticalPadding = 9.dp
    val bubbleMaxWidth = 440.dp
    const val bubbleMaxWidthFraction = 0.8f
    val groupedGap = 2.dp
    val groupGap = 12.dp
    val metaGap = 6.dp
    val metaIcon = 14.dp
    val metaTextSize = 11.sp
    val metaLineHeight = 14.sp
    val headerAvatar = 32.dp
    val emptyAvatar = 72.dp
    val composerMinHeight = 48.dp
    val sendButton = 44.dp
    val jumpButton = 40.dp
    val statusIconContainer = 56.dp
    val skeletonLine = 14.dp
    const val composerMaxLines = 6

    fun bubbleShape(mine: Boolean, joinsPrevious: Boolean, joinsNext: Boolean): RoundedCornerShape {
        val full = bubbleRadius
        val joined = bubbleJoinedRadius
        val top = if (joinsPrevious) joined else full
        val bottom = if (joinsNext) joined else full
        return if (mine) {
            RoundedCornerShape(topStart = full, topEnd = top, bottomEnd = bottom, bottomStart = full)
        } else {
            RoundedCornerShape(topStart = top, topEnd = full, bottomEnd = full, bottomStart = bottom)
        }
    }

    /** Placeholders and the composer field share the bubble radius so tall drafts keep soft corners. */
    val roundedShape = RoundedCornerShape(bubbleRadius)
}
