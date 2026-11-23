package com.srisu.srisu.components

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.test.core.app.ActivityScenario
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.srisu.srisu.theme.SriSuTheme
import com.srisu.srisu.utils.Country
import org.junit.Test
import java.io.File

/** Synthetic, offline fixture using the production phone control and modal.
 * Uses the real frame clock and device gestures, never Compose test-clock advancement.
 * Records observations, not flaky device-specific pass/fail timing thresholds.
 */
class CountryPickerPerformanceTest {
    private val device by lazy { UiDevice.getInstance(instrumentation) }
    private fun idle() { instrumentation.uiAutomation.waitForIdle(150, 5000) }
    private fun node(selector: BySelector): UiObject2 =
        requireNotNull(device.wait(Until.findObject(selector), 5000)) { "Missing UI: $selector" }
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val output by lazy {
        File(instrumentation.targetContext.getExternalFilesDir(null), "country-performance").apply { mkdirs() }
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .bufferedReader().use { it.readText() }

    private fun resetFrames() {
        idle()
        shell("dumpsys gfxinfo ${instrumentation.targetContext.packageName} reset")
    }

    private fun recordFrames(phase: String) {
        idle()
        val report = shell("dumpsys gfxinfo ${instrumentation.targetContext.packageName} framestats")
        File(output, "$phase.txt").writeText(report)
        println("COUNTRY_FRAME_REPORT $phase ${File(output, "$phase.txt")}")
    }

    private fun screenshot(name: String) {
        idle()
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun openingScrollingAndSearch() {
        output.listFiles()?.forEach { it.delete() }
        var show by mutableStateOf(false)
        var selected by mutableStateOf(Country.getCountryModelFromCode("NP")!!)
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
        scenario.onActivity { activity -> activity.setContent {
            SriSuTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.safeDrawingPadding().padding(20.dp)) {
                        PhoneNumberCompo(
                            modifier = Modifier.fillMaxWidth(),
                            countryPrefix = selected.prefix!!,
                            countryCode = selected.code!!,
                            phoneNumber = "",
                            backgroundColor = MaterialTheme.colorScheme.surface,
                            updatePhoneNumber = {},
                            onShowCountryList = { show = true },
                        )
                    }
                    CountrySelectionBottomSheet(
                        modifier = Modifier.semantics { testTagsAsResourceId = true },
                        countries = Country.getAllCountriesFromJson(),
                        show = show,
                        selectedCode = selected.code,
                        onCountrySelected = { selected = it; show = false },
                        onClose = { show = false },
                    )
                }
            }
        }
        }
        idle()
        screenshot("phone-country-control")
        resetFrames()
        val coldStart = System.nanoTime()
        node(By.desc("Nepal")).click()
        node(By.res("country-search"))
        idle()
        val coldIdleMs = (System.nanoTime() - coldStart) / 1_000_000.0
        recordFrames("first-open")
        screenshot("country-picker")

        val warmIdleMs = mutableListOf<Double>()
        repeat(3) { iteration ->
            node(By.desc("Close country picker")).click()
            resetFrames()
            val start = System.nanoTime()
            node(By.desc("Nepal")).click()
            node(By.res("country-search"))
            idle()
            warmIdleMs += (System.nanoTime() - start) / 1_000_000.0
            recordFrames("warm-open-${iteration + 1}")
        }

        resetFrames()
        val bounds = node(By.res("country-list")).visibleBounds
        val top = bounds.top + bounds.height() / 5
        val bottom = bounds.bottom - bounds.height() / 5
        repeat(16) { device.swipe(bounds.centerX(), bottom, bounds.centerX(), top, 70) }
        recordFrames("scroll")

        // Isolate search from any outstanding fling/nested sheet gesture.
        scenario.onActivity { show = false }
        idle()
        node(By.desc("Nepal")).click()
        node(By.res("country-search"))
        idle()
        resetFrames()
        listOf("C", "Cy", "Cyp", "Cypr", "Cypru", "Cyprus", "", "+", "+9", "+97", "+977").forEach {
            node(By.res("country-search")).text = it
            idle()
        }
        screenshot("search-result")
        device.dumpWindowHierarchy(File(output, "search-hierarchy.xml"))
        node(By.text("Nepal"))
        recordFrames("search")

        repeat(100) { Country.search(Country.getAllCountriesFromJson(), "turkiye", true) }
        val filterStart = System.nanoTime()
        repeat(1000) { Country.search(Country.getAllCountriesFromJson(), if (it % 2 == 0) "turkiye" else "+977", true) }
        val filterMs = (System.nanoTime() - filterStart) / 1_000_000.0
        File(output, "timings.json").writeText(
            """{"firstOpenToTestIdleMs":$coldIdleMs,"warmOpenToTestIdleMs":$warmIdleMs,"thousandFiltersMs":$filterMs}"""
        )
        println("COUNTRY_TIMINGS ${File(output, "timings.json").readText()}")
        node(By.desc("Close country picker")).click()
        }
    }
}
