package com.srisu.srisu.features.moments

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
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
class MomentRenderTest {
    private var previousLoader: ImageLoader?=null
    private var renderLoader: ImageLoader?=null
    @After fun restoreLoader() {
        previousLoader?.let {SingletonImageLoader.setUnsafe(it)}
        renderLoader?.shutdown()
    }
    @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private fun render(name:String,dark:Boolean=false,fontScale:Float=1f,small:Boolean=false,landscape:Boolean=false,short:Boolean=false,keyboardViewport:Boolean=false,check:()->Unit={}) {
        previousLoader=SingletonImageLoader.get(compose.activity)
        renderLoader=ImageLoader.Builder(compose.activity).allowHardware(false).build()
        SingletonImageLoader.setUnsafe(renderLoader!!)
        if(keyboardViewport) RuntimeEnvironment.setQualifiers("w640dp-h240dp-land-xhdpi")
        else if(landscape) RuntimeEnvironment.setQualifiers("w640dp-h320dp-land-xhdpi")
        else if(short) RuntimeEnvironment.setQualifiers("w320dp-h480dp-xhdpi")
        else if(small) RuntimeEnvironment.setQualifiers("w320dp-h640dp-xhdpi")
        compose.mainClock.autoAdvance=false
        compose.setContent {
            CompositionLocalProvider(androidx.compose.ui.platform.LocalInspectionMode provides true) {org.jetbrains.compose.resources.PreviewContextConfigurationEffect()}
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,fontScale)) {SriSuTheme(darkTheme=dark) {androidx.compose.material3.Surface {MomentRenderFixture(name)}}}
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        if((name.startsWith("create") && !name.contains("select") && !keyboardViewport) || (name.startsWith("viewer") && !name.contains("expired") && !name.contains("unavailable"))) {
            compose.waitUntil(8_000) {
                compose.mainClock.advanceTimeByFrame()
                compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Photo ready"),useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()
            }
        }
        compose.mainClock.advanceTimeBy(500);compose.waitForIdle()
        if(name.startsWith("create")) compose.onNodeWithTag(if(keyboardViewport) "moment-caption-done" else "moment-share").assertIsDisplayed()
        compose.mainClock.autoAdvance=true
        check()
        val file=File("build/reports/moments/android-$name.png").apply {parentFile?.mkdirs()}
        compose.runOnIdle {
            val view=org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf {it.isShowing}?.window?.decorView ?: compose.activity.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap));file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        }
    }
    @Test fun baseline()=render("00-baseline-home")
    @Test fun homeEmpty()=render("home-empty")
    @Test fun homeActive()=render("home-active")
    @Test fun homeOffline()=render("home-offline")
    @Test fun homeSmallLargeText()=render("home-long-small-large-text",fontScale=1.6f,small=true)
    @Test fun selection()=render("create-select") {
        compose.onNodeWithTag("moment-add-first-photo").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithContentDescription("Add or edit caption").assertIsDisplayed()
        compose.onNode(hasText("Just you two") and isSelectable()).assertIsSelected()
        compose.onNodeWithTag("moment-share").assertIsNotEnabled()
    }
    @Test fun emptySmallLargeText()=render("create-select-small",small=true,fontScale=2f)
    @Test fun emptyLandscape()=render("create-select-landscape",landscape=true)
    @Test fun addThenRemoveLastPhotoKeepsCaptionAndAudience()=render("create-select-flow") {
        compose.onNodeWithTag("moment-share").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Add or edit caption").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextInput("A little thought before choosing a photo.")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.onNodeWithText("Public").performScrollTo().performClick()
        compose.onNodeWithTag("moment-add-first-photo").performScrollTo().performClick()
        compose.onNodeWithTag("moment-share").assertIsEnabled()
        compose.onNodeWithContentDescription("Remove selected photo").performScrollTo().performClick()
        compose.onNodeWithTag("moment-add-first-photo").assertIsDisplayed()
        compose.onNodeWithTag("moment-share").assertIsNotEnabled()
        compose.onNodeWithText("A little thought before choosing a photo.").assertExists()
        compose.onNode(hasText("Public") and isSelectable()).assertIsSelected()
    }
    @Test fun cancelledSelectionStaysInComposer()=render("create-select-cancel") {
        compose.onNodeWithTag("moment-add-first-photo").performClick()
        compose.onNodeWithTag("moment-add-first-photo").assertIsDisplayed()
        compose.onNodeWithContentDescription("Take a photo").assertIsEnabled()
        compose.onNodeWithTag("moment-share").assertIsNotEnabled()
    }
    @Test fun cropApplyCancelAndFullRestore()=render("create-crop-applied") {
        compose.onNodeWithTag("moment-photo-full").assertIsSelected()
        compose.onNodeWithTag("moment-photo-fill").performScrollTo().performClick()
        compose.onNodeWithTag("moment-photo-full").assertIsNotSelected()
        compose.onNodeWithTag("moment-photo-crop").performClick()
        compose.waitUntil(8_000) { compose.onAllNodes(hasTestTag("moment-crop-apply") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("moment-crop-ratio-1:1").performClick()
        compose.onNodeWithTag("moment-crop-zoom").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(2f) }
        compose.onNodeWithTag("moment-crop-canvas").performTouchInput { swipe(center, center + androidx.compose.ui.geometry.Offset(50f, 80f)) }
        compose.onNodeWithTag("moment-crop-apply").performClick()
        compose.onNodeWithTag("moment-photo-full").assertIsNotSelected()
        compose.onNodeWithTag("moment-photo-crop").performClick()
        compose.onNodeWithTag("moment-crop-reset").performClick()
        compose.onNodeWithTag("moment-crop-cancel").performClick()
        compose.onNodeWithTag("moment-photo-full").assertIsNotSelected()
        compose.onNodeWithTag("moment-photo-full").performClick().assertIsSelected()
        compose.onNodeWithTag("moment-share").assertIsDisplayed().assertIsEnabled()
    }
    @Test fun cropDark()=render("create-crop-dark",dark=true) {
        compose.onNodeWithTag("moment-photo-crop").performScrollTo().performClick()
        compose.waitUntil(8_000) { compose.onAllNodes(hasTestTag("moment-crop-apply") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("moment-crop-ratio-4:5").performClick()
        compose.onNodeWithTag("moment-crop-apply").assertIsDisplayed()
    }
    @Test fun cropSmallLargeText()=render("create-crop-small",small=true,fontScale=2f) {
        compose.onNodeWithTag("moment-photo-crop").performScrollTo().performClick()
        compose.waitUntil(8_000) { compose.onAllNodes(hasTestTag("moment-crop-apply") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("moment-crop-apply").assertIsDisplayed()
        compose.onNodeWithTag("moment-crop-canvas").assertIsDisplayed()
        compose.onNodeWithTag("moment-crop-reset").performScrollTo().assertIsDisplayed()
    }
    @Test fun cropLandscape()=render("create-crop-landscape",landscape=true) {
        compose.onNodeWithTag("moment-photo-crop").performClick()
        compose.waitUntil(8_000) { compose.onAllNodes(hasTestTag("moment-crop-apply") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("moment-crop-ratio-4:5").performClick()
        compose.onNodeWithTag("moment-crop-apply").assertIsDisplayed()
        compose.onNodeWithTag("moment-crop-canvas").assertIsDisplayed()
    }
    @Test fun preview()=render("create-preview")
    @Test fun previewSmallLargeText()=render("create-preview-small-large-text",fontScale=1.6f,small=true)
    @Test fun shortLandscapeCaption()=render("create-caption-compact",fontScale=2f,keyboardViewport=true) {
        compose.onNode(hasSetTextAction()).assertIsDisplayed()
        compose.onNodeWithTag("moment-caption-done").assertIsDisplayed()
    }
    @Test fun previewLandscape()=render("create-landscape",landscape=true)
    @Test fun previewShort()=render("create-short",short=true)
    @Test fun previewDark()=render("create-dark",dark=true)
    @Test fun largeTextCanChoosePublicAndReturnToPrivate()=render("create-large-interaction",small=true,fontScale=2f) {
        compose.onNodeWithText("Public").performScrollTo().performClick()
        compose.onNodeWithText("This Moment and its caption will appear in discovery. Replies from other people stay private appreciation notes.").assertExists()
        compose.onNodeWithText("Just you two").performScrollTo().performClick()
        compose.onNode(hasText("Just you two") and isSelectable()).assertIsSelected()
        compose.onNodeWithTag("moment-share").assertIsDisplayed().assertIsEnabled()
    }
    @Test fun captionDoneKeepsDraftAndReturnsToAudience()=render("create-edit-interaction",small=true) {
        compose.onNodeWithContentDescription("Add or edit caption").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("A little thought for you.\nSee you soon.")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithText("A little thought for you.\nSee you soon.").assertExists()
        compose.onNodeWithText("Public").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("moment-share").assertIsDisplayed()
    }
    @Test fun progressPreventsChangesAndDuplicateShare()=render("create-progress-interaction") {
        compose.onNodeWithTag("moment-share").assertIsNotEnabled()
        compose.onNode(hasText("Public") and isSelectable()).assertIsNotEnabled()
        compose.onNodeWithContentDescription("Remove selected photo").assertIsNotEnabled()
    }
    @Test fun navigation()=render("navigation-home")
    @Test fun navigationDark()=render("navigation-dark",dark=true)
    @Test fun navigationLargeText()=render("navigation-small-large-text",fontScale=1.6f,small=true)
    @Test fun audience()=render("create-audience")
    @Test fun upload()=render("create-progress")
    @Test fun failedUpload()=render("create-failure")
    @Test fun viewer()=render("viewer-partner")
    @Test fun creator()=render("viewer-creator")
    @Test fun edit()=render("viewer-creator-edit")
    @Test fun visitor()=render("viewer-public")
    @Test fun expired()=render("viewer-expired")
    @Test fun unavailable()=render("viewer-unavailable")
    @Test fun dark()=render("home-active-dark",dark=true)
    @Test fun viewerSmallLargeText()=render("viewer-long-small-large-text",fontScale=1.6f,small=true)
}
