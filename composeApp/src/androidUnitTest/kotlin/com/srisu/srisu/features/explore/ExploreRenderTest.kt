package com.srisu.srisu.features.explore

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.*
import com.srisu.srisu.features.explore.presentation.*
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
class ExploreRenderTest {
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
            CompositionLocalProvider(LocalDensity provides Density(density.density,fontScale)) {SriSuTheme(darkTheme=dark) {androidx.compose.material3.Surface {ExploreRenderFixture(name)}}}
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(500);compose.waitForIdle()
        val file=File("build/reports/explore/android-$name.png").apply {parentFile?.mkdirs()}
        compose.runOnIdle {
            val view=org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf {it.isShowing}?.window?.decorView ?: compose.activity.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap));file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        }
    }
    @Test fun global()=render("global")
    @Test fun faves()=render("faves")
    @Test fun empty()=render("faves-empty")
    @Test fun noActive()=render("no-active")
    @Test fun manage()=render("manage")
    @Test fun loading()=render("loading")
    @Test fun error()=render("error")
    @Test fun saving()=render("saving")
    @Test fun retry()=render("retry")
    @Test fun caughtUp()=render("caught-up")
    @Test fun dark()=render("faves-dark",dark=true)
    @Test fun largeText()=render("global-small-large-text",fontScale=1.6f,small=true)

    @Test fun returningFromViewerRestartsTheGuardedImageLoad() {
        val calls=java.util.concurrent.atomic.AtomicInteger()
        val card=FakeExplore.card(10).copy(preview=FakeExplore.card(10).preview.copy(
            photos=listOf(com.srisu.srisu.features.moments.MomentFixtures.photo)))
        var state by androidx.compose.runtime.mutableStateOf(ExploreState(active=false,
            global=ExploreFeed(listOf(card),loaded=true,fresh=false)))
        compose.setContent {SriSuTheme {ExploreContent(state=state,onSelect={},onRefresh={},
            onMore={},onBroader={},onManage={},onLibrary={},onFave={_,_->},onRetryFave={},
            onMoment={},onProfile={},onPreferences={},loadImage={calls.incrementAndGet();null})}}
        compose.waitForIdle();org.junit.Assert.assertEquals(0,calls.get())
        compose.runOnIdle {state=state.copy(active=true,global=state.global.copy(fresh=true))}
        compose.waitForIdle()
        org.junit.Assert.assertTrue("Image requests after authorization: ${calls.get()}",calls.get()>0)
        val first=calls.get()
        compose.runOnIdle {state=state.copy(active=false,global=state.global.copy(fresh=false))}
        compose.waitForIdle()
        compose.runOnIdle {state=state.copy(active=true,global=state.global.copy(fresh=true))}
        compose.waitForIdle()
        org.junit.Assert.assertTrue("Image requests after returning: ${calls.get()} (before $first)",calls.get()>first)
    }
    @Test fun reauthorizationKeepsTheReadingPositionAndTabsKeepSeparateScroll() {
        var state by androidx.compose.runtime.mutableStateOf(ExploreState(active=true,
            global=ExploreFeed((1L..20).map {FakeExplore.card(it)},loaded=true,fresh=true),
            faves=ExploreFeed((30L..40).map {FakeExplore.card(it).copy(isFaved=true)},loaded=true,fresh=true)))
        lateinit var global: androidx.compose.foundation.lazy.LazyListState
        lateinit var saved: androidx.compose.foundation.lazy.LazyListState
        lateinit var scope: kotlinx.coroutines.CoroutineScope
        compose.setContent {
            global=androidx.compose.foundation.lazy.rememberLazyListState()
            saved=androidx.compose.foundation.lazy.rememberLazyListState()
            scope=androidx.compose.runtime.rememberCoroutineScope()
            SriSuTheme {ExploreContent(state,{state=state.copy(section=it)},{},{},{},{},{},{_,_->},{},{},{},{},{null},global,saved)}
        }
        compose.runOnIdle {scope.launch {global.scrollToItem(6,20)}}
        compose.waitForIdle()
        val index=global.firstVisibleItemIndex;val offset=global.firstVisibleItemScrollOffset
        compose.runOnIdle {state=state.copy(active=false,global=state.global.copy(fresh=false))}
        compose.waitForIdle()
        compose.runOnIdle {state=state.copy(active=true,global=state.global.copy(fresh=true),section="faves")}
        compose.waitForIdle()
        compose.runOnIdle {scope.launch {saved.scrollToItem(3)}}
        compose.waitForIdle()
        compose.runOnIdle {state=state.copy(section="global")}
        compose.waitForIdle()
        org.junit.Assert.assertEquals(index,global.firstVisibleItemIndex)
        org.junit.Assert.assertEquals(offset,global.firstVisibleItemScrollOffset)
        org.junit.Assert.assertEquals(3,saved.firstVisibleItemIndex)
    }
}
