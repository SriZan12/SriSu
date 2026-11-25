package com.srisu.srisu.features.profile

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.srisu.srisu.features.auth.data.remote.response.InterestResponse.Interest
import com.srisu.srisu.features.auth.presentation.screen.profilesetup.OnboardingPhotoContent
import com.srisu.srisu.features.auth.presentation.state.AuthUIStates
import com.srisu.srisu.features.home.profile.presentation.screen.InterestScreen
import com.srisu.srisu.theme.SriSuTheme
import com.srisu.srisu.utils.*
import org.junit.*
import org.junit.Assert.*

/** Installed production composables, native picker and IME, synthetic state only. */
class ProfileRepairDeviceTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun keyboardShown() = android.os.ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand("dumpsys input_method")
    ).bufferedReader().use { it.readText().contains("mInputShown=true") }
    @Test fun interestsKeepsDoneAboveKeyboardAndSystemBackKeepsSelection() {
        var saved = false; var returned = false
        ui.activityRule.scenario.onActivity { WindowCompat.setDecorFitsSystemWindows(it.window, false) }
        ui.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) { SriSuTheme {
                InterestScreen(listOf(Interest(id = 4, name = "Long walks and little adventures")),
                    onCancel = { returned = true }, onInterestSelected = { saved = it?.any { row -> row?.interest == 4 && row.removed != true } == true })
            } }
        }
        ui.onNodeWithText("Search interests").performTouchInput { click() }
        ui.waitUntil(10_000) { keyboardShown() }
        ui.onNodeWithText("Search interests").performTextInput("walk")
        ui.onNodeWithText("Long walks and little adventures").performScrollTo().performTouchInput { click() }
        ui.onNodeWithText("Done").assertIsDisplayed()
        val bounds = ui.onNodeWithText("Done").fetchSemanticsNode().boundsInWindow
        ui.runOnIdle {
            val decor = ui.activity.window.decorView
            val ime = decor.rootWindowInsets.getInsets(android.view.WindowInsets.Type.ime()).bottom
            assertTrue(ime > 0); assertTrue(bounds.bottom <= decor.height - ime + 1)
        }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        ui.waitUntil(10_000) { !keyboardShown() }
        assertFalse(returned)
        ui.onNodeWithText("Done").performTouchInput { click() }; assertTrue(saved)
        ui.onNodeWithContentDescription("Go back").performTouchInput { click() }; assertTrue(returned)
    }
    @Test fun onboardingOpensSystemPhotoPickerAndCancellationKeepsStep() {
        var result: List<String?>? = null; var failed = false
        ui.setContent { SriSuTheme {
            val picker = rememberGalleryManager(onResult = { result = it }, mediaType = MediaType.IMAGE_ONLY,
                isMultiple = false, onError = { failed = true })
            OnboardingPhotoContent(AuthUIStates(currentProgressStep = 2), {}, {}, picker::launch, {})
        } }
        ui.onNodeWithText("Choose photo").performScrollTo().performTouchInput { click() }
        // Await the native picker window, not an arbitrary delay.
        ui.waitUntil(10_000) { !ui.activity.hasWindowFocus() }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        ui.waitUntil(10_000) { ui.activity.hasWindowFocus() }
        ui.onNodeWithText("Choose photo").performScrollTo().assertIsDisplayed()
        assertFalse(failed); assertTrue(result.isNullOrEmpty())
    }
}
