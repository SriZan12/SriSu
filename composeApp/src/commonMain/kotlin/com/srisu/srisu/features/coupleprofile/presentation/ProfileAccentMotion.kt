package com.srisu.srisu.features.coupleprofile.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Owned by the screen, so editors, preview, refresh and restored state cannot replay it. */
@Stable
internal class ProfileAccentMotion(consumed: Boolean = false) {
    var consumed by mutableStateOf(consumed)
        private set
    val heartScale = Animatable(1f)
    val momentsScale = Animatable(1f)
    val momentsRotation = Animatable(0f)

    suspend fun playOnce() {
        if (consumed) return
        consumed = true
        // Compose supplies Android's duration scale and iOS's Reduce Motion setting.
        if (coroutineContext[MotionDurationScale]?.scaleFactor == 0f) return
        try {
            coroutineScope {
                launch {
                    heartScale.animateTo(1f, keyframes {
                        durationMillis = 580
                        1f at 0 using FastOutSlowInEasing
                        1.16f at 110 using FastOutSlowInEasing
                        1f at 240 using FastOutSlowInEasing
                        1.09f at 350 using FastOutSlowInEasing
                        1f at 580
                    })
                }
                launch {
                    momentsScale.animateTo(1f, keyframes {
                        durationMillis = 820
                        1f at 0
                        1f at 300 using FastOutSlowInEasing
                        1.14f at 470 using FastOutSlowInEasing
                        1f at 820
                    })
                }
                launch {
                    momentsRotation.animateTo(0f, keyframes {
                        durationMillis = 820
                        0f at 0
                        0f at 300 using FastOutSlowInEasing
                        7f at 470 using FastOutSlowInEasing
                        0f at 820
                    })
                }
            }
        } finally {
            // Leaving/scrolling away midway settles immediately and still consumes the entrance.
            withContext(NonCancellable) {
                heartScale.snapTo(1f)
                momentsScale.snapTo(1f)
                momentsRotation.snapTo(0f)
            }
        }
    }

    companion object {
        val Saver = Saver<ProfileAccentMotion, Boolean>(
            save = { it.consumed }, restore = { ProfileAccentMotion(it) },
        )
    }
}

@Composable
internal fun rememberProfileAccentMotion(accountId: Long?, coupleId: Long?): ProfileAccentMotion =
    rememberSaveable(accountId, coupleId, saver = ProfileAccentMotion.Saver) { ProfileAccentMotion() }

/** Wait for the statistics row to enter the clipped viewport; no off-screen animation timer. */
@Composable
internal fun profileAccentEntrance(motion: ProfileAccentMotion, enabled: Boolean): Modifier {
    // Visibility belongs to this layout node; changing the account need not reposition it.
    var visible by remember { mutableStateOf(false) }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val active = visible && enabled && lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(motion, active) { if (active) motion.playOnce() }
    return Modifier.onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInWindow()
        visible = bounds.width > 0 && bounds.height >= coordinates.size.height * .75f
    }
}
