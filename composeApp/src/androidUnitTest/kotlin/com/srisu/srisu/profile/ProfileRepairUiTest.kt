package com.srisu.srisu.profile

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import com.srisu.srisu.features.auth.presentation.screen.profilesetup.OnboardingPhotoContent
import com.srisu.srisu.features.auth.presentation.state.AuthUIStates
import com.srisu.srisu.features.home.profile.presentation.screen.InterestScreen
import com.srisu.srisu.features.home.profile.presentation.state.InterestCatalogueState
import com.srisu.srisu.features.profile.repairInterests
import com.srisu.srisu.theme.SriSuTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w320dp-h640dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProfileRepairUiTest {
    @get:Rule val ui = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private fun render(scale: Float = 1f, content: @Composable () -> Unit) {
        ui.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) { org.jetbrains.compose.resources.PreviewContextConfigurationEffect() }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) { SriSuTheme { content() } }
        }
    }
    private fun capture(name: String) = ui.runOnIdle {
        val view = ui.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        File("build/reports/profile-repair/$name.png").apply { parentFile.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun interestsSearchSelectSaveAndBackAtLargeText() {
        var saved = emptyList<Int>(); var back = false
        render(1.8f) { InterestScreen(repairInterests, onCancel = { back = true }, onInterestSelected = {
            saved = it.orEmpty().filterNotNull().filter { it.removed != true }.mapNotNull { it.interest }
        }) }
        ui.onNodeWithText("Search interests").performTextInput("walk")
        ui.onNodeWithText("Long walks and little adventures").performScrollTo().assertIsDisplayed().performClick()
        capture("interests-small-large-text")
        ui.onNodeWithText("Done").assertIsDisplayed().performClick()
        assertEquals(listOf(4), saved)
        ui.onNodeWithContentDescription("Go back").performClick(); assertTrue(back)
    }
    @Test fun interestFailureRetriesAndEmptyIsHonest() {
        var retries = 0
        var catalogue by mutableStateOf(InterestCatalogueState(loading = false, offline = true))
        render { InterestScreen(catalogue = catalogue, onRetry = { retries++ }) }
        ui.onNodeWithText("Done").assertIsNotEnabled()
        ui.onNodeWithText("Retry").performClick(); assertEquals(1, retries)
        ui.runOnIdle { catalogue = InterestCatalogueState(loading = false, loaded = true) }
        ui.onNodeWithText("No interests are available yet.").assertIsDisplayed()
    }
    @Test @Config(qualifiers = "w640dp-h320dp-xhdpi") fun photoLandscapeCanScrollAndReturn() {
        var choose = false; var back = false
        render { OnboardingPhotoContent(AuthUIStates(currentProgressStep = 2), { back = true }, {}, { choose = true }, {}) }
        ui.onNodeWithText("Choose photo").performScrollTo().assertIsDisplayed().performClick(); assertTrue(choose)
        capture("photo-landscape")
        ui.onNodeWithContentDescription("Go back").performClick(); assertTrue(back)
    }
    @Test fun photoSmallLargeText() {
        render(1.8f) { OnboardingPhotoContent(AuthUIStates(currentProgressStep = 2), {}, {}, {}, {}) }
        ui.onNodeWithText("Choose photo").performScrollTo().assertIsDisplayed(); capture("photo-small-large-text")
    }
}
