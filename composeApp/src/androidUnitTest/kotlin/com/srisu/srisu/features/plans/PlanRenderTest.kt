package com.srisu.srisu.features.plans

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.*
import com.srisu.srisu.features.plans.presentation.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.srisu.srisu.theme.SriSuTheme
import org.junit.Rule
import org.junit.Test
import org.junit.After
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.allowHardware
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
class PlanRenderTest {
    private var previousLoader: ImageLoader?=null
    private var renderLoader: ImageLoader?=null
    @After fun restoreLoader() {
        previousLoader?.let {SingletonImageLoader.setUnsafe(it)}
        renderLoader?.shutdown()
    }
    @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private fun render(name:String,dark:Boolean=false,fontScale:Float=1f,small:Boolean=false) {
        previousLoader=SingletonImageLoader.get(compose.activity)
        renderLoader=ImageLoader.Builder(compose.activity).allowHardware(false).build()
        SingletonImageLoader.setUnsafe(renderLoader!!)
        if(small) RuntimeEnvironment.setQualifiers("w320dp-h640dp-xhdpi")
        compose.mainClock.autoAdvance=false
        compose.setContent {
            CompositionLocalProvider(androidx.compose.ui.platform.LocalInspectionMode provides true) {org.jetbrains.compose.resources.PreviewContextConfigurationEffect()}
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,fontScale)) {SriSuTheme(darkTheme=dark) {androidx.compose.material3.Surface {PlanRenderFixture(name)}}}
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(500);compose.waitForIdle()
        val file=File("build/reports/plans/android-$name.png").apply {parentFile?.mkdirs()}
        compose.runOnIdle {
            val view=org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf {it.isShowing}?.window?.decorView ?: compose.activity.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap));file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        }
    }
    @Test fun agenda()=render("agenda")
    @Test fun invitation()=render("invited")
    @Test fun agreed()=render("agreed")
    @Test fun change()=render("change")
    @Test fun completed()=render("completed")
    @Test fun failed()=render("failed")
    @Test fun past()=render("past")
    @Test fun empty()=render("empty")
    @Test fun dark()=render("agenda-dark",dark=true)
    @Test fun largeText()=render("invited-small-large-text",fontScale=1.6f,small=true)
    @Test fun acceptanceIsReachableWithLargeText() {
        RuntimeEnvironment.setQualifiers("w320dp-h640dp-xhdpi")
        var chosen:String?=null
        compose.setContent {
            CompositionLocalProvider(androidx.compose.ui.platform.LocalInspectionMode provides true) {org.jetbrains.compose.resources.PreviewContextConfigurationEffect()}
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.6f)) {SriSuTheme {
                PlanContent(PlanFixtures.base.copy(plan=PlanFixtures.plan),com.srisu.srisu.features.plans.data.ReminderState(),{},{},{},{},{},{},{},{},{},{action,_->chosen=action},{},{},{},{})
            }}
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Yes, let’s go"))
        compose.onNodeWithText("Yes, let’s go").assertIsDisplayed().performClick()
        compose.runOnIdle {org.junit.Assert.assertEquals("accept",chosen)}
    }
    @Test fun partnerChecklistUpdateDoesNotClearTheTextBeingTyped() {
        val current=androidx.compose.runtime.mutableStateOf(PlanFixtures.base.copy(plan=PlanFixtures.agreed))
        compose.setContent {
            CompositionLocalProvider(androidx.compose.ui.platform.LocalInspectionMode provides true) {org.jetbrains.compose.resources.PreviewContextConfigurationEffect()}
            SriSuTheme {PlanContent(current.value,com.srisu.srisu.features.plans.data.ReminderState(),{},{},{},{},{},{},{},{},{},{_,_->},{},{},{},{})}
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Add something to bring"))
        compose.onNodeWithText("Add something to bring").performTextInput("Keep my checklist draft")
        compose.runOnIdle {current.value=current.value.copy(plan=PlanFixtures.agreed.copy(checklist=PlanFixtures.agreed.checklist+com.srisu.srisu.features.plans.data.PlanItem("remote","Partner’s addition")))}
        compose.onNodeWithText("Keep my checklist draft").assertExists().performImeAction()
        compose.onNodeWithText("Keep my checklist draft").assertIsNotFocused()
        compose.runOnIdle {current.value=current.value.copy(addedItemRequest="own-confirmed-request")}
        compose.onNodeWithText("Keep my checklist draft").assertDoesNotExist()
    }

    @Test fun editorDateAndTimeControlsOpen() {
        compose.setContent {
            CompositionLocalProvider(androidx.compose.ui.platform.LocalInspectionMode provides true) {org.jetbrains.compose.resources.PreviewContextConfigurationEffect()}
            SriSuTheme {PlanEditor(PlanFixtures.base.copy(editing=true,draft=com.srisu.srisu.features.plans.data.PlanDraft(3,title="A walk",timeZone="Asia/Kathmandu")),{},{},{},{},{})}
        }
        compose.onNodeWithText("Choose a date").performClick();compose.waitForIdle()
        compose.onNodeWithText("Choose date").assertExists().performClick();compose.waitForIdle()
        compose.onNodeWithText("Choose a time").performClick();compose.waitForIdle()
        compose.onNodeWithText("Choose time").assertExists()
    }

    @Test fun editorKeepsCloseReachableWithLargeTextAndSupportsNextAndDone() {
        RuntimeEnvironment.setQualifiers("w320dp-h480dp-xhdpi")
        val draft=androidx.compose.runtime.mutableStateOf(com.srisu.srisu.features.plans.data.PlanDraft(3,title="A walk",timeZone="Asia/Kathmandu"))
        var closed=false
        compose.setContent {
            CompositionLocalProvider(androidx.compose.ui.platform.LocalInspectionMode provides true) {org.jetbrains.compose.resources.PreviewContextConfigurationEffect()}
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.6f)) {
                SriSuTheme {PlanEditor(PlanFixtures.base.copy(editing=true,draft=draft.value),{draft.value=it},{},{closed=true},{},{})}
            }
        }
        val title=compose.onNodeWithText("What would you like to do?")
        val place=compose.onNodeWithText("Place or https link (optional)")
        title.performScrollTo().performClick().performImeAction()
        place.assertIsFocused().performTextInput("Keep this place")
        place.performImeAction();place.assertIsNotFocused()
        compose.onNodeWithText("Discard draft").performScrollTo()
        compose.onNodeWithContentDescription("Save draft and close").assertIsDisplayed().performClick()
        compose.runOnIdle {org.junit.Assert.assertTrue(closed);org.junit.Assert.assertEquals("Keep this place",draft.value.place)}
    }

}
