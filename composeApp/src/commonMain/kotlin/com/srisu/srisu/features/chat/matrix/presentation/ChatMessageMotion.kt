package com.srisu.srisu.features.chat.matrix.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.srisu.srisu.theme.ChatTokens

private val messageFade = tween<Float>(ChatTokens.messageEnterMillis, easing = LinearOutSlowInEasing)

/** One short reveal when a loaded timeline enters composition, not on message/state updates. */
@Composable
internal fun rememberChatLoadAlpha(motionEnabled: Boolean): State<Float> {
    val opacity = remember { Animatable(if (motionEnabled) 0f else 1f) }
    LaunchedEffect(motionEnabled) {
        if (motionEnabled) opacity.animateTo(1f, messageFade) else opacity.snapTo(1f)
    }
    return opacity.asState()
}

/** Only newly inserted, visible rows fade. Stable transaction keys prevent acknowledgement replay.
 * No placement/size or removal animation: pagination and media geometry remain immediate.
 */
internal fun LazyItemScope.chatMessageEntrance(motionEnabled: Boolean): Modifier =
    if (!motionEnabled) Modifier else Modifier.animateItem(
        fadeInSpec = messageFade,
        placementSpec = null,
        fadeOutSpec = null,
    )
