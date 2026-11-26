package com.srisu.srisu.features.moments

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.allowHardware
import com.srisu.srisu.features.moments.presentation.MomentViewerState
import com.srisu.srisu.theme.SriSuTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w390dp-h844dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(coil3.annotation.DelicateCoilApi::class)
class PublicMomentRenderTest {
    @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private var previous:ImageLoader?=null
    private var loader:ImageLoader?=null
    @After fun restore() {previous?.let {SingletonImageLoader.setUnsafe(it)};loader?.shutdown()}
    private fun show(state:State<MomentViewerState>,onState:(MomentViewerState)->Unit,onAction:(String)->Unit={},
        dark:Boolean=false,fontScale:Float=1f,qualifiers:String="w390dp-h844dp-xhdpi") {
        RuntimeEnvironment.setQualifiers(qualifiers)
        previous=SingletonImageLoader.get(compose.activity)
        loader=ImageLoader.Builder(compose.activity).allowHardware(false).build()
        SingletonImageLoader.setUnsafe(requireNotNull(loader))
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {org.jetbrains.compose.resources.PreviewContextConfigurationEffect()}
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,fontScale)) {
                SriSuTheme(darkTheme=dark) {PublicMomentUiContent(state.value,onState,onAction)}
            }
        }
        compose.waitForIdle()
    }
    private fun readyPhoto() {
        compose.waitUntil(8_000) {
            compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Photo ready"),useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Public Moment photo").assertExists()
    }
    private fun capture(name:String) {
        val file=File("build/reports/public-moments/android-$name.png").apply {parentFile?.mkdirs()}
        compose.runOnIdle {
            val view=org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf {it.isShowing}?.window?.decorView ?: compose.activity.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap));file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        }
    }
    @Test fun photoNavigationAndProfileCallbacks() {
        val state=mutableStateOf(PublicMomentUiFixture.state());val actions=mutableListOf<String>()
        show(state,{state.value=it},{actions+=it});readyPhoto();capture("photo")
        compose.onNodeWithContentDescription("Previous Moment").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Next Moment").performClick()
        compose.onNodeWithText("Their page →").performClick()
        assertTrue(actions.containsAll(listOf("move:-1","move:1","profile:3")))
        compose.runOnIdle {state.value=state.value.copy(hasPrevious=false,hasNext=false)}
        compose.onNodeWithContentDescription("Previous Moment").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Next Moment").assertIsNotEnabled()
    }
    @Test fun textOnlyMomentAndAppreciationSuccess() {
        val state=mutableStateOf(PublicMomentUiFixture.state(textOnly=true));val actions=mutableListOf<String>()
        show(state,{state.value=it},{actions+=it})
        compose.onNodeWithText(PublicMomentUiFixture.caption).assertIsDisplayed()
        compose.onNodeWithContentDescription("Public Moment photo").assertDoesNotExist();capture("text")
        compose.onNodeWithText("Send appreciation").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).assertIsDisplayed().performTextInput("This made us smile.")
        compose.onAllNodes(hasText("Send appreciation") and hasClickAction()).onLast().performScrollTo().performClick()
        assertEquals("This made us smile.",state.value.note);assertEquals(1,actions.count {it=="send"})
        compose.runOnIdle {state.value=state.value.copy(busy=true)}
        compose.onNodeWithText("Sending…").assertIsNotEnabled();capture("appreciation-sending")
        compose.runOnIdle {state.value=state.value.copy(busy=false,note="",noteSent=true)}
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithText("Your appreciation was sent").performScrollTo().assertIsDisplayed()
    }
    @Test fun revocationClosesNoteSheetAndRemovesPrivatePixelsAndActions() {
        val state=mutableStateOf(PublicMomentUiFixture.state());show(state,{state.value=it});readyPhoto()
        compose.onNodeWithText("Send appreciation").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextInput("A draft note")
        compose.runOnIdle {state.value=state.value.copy(fresh=false,unavailable="unavailable",note="",bytes=null)}
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithText(PublicMomentUiFixture.caption).assertDoesNotExist()
        compose.onNodeWithText("Maya").assertDoesNotExist()
        compose.onNodeWithContentDescription("Public Moment photo").assertDoesNotExist()
        compose.onNodeWithText("Their page →").assertDoesNotExist()
        compose.onNodeWithText("This Moment is no longer available").assertIsDisplayed();capture("unavailable")
    }
    @Test fun smallDarkLargeTextKeepsActionsReachable() {
        val state=mutableStateOf(PublicMomentUiFixture.state())
        show(state,{state.value=it},dark=true,fontScale=1.8f,qualifiers="w320dp-h640dp-xhdpi")
        readyPhoto();capture("small-dark-large-text")
        compose.onNodeWithText("Send appreciation").performScrollTo().assertIsDisplayed().performClick()
        compose.onNode(hasSetTextAction()).performScrollTo().assertIsDisplayed();capture("small-dark-note")
    }
    @Test fun landscapeAllowsMediaThenNavigationAndAppreciation() {
        val state=mutableStateOf(PublicMomentUiFixture.state())
        show(state,{state.value=it},qualifiers="w640dp-h320dp-land-xhdpi")
        readyPhoto();capture("landscape")
        compose.onNodeWithContentDescription("Next Moment").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Send appreciation").performScrollTo().assertIsDisplayed()
    }
}
