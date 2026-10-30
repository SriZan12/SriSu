package com.srisu.srisu.notifications

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.srisu.srisu.features.notifications.FirebaseIdCopyPanel
import com.srisu.srisu.theme.SriSuTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], qualifiers="w390dp-h844dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FirebaseIdCopyPanelTest {
    @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun retryCopyAndPermissionRevocationReflectTheirActualOutcomes() {
        val state=mutableStateOf(FirebaseIdState.FAILED)
        var retries=0
        var copies=0
        var succeeds=false
        compose.setContent { SriSuTheme { FirebaseIdCopyPanel(state.value, { copies++; succeeds }, { retries++ }) } }
        compose.onNodeWithText("Copy Firebase ID").assertIsNotEnabled()
        compose.onNodeWithText("Retry Firebase registration").performClick()
        compose.runOnIdle { assertEquals(1,retries); state.value=FirebaseIdState.REGISTERING }
        compose.onNodeWithText("Copy Firebase ID").assertIsNotEnabled()
        compose.onNodeWithText("Retry Firebase registration").assertDoesNotExist()
        compose.runOnIdle { state.value=FirebaseIdState.READY }
        compose.onNodeWithText("Copy Firebase ID").performClick()
        compose.onNodeWithText("Couldn’t copy the Firebase ID. Check notification permission and try again.").assertIsDisplayed()
        compose.runOnIdle { succeeds=true }
        compose.onNodeWithText("Copy Firebase ID").performClick()
        compose.onNodeWithText("Copied. Paste it into Firebase’s Send test message dialog.").assertIsDisplayed()
        compose.runOnIdle { assertEquals(2,copies); state.value=FirebaseIdState.PERMISSION_REQUIRED }
        compose.onNodeWithText("Copy Firebase ID").assertIsNotEnabled()
        compose.onNodeWithText("Copied. Paste it into Firebase’s Send test message dialog.").assertDoesNotExist()
    }

    @Test fun toolsAreAbsentInReleaseAndSignInIsRequiredInDebug() {
        val state=mutableStateOf(FirebaseIdState.HIDDEN)
        compose.setContent { SriSuTheme { FirebaseIdCopyPanel(state.value, { error("Copy must be unavailable") }, {}) } }
        compose.onNodeWithText("Firebase test notifications").assertDoesNotExist()
        compose.runOnIdle { state.value=FirebaseIdState.SIGN_IN_REQUIRED }
        compose.onNodeWithText("Sign in to get this phone’s Firebase ID.").assertIsDisplayed()
        compose.onNodeWithText("Copy Firebase ID").assertIsNotEnabled()
    }
}
