package com.srisu.srisu.features.profile

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import com.srisu.srisu.features.auth.presentation.screen.profilesetup.OnboardingPhotoContent
import com.srisu.srisu.features.auth.presentation.state.AuthUIStates
import com.srisu.srisu.features.home.HomeRenderFixture
import com.srisu.srisu.features.home.profile.presentation.screen.InterestScreen
import com.srisu.srisu.theme.SriSuTheme
import kotlinx.cinterop.*
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import platform.Foundation.*
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class ProfileRepairIosTest {
    private fun interests(width: Int, height: Int, scale: Float, dark: Boolean) = runSkikoComposeUiTest(
        size = Size(width * 2f, height * 2f), density = Density(2f, scale)) {
        var back = false; var saved = emptyList<Int>()
        setContent { SriSuTheme(darkTheme = dark) { InterestScreen(repairInterests, onCancel = { back = true }, onInterestSelected = {
            saved = it.orEmpty().filterNotNull().filter { it.removed != true }.mapNotNull { it.interest }
        }) } }
        onNodeWithText("Search interests").performTextInput("walk")
        onNodeWithText("Long walks and little adventures").performScrollTo().assertIsDisplayed().performClick()
        writePng("interests-$width-$scale", Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        onNodeWithText("Done").assertIsDisplayed().performClick(); assertEquals(listOf(4), saved)
        onNodeWithContentDescription("Go back").performClick(); assertTrue(back)
    }
    @Test fun interestStandard() = interests(390, 844, 1f, false)
    @Test fun interestSmallDarkLargeText() = interests(320, 640, 1.8f, true)
    @Test fun interestLandscape() = interests(640, 320, 1f, false)
    @Test fun photoSmallLargeText() = runSkikoComposeUiTest(size = Size(640f, 1280f), density = Density(2f, 1.8f)) {
        var choose = false; var back = false
        setContent { SriSuTheme { OnboardingPhotoContent(AuthUIStates(currentProgressStep = 2), { back = true }, {}, { choose = true }, {}) } }
        onNodeWithText("Choose photo").performScrollTo().assertIsDisplayed().performClick(); assertTrue(choose)
        writePng("photo-small-large-text", Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        onNodeWithContentDescription("Go back").performClick(); assertTrue(back)
    }
    @Test fun skeletonTransitionsToContentWithoutEmptyFlash() = runSkikoComposeUiTest(size = Size(640f, 1280f), density = Density(2f)) {
        var state by mutableStateOf("loading")
        mainClock.autoAdvance = false
        setContent { SriSuTheme { HomeRenderFixture(state) } }
        mainClock.advanceTimeBy(500)
        onNodeWithContentDescription("Loading Home").assertIsDisplayed()
        onNodeWithText("You’re all caught up").assertDoesNotExist()
        writePng("home-skeleton-small", Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        runOnIdle { state = "spark" }; mainClock.advanceTimeBy(500)
        onNodeWithContentDescription("Loading Home").assertDoesNotExist()
        onNodeWithText("You & Aatithi").assertIsDisplayed()
    }
    @Test fun reducedMotionSkeletonStaysStill() = runSkikoComposeUiTest(size = Size(640f, 1280f), density = Density(2f),
        effectContext = object : androidx.compose.ui.MotionDurationScale { override val scaleFactor = 0f }) {
        mainClock.autoAdvance = false
        setContent { SriSuTheme { HomeRenderFixture("loading") } }
        mainClock.advanceTimeBy(100)
        fun capture() = Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes
        val first = capture()
        mainClock.advanceTimeBy(1000)
        assertContentEquals(first, capture())
    }
    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    private fun writePng(name: String, bytes: ByteArray) {
        val directory = NSTemporaryDirectory() + "profile-repair"
        NSFileManager.defaultManager.createDirectoryAtPath(directory, true, null, null)
        val path = "$directory/ios-$name.png"
        bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()).writeToFile(path, true) }
        println("PROFILE_REPAIR_RENDER $path")
    }
}
