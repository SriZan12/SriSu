package com.srisu.srisu.components

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import com.srisu.srisu.theme.SriSuTheme
import com.srisu.srisu.utils.Country
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Config
import java.io.File

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
class CountryPickerUiTest {
    @get:Rule val ui = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private var selected: String? = null
    private var closed = false
    private fun show(phone: Boolean = true, scale: Float = 1f, dark: Boolean = false, empty: Boolean = false) {
        ui.setContent {
            CompositionLocalProvider(androidx.compose.ui.platform.LocalInspectionMode provides true) {
                org.jetbrains.compose.resources.PreviewContextConfigurationEffect()
            }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) { SriSuTheme(darkTheme = dark) {
                CountryPickerContent(if (empty) emptyList() else Country.getAllCountriesFromJson(), "NP", phone, { selected = it.code }, { closed = true }, Modifier.fillMaxSize())
            } }
        }
        ui.waitForIdle()
    }
    private fun capture(name: String) = ui.runOnIdle {
        val view=ui.activity.window.decorView
        val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        File("build/reports/countries/android-$name.png").apply { parentFile?.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
    }
    @Test fun searchPrefixSelectAndClose() {
        show(); capture("standard")
        ui.onNodeWithTag("country-search").performTextInput("+977")
        ui.onNodeWithText("Nepal").assertIsDisplayed()
        ui.onNodeWithTag("country-NP").assertIsSelected().performClick()
        assertEquals("NP",selected); capture("selected")
        ui.onNodeWithContentDescription("Close country picker").performClick(); assertTrue(closed)
    }
    @Test @Config(qualifiers="w320dp-h640dp-xhdpi") fun smallDarkLargeTextAndLongNames() {
        show(scale=1.8f,dark=true)
        ui.onNodeWithTag("country-search").performTextInput("Saint Vincent")
        ui.onNodeWithText("St. Vincent & Grenadines").assertIsDisplayed()
        capture("small-dark-large-text")
        ui.onNodeWithTag("country-VC").performClick();assertEquals("VC",selected)
    }
    @Test @Config(qualifiers="w640dp-h320dp-land-xhdpi") fun landscapeLocationModeHasNoCallingCodes() {
        show(phone=false)
        ui.onNodeWithTag("country-search").performTextInput("Bouvet")
        ui.onNodeWithText("Bouvet Island").assertIsDisplayed()
        ui.onNodeWithTag("country-BV").performClick();assertEquals("BV",selected)
        capture("landscape-location")
    }
    @Test fun noResultsCanBeClearedAndListScrollsToLastCountry() {
        show();ui.onNodeWithTag("country-search").performTextInput("zzzz")
        ui.onNodeWithText("No matching countries").assertIsDisplayed()
        ui.onNodeWithContentDescription("Clear country search").performClick()
        ui.onNodeWithTag("country-list").performScrollToNode(hasTestTag("country-ZW"))
        ui.onNodeWithText("Zimbabwe").assertIsDisplayed()
        ui.onNodeWithTag("country-search").performTextInput("curacao")
        ui.onNodeWithText("Curaçao").assertIsDisplayed()
    }
    @Test fun emptyListStillHasReachableClose() {
        show(empty=true);ui.onNodeWithText("No countries available").assertIsDisplayed()
        ui.onNodeWithContentDescription("Close country picker").performClick();assertTrue(closed)
    }
}
