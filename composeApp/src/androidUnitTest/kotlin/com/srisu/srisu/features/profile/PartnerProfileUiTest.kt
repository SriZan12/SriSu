package com.srisu.srisu.features.profile

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import com.srisu.srisu.features.auth.data.remote.response.User
import com.srisu.srisu.features.chat.data.remote.response.FindYourPartnerResponse
import com.srisu.srisu.features.chat.presentation.findpartner.screen.PartnerSearchResult
import com.srisu.srisu.features.chat.presentation.findpartner.state.FindPartnerState
import com.srisu.srisu.features.chat.presentation.findpartner.state.PartnerSearchStatus
import com.srisu.srisu.features.home.profile.presentation.screen.PartnerProfileContent
import com.srisu.srisu.features.home.profile.presentation.state.ProfileUIState
import com.srisu.srisu.features.home.profile.presentation.vm.ProfileViewModel
import com.srisu.srisu.theme.SriSuTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse

@OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class, coil3.annotation.DelicateCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PartnerProfileUiTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val basic = User(id = 2, fullName = "Maya Sharma", username = "maya")
    private fun show(state: ProfileUIState, dark: Boolean = false, scale: Float = 1f, onBack: () -> Unit = {}) {
        compose.setContent {
            CompositionLocalProvider(androidx.compose.ui.platform.LocalInspectionMode provides true) {
                org.jetbrains.compose.resources.PreviewContextConfigurationEffect()
            }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                SriSuTheme(darkTheme = dark) { PartnerProfileContent(state, onBack) }
            }
        }
        compose.waitForIdle()
    }
    private fun capture(name: String) {
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            File("build/reports/partner-profile/$name.png").apply { parentFile?.mkdirs() }.outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }
    @Test fun avatarOpensSelectedProfileWithoutSendingAnInvitation() {
        var selected: Long? = null
        var sends = 0
        compose.setContent {
            CompositionLocalProvider(androidx.compose.ui.platform.LocalInspectionMode provides true) {
                org.jetbrains.compose.resources.PreviewContextConfigurationEffect()
            }
            SriSuTheme {
                PartnerSearchResult(
                    FindPartnerState(searchStatus = PartnerSearchStatus.Found, invitationStatusChecked = true,
                        partnerResponse = FindYourPartnerResponse(id = 2, fullName = "Maya Sharma")),
                    onConnect = { sends++ }, onCancel = {}, onRetry = {}, onCheckInvitation = {}, onProfile = { selected = it },
                )
            }
        }
        compose.onNodeWithContentDescription("View Maya Sharma’s profile").performClick()
        assertEquals(2L, selected)
        assertEquals(0, sends)
        compose.onNodeWithText("Connect").performClick()
        assertEquals(1, sends)
    }
    @Test fun basicProfileHasNoInventedDetails() {
        show(ProfileUIState(basic, isLoading = false))
        compose.onNodeWithText("Maya Sharma").assertIsDisplayed()
        compose.onNodeWithText("@maya").assertIsDisplayed()
        compose.onNodeWithText("Bio and interests are private until you’re connected.").assertIsDisplayed()
        listOf("Some City", "Some Country", "(null)", "About", "Interests", "Photos").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
        capture("basic-light")
    }
    @Test @Config(qualifiers = "w320dp-h640dp-xhdpi") fun longNamesAndLargeTextFitASmallDarkScreen() {
        show(ProfileUIState(basic.copy(fullName = "Annapurna Alexandra Shrestha", username = "annapurna.alexandra"), false), dark = true, scale = 1.6f)
        compose.onNodeWithText("Annapurna Alexandra Shrestha").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Bio and interests are private until you’re connected.").performScrollTo().assertIsDisplayed()
        capture("small-dark-large-text")
    }
    @Test @Config(qualifiers = "w640dp-h320dp-land-xhdpi") fun richerAuthorizedSnapshotScrollsWithoutRemovedContent() {
        show(ProfileUIState(basic.copy(bio = "Coffee, walks and little adventures.",
            userInterests = listOf(User.UserInterest(name = "Mountain walks"), User.UserInterest(name = "Reading together"),
                User.UserInterest(name = "Removed interest", removed = true))), false, detailsVisible = true, connectionState = "available"))
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Mountain walks"))
        compose.onNodeWithText("Mountain walks").assertIsDisplayed()
        compose.onNodeWithText("Removed interest").assertDoesNotExist()
        capture("landscape-interests")
    }
    @Test fun unavailableSelectionOffersReturnInsteadOfAnEmptyProfile() {
        var returned = false
        show(ProfileUIState(isLoading = false), onBack = { returned = true })
        compose.onNodeWithText("Profile unavailable").assertIsDisplayed()
        compose.onNodeWithText("Go back").performClick()
        assertEquals(true, returned)
        capture("unavailable")
    }
    @Test fun extraPhotosNeverAppearInThePartnerProfile() {
        show(ProfileUIState(basic.copy(userPhotos = listOf(User.UserPhoto(photo = "file:///private-photo.jpg"))),
            false, detailsVisible = true))
        compose.onNodeWithText("Photos").assertDoesNotExist()
        compose.onNodeWithText("No bio or interests added yet.").assertIsDisplayed()
    }
    @Test fun failedPhotoOffersRetryAndKeepsIdentityVisible() {
        show(ProfileUIState(basic.copy(profilePhoto = "file:///missing-synthetic-profile.jpg"), false))
        compose.waitUntil(10000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            compose.onAllNodesWithText("Retry").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Maya Sharma").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        compose.waitUntil(10000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            compose.onAllNodesWithText("Retry").fetchSemanticsNodes().isNotEmpty()
        }
        capture("photo-error")
    }
    @Test fun loadingDoesNotPresentMissingDataAsARealProfile() {
        show(ProfileUIState())
        compose.onNodeWithText("Loading profile…").assertIsDisplayed()
        compose.onNodeWithText("Profile unavailable").assertDoesNotExist()
    }
    @Test fun loadedProfileConnectUsesSelectedUserAndShowsRealDetails() {
        var selected: Long? = null
        compose.setContent {
            CompositionLocalProvider(androidx.compose.ui.platform.LocalInspectionMode provides true) {
                org.jetbrains.compose.resources.PreviewContextConfigurationEffect()
            }
            SriSuTheme {
                PartnerProfileContent(ProfileUIState(basic.copy(bio = "Coffee, walks and little adventures.",
                    userInterests = listOf(User.UserInterest(name = "Photography"), User.UserInterest(name = "Walking"))),
                    false, detailsVisible = true, connectionState = "available"), onBack = {},
                    onAction = { user, action -> assertEquals("available", action); selected = user.id })
            }
        }
        compose.onNodeWithText("Connect").performClick()
        assertEquals(2L, selected)
        compose.onNodeWithText("Coffee, walks and little adventures.").assertIsDisplayed()
        capture("loaded-light")
    }
    @Test @Config(qualifiers = "w320dp-h640dp-xhdpi") fun sharingSwitchAndSaveStayReachableAtLargeText() {
        var sharing by androidx.compose.runtime.mutableStateOf(false)
        var saves = 0
        compose.setContent {
            CompositionLocalProvider(androidx.compose.ui.platform.LocalInspectionMode provides true) {
                org.jetbrains.compose.resources.PreviewContextConfigurationEffect()
            }
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.6f)) {
                SriSuTheme {
                    com.srisu.srisu.features.home.profile.presentation.screen.EditProfileForm(
                        com.srisu.srisu.features.home.profile.presentation.state.EditProfileUIState(
                            fullName="Maya", userName="maya", sharePartnerDetails=sharing),
                        com.srisu.srisu.features.home.profile.presentation.screen.ProfileFormActions(
                            sharePartnerDetails={ sharing=it }, save={ saves++ }))
                }
            }
        }
        compose.onNodeWithContentDescription("Share bio and interests in Find Partner").performScrollTo().assertIsDisplayed().performClick().assertIsOn().assertIsDisplayed()
        compose.onNodeWithText("People who find you can see these details. Extra photos stay private. Save to apply.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Share bio and interests in Find Partner").assertIsDisplayed()
        capture("sharing-small-large-text")
        compose.onNodeWithText("Update Profile").performScrollTo().performClick()
        assertEquals(1, saves)
    }
    @Test fun networkErrorOffersRetry() {
        var retries = 0
        compose.setContent {
            CompositionLocalProvider(androidx.compose.ui.platform.LocalInspectionMode provides true) {
                org.jetbrains.compose.resources.PreviewContextConfigurationEffect()
            }
            SriSuTheme { PartnerProfileContent(ProfileUIState(isLoading = false,
                error = com.srisu.srisu.core.data.remote.ApiError(
                    com.srisu.srisu.core.data.remote.NetworkAPIResult.ErrorType.NETWORK, "network", "Check your connection.")),
                onBack = {}, onRetry = { retries++ }) }
        }
        compose.onNodeWithText("Couldn’t load profile").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        assertEquals(1, retries)
        capture("network-error")
    }
}
