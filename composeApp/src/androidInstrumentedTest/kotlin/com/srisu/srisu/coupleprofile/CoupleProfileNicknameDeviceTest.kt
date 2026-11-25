package com.srisu.srisu.coupleprofile

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.srisu.srisu.features.coupleprofile.presentation.CoupleProfileNicknameContent
import com.srisu.srisu.features.settings.SettingsPartner
import com.srisu.srisu.features.settings.SettingsState
import com.srisu.srisu.theme.SriSuTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/**
 * Uses a real Android dialog/window. Run this class again with the emulator resized to
 * 320 x 640 and 640 x 320 dp to exercise dialog constraints, not just a narrow content box.
 * Robolectric's dialog measurement did not settle for this editor; these assertions
 * intentionally run on Android instead of suppressing idle checks or changing the UI.
 */
class CoupleProfileNicknameDeviceTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val partner=SettingsPartner(2,"Sam",coupleId=1,connectionId=3,revision=2,nicknameRevision=1)
    private val state=mutableStateOf(SettingsState(loading=false,loaded=true,partner=partner))
    private val requests=mutableListOf<Pair<SettingsPartner,String>>()
    private var retries=0
    private fun show(scale:Float=1f,accept:Boolean=true) {
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        compose.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density,scale),
                androidx.compose.ui.platform.LocalCursorBlinkEnabled provides false,
            ) {
                SriSuTheme(darkTheme=true) {Surface {Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp)) {
                    CoupleProfileNicknameContent(1,state.value,onSave={expected,value ->
                        requests += expected to value
                        state.value=if(accept) state.value.copy(partner=expected.copy(nickname=value.takeIf {it.isNotBlank()},
                            nicknameRevision=expected.nicknameRevision!!+1),nicknameSaved=state.value.nicknameSaved+1)
                        else state.value.copy(nicknameSaving=true)
                    },onRetry={retries++})
                }}}
            }
        }
    }
    private fun open()=compose.onNodeWithTag("couple-profile-nickname").performScrollTo().performClick()

    @Test fun saveAndRemoveUpdateTheCoupleProfileRow() {
        show();open()
        compose.onNodeWithTag("partner-nickname").performScrollTo().performTextInput("Moonbeam")
        compose.onNodeWithText("Save",substring=false).performClick()
        compose.onNodeWithTag("partner-nickname").assertDoesNotExist()
        compose.onNodeWithText("Moonbeam").assertIsDisplayed()
        assertEquals(listOf(partner to "Moonbeam"),requests)
        open()
        compose.onNodeWithTag("partner-nickname").assertTextContains("Moonbeam")
        compose.onNodeWithText("Remove nickname").performScrollTo().performClick()
        compose.onNodeWithText("Add a nickname for Sam").assertIsDisplayed()
        assertEquals("",requests.last().second);assertEquals(2,requests.size)
    }

    @Test fun failedSaveKeepsDraftAndRelationshipSwitchClosesEditor() {
        show(accept=false);open()
        compose.onNodeWithTag("partner-nickname").performScrollTo().performTextInput("Moonbeam")
        compose.onNodeWithText("Save",substring=false).performClick()
        compose.onNodeWithText("Saving…").assertIsNotEnabled()
        compose.runOnIdle {state.value=state.value.copy(nicknameSaving=false,nicknameError="Couldn’t save. Try again.")}
        compose.onNodeWithTag("partner-nickname").assertTextContains("Moonbeam")
        compose.onNodeWithText("Save",substring=false).assertIsEnabled()
        compose.runOnIdle {state.value=state.value.copy(partner=partner.copy(coupleId=9,connectionId=10))}
        compose.onNodeWithTag("couple-profile-nickname").assertDoesNotExist()
        compose.onNodeWithTag("partner-nickname").assertDoesNotExist()
        assertEquals(1,requests.size)
    }

    @Test fun loadingFailureAndUnpairedHaveDistinctControls() {
        state.value=SettingsState();show()
        compose.onNodeWithTag("couple-profile-nickname").assertIsNotEnabled()
        compose.onNodeWithText("Checking your connection…").assertIsDisplayed()
        compose.runOnIdle {state.value=SettingsState(loading=false,error="Offline")}
        compose.onNodeWithTag("couple-profile-nickname").performClick()
        assertEquals(1,retries)
        compose.onNodeWithTag("partner-nickname").assertDoesNotExist()
        compose.runOnIdle {state.value=SettingsState(loading=false,loaded=true)}
        compose.onNodeWithTag("couple-profile-nickname").assertDoesNotExist()
    }

    @Test fun largeTextKeepsEntryAndSaveReachable() {
        state.value=state.value.copy(partner=partner.copy(name="Annapurna Alexandra Shrestha"))
        show(scale=1.8f)
        compose.onNodeWithText("Add a nickname for Annapurna Alexandra Shrestha").assertIsDisplayed()
        open();compose.onNodeWithTag("partner-nickname").performScrollTo().performTextInput("Sunshine")
        compose.onNodeWithText("Save",substring=false).assertIsDisplayed().performClick()
        compose.onNodeWithText("Sunshine").assertIsDisplayed()
    }

    @Test fun editorActionsRemainReachable() {
        show();open()
        compose.onNodeWithTag("partner-nickname").performScrollTo().performTextInput("Moonbeam")
        compose.onNodeWithText("Save",substring=false).assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Cancel").assertIsDisplayed().performClick()
        compose.onNodeWithTag("partner-nickname").assertDoesNotExist()
        assertTrue(requests.isEmpty())
    }
}
