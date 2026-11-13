package com.srisu.srisu.features.chat

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runSkikoComposeUiTest
import com.srisu.srisu.features.chat.matrix.presentation.rememberChatLoadAlpha
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ChatMessageMotionIosTest {
    @Test fun shortRevealFinishesOnTheIosFrameClock() = runSkikoComposeUiTest {
        lateinit var opacity: State<Float>
        mainClock.autoAdvance = false
        setContent { opacity = rememberChatLoadAlpha(true) }
        waitForIdle()
        mainClock.advanceTimeBy(80)
        runOnIdle { assertTrue(opacity.value > 0f && opacity.value < 1f) }
        mainClock.advanceTimeBy(192)
        runOnIdle { assertEquals(1f, opacity.value) }
    }

    @Test fun disabledMotionIsImmediateAndDoesNotReplayWhenEnabled() = runSkikoComposeUiTest {
        val enabled = mutableStateOf(false)
        lateinit var opacity: State<Float>
        mainClock.autoAdvance = false
        setContent { opacity = rememberChatLoadAlpha(enabled.value) }
        runOnIdle { assertEquals(1f, opacity.value); enabled.value = true }
        mainClock.advanceTimeByFrame()
        runOnIdle { assertEquals(1f, opacity.value) }
    }
}
