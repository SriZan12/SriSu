package com.srisu.srisu.components

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.srisu.srisu.theme.SriSuTheme
import com.srisu.srisu.utils.Country
import org.junit.*
import org.junit.Assert.*

/** Real modal/IME with synthetic state; never touches an account or sends an OTP. */
class CountryPickerDeviceTest {
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private fun keyboardShown()=android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("dumpsys input_method")).bufferedReader().use { it.readText().contains("mInputShown=true") }
    @Test fun modalKeyboardSelectionReopenAndScrolling() {
        var show by mutableStateOf(false); var selected by mutableStateOf("NP")
        ui.setContent { SriSuTheme {
            TextButton(onClick={show=true}) { Text("Choose country") }
            CountrySelectionBottomSheet(countries=Country.getAllCountriesFromJson(),show=show,selectedCode=selected,
                onCountrySelected={selected=it.code!!;show=false},onClose={show=false})
        } }
        val openedAt=System.nanoTime()
        ui.onNodeWithText("Choose country").performClick()
        ui.onNodeWithTag("country-search").assertIsDisplayed()
        println("COUNTRY_PICKER_OPEN_MS " + (System.nanoTime()-openedAt)/1_000_000.0)
        ui.onNodeWithTag("country-search").performTouchInput { click() }
        ui.waitUntil(10_000){keyboardShown()}
        ui.onNodeWithTag("country-search").performTextInput("+357")
        ui.onNodeWithTag("country-CY").assertIsDisplayed().performTouchInput { click() }
        ui.waitUntil(10_000){!keyboardShown()};assertEquals("CY",selected)
        ui.onNodeWithText("Choose country").performClick()
        repeat(5) { ui.onNodeWithTag("country-list").performTouchInput { swipeUp(durationMillis=250) } }
        val searchAt=System.nanoTime()
        repeat(1000) { Country.search(Country.getAllCountriesFromJson(), if(it%2==0) "turkiye" else "+977", true) }
        println("COUNTRY_SEARCH_1000_MS " + (System.nanoTime()-searchAt)/1_000_000.0)
        ui.onNodeWithTag("country-list").performScrollToNode(hasTestTag("country-ZW"))
        ui.onNodeWithText("Zimbabwe").assertIsDisplayed()
        ui.onNodeWithTag("country-search").performTextInput("Cyprus")
        ui.onNodeWithTag("country-CY").assertIsSelected()
        ui.onNodeWithTag("country-search").performTouchInput { click() }
        ui.waitUntil(10_000){keyboardShown()}
        // Let the native IME transition settle before sending system Back.
        instrumentation.uiAutomation.waitForIdle(150, 5000)
        UiDevice.getInstance(instrumentation).pressBack()
        ui.waitUntil(10_000){!keyboardShown()}
        ui.onNodeWithContentDescription("Close country picker").performClick()
        ui.onNodeWithText("Choose country").assertIsDisplayed();assertEquals("CY",selected)
    }
    @Test fun reselectingProfileCountryDoesNotEraseItsCity() {
        var show by mutableStateOf(true); var changes=0
        ui.setContent { SriSuTheme {
            CountrySelectionBottomSheet(countries=Country.getAllCountriesFromJson(),show=show,selectedCode="NP",callingCodes=false,
                onCountrySelected={changes++;show=false},onClose={show=false})
        } }
        ui.onNodeWithTag("country-search").performTextInput("Nepal")
        ui.onNodeWithTag("country-NP").performClick()
        ui.runOnIdle { assertFalse(show);assertEquals(0,changes) }
    }

}
