package com.srisu.srisu.coupleprofile

import android.graphics.Bitmap
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.dp
import com.srisu.srisu.features.coupleprofile.data.CoupleProfile
import com.srisu.srisu.features.coupleprofile.presentation.*
import com.srisu.srisu.theme.SriSuTheme
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProfileAccentMotionTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private lateinit var motion: ProfileAccentMotion
    private val profile = CoupleProfile(1, "member", daysTogether = 248, visibleMomentCount = 48, daysToAnniversary = 117)

    @OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
    @Composable private fun Fixture(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalInspectionMode provides true) {
            org.jetbrains.compose.resources.PreviewContextConfigurationEffect()
        }
        SriSuTheme {
            Column(Modifier.fillMaxWidth().padding(20.dp)) { content() }
        }
    }

    private fun settle() {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1000)
        compose.runOnIdle { assertTrue(motion.consumed); assertResting() }
    }

    private fun assertResting() {
        assertEquals(1f, motion.heartScale.value, .001f)
        assertEquals(1f, motion.momentsScale.value, .001f)
        assertEquals(0f, motion.momentsRotation.value, .001f)
    }

    private fun capture(name: String) = compose.runOnIdle {
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        val output = File("build/reports/couple-profile-motion/$name.png").apply { parentFile?.mkdirs() }
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun heartThenMomentsSettleWithoutLooping() {
        compose.mainClock.autoAdvance = false
        compose.setContent { Fixture {
            motion = rememberProfileAccentMotion(1, 1)
            ProfileMetrics(profile, motion)
        } }
        compose.waitForIdle()
        capture("rest")
        compose.mainClock.advanceTimeBy(128)
        compose.runOnIdle {
            assertTrue(motion.consumed)
            assertTrue(motion.heartScale.value > 1.10f)
            assertEquals(1f, motion.momentsScale.value, .001f)
        }
        capture("heart-beat")
        compose.mainClock.advanceTimeBy(352)
        compose.runOnIdle {
            assertTrue(motion.momentsScale.value > 1.10f)
            assertTrue(motion.momentsRotation.value > 5f)
        }
        capture("moments-twinkle")
        settle()
        capture("settled")
        compose.mainClock.advanceTimeBy(5000)
        compose.runOnIdle { assertResting() }
    }

    @Test fun recompositionAndReturningFromEditorDoNotReplay() {
        val overview = mutableStateOf(true)
        val count = mutableIntStateOf(48)
        compose.mainClock.autoAdvance = false
        compose.setContent { Fixture {
            motion = rememberProfileAccentMotion(1, 1)
            if (overview.value) ProfileMetrics(profile.copy(visibleMomentCount = count.intValue), motion)
        } }
        settle()
        compose.runOnIdle { count.intValue++ }
        compose.mainClock.advanceTimeBy(128)
        compose.runOnIdle { assertResting(); overview.value = false }
        compose.waitForIdle()
        compose.runOnIdle { overview.value = true }
        compose.mainClock.advanceTimeBy(128)
        compose.runOnIdle { assertResting() }
    }

    @Test fun waitsUntilVisibleAndDoesNotReplayAfterScrolling() {
        lateinit var scroll: ScrollState
        compose.mainClock.autoAdvance = false
        compose.setContent { Fixture {
            motion = rememberProfileAccentMotion(1, 1)
            scroll = rememberScrollState()
            Column(Modifier.height(300.dp).verticalScroll(scroll)) {
                Spacer(Modifier.height(600.dp))
                ProfileMetrics(profile, motion)
            }
        } }
        compose.mainClock.advanceTimeBy(1000)
        compose.runOnIdle { assertFalse(motion.consumed) }
        compose.runOnIdle { scroll.dispatchRawDelta(scroll.maxValue.toFloat()) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(128)
        compose.runOnIdle { assertTrue(motion.heartScale.value > 1f) }
        // Scroll away while running: cancel into a stable resting state.
        compose.runOnIdle { scroll.dispatchRawDelta(-scroll.maxValue.toFloat()) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.runOnIdle { assertResting(); scroll.dispatchRawDelta(scroll.maxValue.toFloat()) }
        compose.mainClock.advanceTimeBy(128)
        compose.runOnIdle { assertResting() }
    }

    @Test fun restoredScreenDoesNotReplay() {
        val restoration = StateRestorationTester(compose)
        compose.mainClock.autoAdvance = false
        restoration.setContent { Fixture {
            motion = rememberProfileAccentMotion(1, 1)
            ProfileMetrics(profile, motion)
        } }
        settle()
        restoration.emulateSavedInstanceStateRestore()
        compose.mainClock.advanceTimeBy(128)
        compose.runOnIdle { assertTrue(motion.consumed); assertResting() }
    }

    @Test fun differentAccountOrCoupleGetsItsOwnEntrance() {
        val account = mutableStateOf(1L)
        val couple = mutableStateOf(1L)
        compose.mainClock.autoAdvance = false
        compose.setContent { Fixture {
            motion = rememberProfileAccentMotion(account.value, couple.value)
            ProfileMetrics(profile.copy(id = couple.value), motion)
        } }
        settle()
        compose.runOnIdle { couple.value = 2 }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(160)
        compose.runOnIdle { assertTrue(motion.heartScale.value > 1f) }
        settle()
        compose.runOnIdle { account.value = 2 }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(160)
        compose.runOnIdle { assertTrue(motion.heartScale.value > 1f) }
        settle()
    }

    @Test fun reducedMotionConsumesEntranceWithoutAnimating() {
        val reducedMotion = object : MotionDurationScale { override val scaleFactor = 0f }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            motion = rememberProfileAccentMotion(1, 1)
            val scope = rememberCoroutineScope()
            LaunchedEffect(Unit) { scope.launch(reducedMotion) { motion.playOnce() } }
        }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(motion.consumed); assertResting() }
        compose.mainClock.advanceTimeBy(128)
        compose.runOnIdle { assertResting() }
    }

    @Test fun coveredProfileWaitsThenCancelsWhenCoveredAgain() {
        val enabled = mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.setContent { Fixture {
            motion = rememberProfileAccentMotion(1, 1)
            ProfileMetrics(profile, motion, motionEnabled = enabled.value)
        } }
        compose.mainClock.advanceTimeBy(1000)
        compose.runOnIdle { assertFalse(motion.consumed); enabled.value = true }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(160)
        compose.runOnIdle { assertTrue(motion.heartScale.value > 1f); enabled.value = false }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertResting(); enabled.value = true }
        compose.mainClock.advanceTimeBy(160)
        compose.runOnIdle { assertResting() }
    }
}
