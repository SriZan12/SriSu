package com.srisu.srisu.features.moments

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsMatcher
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
    private fun render(name:String,dark:Boolean=false,fontScale:Float=1f,small:Boolean=false) {
        previousLoader=SingletonImageLoader.get(compose.activity)
        renderLoader=ImageLoader.Builder(compose.activity).allowHardware(false).build()
        SingletonImageLoader.setUnsafe(renderLoader!!)
        if(small) RuntimeEnvironment.setQualifiers("w320dp-h640dp-xhdpi")
        compose.mainClock.autoAdvance=false
        compose.setContent {
            CompositionLocalProvider(androidx.compose.ui.platform.LocalInspectionMode provides true) {org.jetbrains.compose.resources.PreviewContextConfigurationEffect()}
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,fontScale)) {SriSuTheme(darkTheme=dark) {androidx.compose.material3.Surface {MomentRenderFixture(name)}}}
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        if((name.startsWith("create") && !name.contains("select")) || (name.startsWith("viewer") && !name.contains("expired") && !name.contains("unavailable"))) {
            compose.waitUntil(8_000) {
                compose.mainClock.advanceTimeByFrame()
                compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Photo ready"),useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()
            }
        }
        compose.mainClock.advanceTimeBy(500);compose.waitForIdle()
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
    @Test fun selection()=render("create-select")
    @Test fun preview()=render("create-preview")
    @Test fun previewSmallLargeText()=render("create-preview-small-large-text",fontScale=1.6f,small=true)
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
