package com.srisu.srisu.features.challenges

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.*
import com.srisu.srisu.features.challenges.presentation.*
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
class ChallengeRenderTest {
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
            CompositionLocalProvider(LocalDensity provides Density(density.density,fontScale)) {SriSuTheme(darkTheme=dark) {androidx.compose.material3.Surface {ChallengeRenderFixture(name)}}}
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(500);compose.waitForIdle()
        val file=File("build/reports/challenges/android-$name.png").apply {parentFile?.mkdirs()}
        compose.runOnIdle {
            val view=org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf {it.isShowing}?.window?.decorView ?: compose.activity.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap));file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        }
    }
    @Test fun catalogue()=render("catalogue")
    @Test fun invitation()=render("invited")
    @Test fun detail()=render("detail")
    @Test fun active()=render("active")
    @Test fun paused()=render("paused")
    @Test fun completed()=render("completed")
    @Test fun failed()=render("failed")
    @Test fun history()=render("history")
    @Test fun dark()=render("catalogue-dark",dark=true)
    @Test fun smallLargeText()=render("invited-small-large-text",fontScale=1.6f,small=true)

    @Test fun invitationActionRemainsReachableAtLargeTextAndDispatchesAcceptance() {
        RuntimeEnvironment.setQualifiers("w320dp-h640dp-xhdpi")
        var action: String? = null
        compose.setContent {
            CompositionLocalProvider(androidx.compose.ui.platform.LocalInspectionMode provides true) { org.jetbrains.compose.resources.PreviewContextConfigurationEffect() }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                SriSuTheme {
                    ChallengeContent(ChallengeState(home=ChallengeFixtures.home,session=ChallengeFixtures.session,
                        account=1,loading=false,fresh=true), {}, {}, {}, {}, {}, {}, {_,_->}, {}, {}, {}, { action=it }, {}, {})
                }
            }
        }
        compose.onNodeWithText("Challenges").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Let’s do it"))
        compose.onNodeWithText("Let’s do it").assertIsDisplayed().performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals("accept", action) }
        compose.onNodeWithText("I’ve done my part").assertDoesNotExist()
    }

    @Test fun openingAnActivityFromLowerInTheListStartsAtItsHeading() {
        val state = androidx.compose.runtime.mutableStateOf(ChallengeState(home=ChallengeFixtures.home,
            account=1,loading=false,fresh=true))
        compose.setContent {
            CompositionLocalProvider(androidx.compose.ui.platform.LocalInspectionMode provides true) { org.jetbrains.compose.resources.PreviewContextConfigurationEffect() }
            SriSuTheme {
                ChallengeContent(state.value, {}, {}, {}, {}, { state.value=state.value.copy(selected=it) },
                    {}, {_,_->}, {}, {}, {}, {}, {}, {})
            }
        }
        val title = ChallengeFixtures.collections.first().activities.last().title
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(title,substring=true))
        compose.onNodeWithText(title).performClick()
        compose.onNodeWithText("All activities").assertIsDisplayed()
        compose.onNodeWithText(title).assertIsDisplayed()
    }
    @Test fun completedChallengeOpensExistingMomentAndChatActionsWithoutSendingAnInvitation() {
        var moment: Long? = null
        var chat = false
        compose.setContent {
            CompositionLocalProvider(androidx.compose.ui.platform.LocalInspectionMode provides true) { org.jetbrains.compose.resources.PreviewContextConfigurationEffect() }
            SriSuTheme {
                ChallengeContent(ChallengeState(home=ChallengeFixtures.home,
                    session=ChallengeFixtures.session.copy(creator=1,state="completed",confirmedBy=listOf(1,2)),
                    account=1,loading=false,fresh=true), {}, {chat=true}, {moment=it}, {}, {}, {}, {_,_->}, {}, {}, {}, {}, {}, {})
            }
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Share a Moment"))
        compose.onNodeWithText("Share a Moment").performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals(3L, moment) }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Continue in chat"))
        compose.onNodeWithText("Continue in chat").performClick()
        compose.runOnIdle { org.junit.Assert.assertTrue(chat) }
        compose.onNodeWithText("Send invitation to chat").assertDoesNotExist()
    }
}
