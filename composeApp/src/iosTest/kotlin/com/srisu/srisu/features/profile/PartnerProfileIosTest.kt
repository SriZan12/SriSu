package com.srisu.srisu.features.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import com.srisu.srisu.features.auth.data.remote.response.User
import com.srisu.srisu.features.home.profile.presentation.screen.PartnerProfileContent
import com.srisu.srisu.features.home.profile.presentation.state.ProfileUIState
import com.srisu.srisu.theme.SriSuTheme
import kotlinx.cinterop.*
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import platform.Foundation.*
import kotlin.test.Test
import kotlin.test.assertTrue

/** Actual iOS Compose runtime with synthetic snapshots; no live account or network. */
@OptIn(ExperimentalTestApi::class)
class PartnerProfileIosTest {
    private fun check(width: Int, height: Int, scale: Float, dark: Boolean, name: String) =
        runSkikoComposeUiTest(size = Size(width * 2f, height * 2f), density = Density(2f, scale)) {
            var returned = false
            var connected = false
            setContent {
                SriSuTheme(darkTheme = dark) {
                    PartnerProfileContent(ProfileUIState(User(id = 2, fullName = "Annapurna Alexandra Shrestha", username = "annapurna", bio = "Coffee, walks and little adventures.",
                        userInterests = listOf(User.UserInterest(name = "Photography"), User.UserInterest(name = "Walking"))),
                        false, detailsVisible = true, connectionState = "available"), onBack = { returned = true },
                        onAction = { user, action -> connected = user.id == 2L && action == "available" })
                }
            }
            onNodeWithText("Annapurna Alexandra Shrestha").performScrollTo().assertIsDisplayed()
            onNodeWithText("Connect").performScrollTo().performClick()
            assertTrue(connected)
            onNodeWithText("Coffee, walks and little adventures.").performScrollTo().assertIsDisplayed()
            val png = Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes
            writePng(name, png)
            onNodeWithContentDescription("Go back").performClick()
            assertTrue(returned)
        }
    @Test fun standard() = check(390, 844, 1f, false, "standard")
    @Test fun smallDarkLargeText() = check(320, 640, 1.6f, true, "small-dark-large-text")
    @Test fun landscape() = check(640, 320, 1f, false, "landscape")
    @Test fun missingSelectionCanReturn() = runSkikoComposeUiTest {
        var returned = false
        setContent { SriSuTheme { PartnerProfileContent(ProfileUIState(isLoading = false), onBack = { returned = true }) } }
        onNodeWithText("Profile unavailable").assertIsDisplayed()
        onNodeWithText("Go back").performClick()
        assertTrue(returned)
    }
    @Test fun sharingSwitchAndSaveRemainReachable() = runSkikoComposeUiTest(size = Size(640f, 1280f), density = Density(2f, 1.6f)) {
        var sharing by androidx.compose.runtime.mutableStateOf(false)
        var saved = false
        setContent { SriSuTheme {
            com.srisu.srisu.features.home.profile.presentation.screen.EditProfileForm(
                com.srisu.srisu.features.home.profile.presentation.state.EditProfileUIState(
                    fullName="Maya", userName="maya", sharePartnerDetails=sharing),
                com.srisu.srisu.features.home.profile.presentation.screen.ProfileFormActions(
                    sharePartnerDetails={ sharing=it }, save={ saved=true }))
        } }
        onNodeWithContentDescription("Share bio and interests in Find Partner").performScrollTo().assertIsDisplayed().performClick().assertIsOn().assertIsDisplayed()
        val png = Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes
        writePng("sharing-small-large-text", png)
        onNodeWithText("Update Profile").performScrollTo().performClick()
        assertTrue(saved)
    }
    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    private fun writePng(name: String, bytes: ByteArray) {
        val directory = NSTemporaryDirectory() + "partner-profile"
        NSFileManager.defaultManager.createDirectoryAtPath(directory, true, null, null)
        val path = "$directory/ios-$name.png"
        bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()).writeToFile(path, true) }
        println("PARTNER_PROFILE_RENDER $path")
    }
}
