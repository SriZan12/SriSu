package com.srisu.srisu.navigation

import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.liftric.kvault.KVault
import com.srisu.srisu.MainActivity
import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.utils.Constants
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Installed app against explicitly isolated synthetic accounts. No personal account is opened. */
class NavigationJourneyDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun waitFor(matcher: SemanticsMatcher) = compose.waitUntil(30_000) {
        compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
    }
    private fun back() { instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); compose.waitForIdle() }
    private fun keyboardShown() = android.os.ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand("dumpsys input_method")
    ).bufferedReader().use { it.readText().contains("mInputShown=true") }
    private fun seed(person: Int = 0): KVault {
        val context = instrumentation.targetContext
        val file = File(context.filesDir, "navigation-app-fixture.json")
        assumeTrue("Requires isolated navigation QA fixture", file.isFile)
        val fixture = JSONObject(file.readText())
        require(fixture.getString("scope") == "isolated-navigation-qa")
        require(fixture.getString("base_url") == "http://127.0.0.1:8940/")
        require(ApiEnvironment.configured().baseUrl == fixture.getString("base_url"))
        val session = fixture.getJSONArray("people").getJSONObject(person).getJSONObject("session")
        val allowed = setOf("+9779800000241", "+9779800000242", "+9779800000263", "+9779800000264")
        require(session.getString("phone_number") in allowed)
        val vault = KVault(context, Constants.Auth.SESSION_FILE)
        vault.string(Constants.Auth.SESSION_KEY)?.let { require(JSONObject(it).optString("phone_number") in allowed) }
        assertTrue(vault.set(Constants.Auth.SESSION_KEY, session.toString()))
        return vault
    }
    @Test fun profileExplorePreferencesSettingsAndSignOutHaveWorkingReturnPaths() {
        val vault = seed()
        try { ActivityScenario.launch(MainActivity::class.java).use {
            waitFor(hasText("You & Aatithi"))
            if (compose.onAllNodesWithText("Not now").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText("Not now").performClick()
            compose.onNodeWithText("Profile", useUnmergedTree = true).performClick()
            waitFor(hasText("Update Profile"))
            val bitmap=instrumentation.uiAutomation.takeScreenshot()
            File(instrumentation.targetContext.filesDir,"profile-native.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
            val name=compose.onNode(hasSetTextAction() and hasText("Srijan"))
            name.performScrollTo().performClick().performTextReplacement("Unsaved QA name")
            compose.waitUntil(10_000) { keyboardShown() }
            back() // Dismiss the keyboard before testing the route's Back.
            compose.waitUntil(10_000) { !keyboardShown() }
            back()
            waitFor(hasText("Discard profile changes?"))
            compose.onNodeWithText("Keep editing").performClick()
            compose.onNode(hasSetTextAction() and hasText("Unsaved QA name")).assertExists()
            compose.onNodeWithTag("profile-name").performClick()
            compose.waitUntil(10_000) { keyboardShown() }
            compose.onNodeWithTag("profile-name").performImeAction()
            compose.onNodeWithTag("profile-username").assertIsFocused().performImeAction()
            compose.onNodeWithTag("profile-bio").assertIsFocused().performImeAction()
            compose.waitUntil(10_000) { !keyboardShown() }
            back()
            waitFor(hasText("Discard profile changes?"))
            compose.onNodeWithText("Discard").performClick()
            waitFor(hasText("You & Aatithi"))
            compose.onNodeWithText("Profile",useUnmergedTree=true).performClick()
            waitFor(hasSetTextAction() and hasText("Srijan"))
            compose.onNodeWithContentDescription("Explore SriSu").performClick()
            waitFor(hasText("Set your city & interests"))
            compose.onNodeWithText("Set your city & interests").performClick()
            waitFor(hasText("Update Profile"))
            compose.onNodeWithContentDescription("navigate back icon").assertIsDisplayed().performClick()
            waitFor(hasText("Set your city & interests"))
            compose.onNodeWithContentDescription("Go back").assertIsDisplayed().performClick()
            waitFor(hasContentDescription("Settings"))
            compose.onNodeWithContentDescription("Settings").performClick()
            waitFor(hasText("Notifications",substring=false))
            compose.onNodeWithText("Notifications",substring=false).performClick()
            waitFor(hasText("Choose your alerts. Sounds and lock-screen previews are controlled by your device."))
            compose.onNodeWithTag("settings-detail-list").performScrollToNode(hasText("Share crash diagnostics"))
            compose.onNodeWithText("Share crash diagnostics").assertIsDisplayed()
            compose.onNodeWithContentDescription("Go back").performClick()
            waitFor(hasText("Settings",substring=false))
            compose.onNodeWithContentDescription("Go back").performClick()
            waitFor(hasText("Update Profile"))
            compose.onNodeWithText("Update Profile").performScrollTo().performClick()
            waitFor(hasText("Profile updated"))
            back()
            compose.onNodeWithText("Sign out").performScrollTo().performClick()
            waitFor(hasText("Sign out?"))
            compose.onNodeWithText("Cancel").performClick()
            compose.onNodeWithText("Update Profile").assertExists()
            compose.onNodeWithText("Sign out").performScrollTo().performClick()
            compose.onAllNodesWithText("Sign out").onLast().performClick()
            waitFor(hasText("Phone number", substring = true))
            compose.onAllNodesWithText("You & Aatithi").assertCountEquals(0)
        } } finally { vault.clear() }
    }
    @Test fun profileTextAndPhotoUpdatesUseTheRealBackend() {
        val vault=seed()
        try {ActivityScenario.launch(MainActivity::class.java).use {
            waitFor(hasText("You & Aatithi"))
            kotlinx.coroutines.runBlocking {
                val repo=org.koin.core.context.GlobalContext.get().get<com.srisu.srisu.features.home.profile.domain.repository.ProfileRepository>()
                val loaded=repo.getProfile().result as com.srisu.srisu.core.data.remote.NetworkAPIResult.Success
                val user=requireNotNull(loaded.response?.user)
                val dto=com.srisu.srisu.features.home.profile.data.dto.ProfileUpdateDTO(fullName=user.fullName,username=user.username,
                    bio="Synthetic profile save verification",country=user.country,city=user.city,userInterests=user.userInterests)
                val text=repo.sendUpdateProfileRequest(user.id,dto,null,emptyList()).result
                assertTrue("Text update succeeds",text is com.srisu.srisu.core.data.remote.NetworkAPIResult.Success)
                val bitmap=android.graphics.Bitmap.createBitmap(32,32,android.graphics.Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.rgb(180,195,220)) // theme-audit: image pixels — synthetic upload, not UI styling
                val bytes=java.io.ByteArrayOutputStream().also {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}.toByteArray()
                bitmap.recycle()
                val photo=com.srisu.srisu.utils.MediaFile(id=null,fileName="synthetic.png",mimeType="image/png",fileBytes=bytes)
                val uploaded=repo.sendUpdateProfileRequest(user.id,dto,photo,emptyList()).result
                assertTrue("Photo update succeeds",uploaded is com.srisu.srisu.core.data.remote.NetworkAPIResult.Success)
                val response=(uploaded as com.srisu.srisu.core.data.remote.NetworkAPIResult.Success).response
                assertFalse(response?.user?.profilePhoto.isNullOrBlank())
            }
        }} finally {vault.clear()}
    }
    @Test fun challengeCatalogueDetailAndBackRetainReadingPosition() {
        val vault = seed()
        try { ActivityScenario.launch(MainActivity::class.java).use {
            waitFor(hasText("You & Aatithi"))
            val entry = hasText("Choose your first challenge →") or hasText("Choose another →") or hasText("Open challenge →")
            compose.onNode(hasScrollAction()).performScrollToNode(entry)
            compose.onNode(entry).performClick()
            waitFor(hasText("Challenges"))
            if (compose.onAllNodesWithText("All activities").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText("All activities").performClick()
            waitFor(hasText("Little things, together"))
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("A small welcome"))
            compose.onNodeWithText("A small welcome").performClick()
            waitFor(hasText("All activities"))
            compose.onNodeWithText("All activities").assertIsDisplayed()
            back()
            compose.onNodeWithText("A small welcome").assertIsDisplayed()
            compose.onAllNodesWithText("All activities").assertCountEquals(0)
            compose.onNodeWithContentDescription("Go back").performClick()
            waitFor(hasText("Your challenge"))
        } } finally { vault.clear() }
    }
    @Test fun incomingPartnerPreviewAcceptanceAndMembershipRefreshKeepChatOpen() {
        val vault = seed(3)
        try { ActivityScenario.launch(MainActivity::class.java).use {
            waitFor(hasText("Chat"))
            compose.onNodeWithText("Chat", useUnmergedTree = true).performClick()
            waitFor(hasText("Accept"))
            compose.onNodeWithText("Navigation Solo").performScrollTo().performClick()
            waitFor(hasContentDescription("navigate back icon"))
            compose.onNodeWithContentDescription("navigate back icon").assertIsDisplayed().performClick()
            waitFor(hasText("Accept"))
            compose.onNodeWithText("Accept").performScrollTo().performClick()
            waitFor(hasText("View our space"))
            compose.onNodeWithText("Open conversation").performScrollTo().performClick()
            waitFor(hasContentDescription("Message"))
            // Exercise the actual 30-second membership poll, not a mocked clock.
            val until = android.os.SystemClock.uptimeMillis() + 33_000
            compose.waitUntil(40_000) { android.os.SystemClock.uptimeMillis() >= until }
            compose.onNodeWithContentDescription("Message").assertExists()
            compose.onNodeWithContentDescription("Back to conversations").performClick()
            waitFor(hasText("You & Navigation Solo"))
            compose.onAllNodesWithText("View our space").assertCountEquals(0)
        } } finally { vault.clear() }
    }
    @Test fun unpairedChatAndQuestionsLeadToPartnerSearchAndBack() {
        val vault = seed(2)
        try { ActivityScenario.launch(MainActivity::class.java).use {
            waitFor(hasText("Chat", substring = false))
            compose.onNodeWithText("Chat", useUnmergedTree = true).performClick()
            waitFor(hasText("Find your partner"))
            back()
            waitFor(hasText("Questions", substring = false))
            compose.onNode(hasText("Questions") and isSelectable()).performClick()
            waitFor(hasText("Connect with your partner"))
            compose.onNodeWithText("Connect with your partner").performScrollTo().performClick()
            waitFor(hasText("Search their phone number. Works if they already have SriSu."))
            back()
            waitFor(hasText("Connect with your partner"))
        } } finally { vault.clear() }
    }
}
