package com.srisu.srisu.features.sparks

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
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
class SparkRenderTest {
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
            CompositionLocalProvider(LocalDensity provides Density(density.density,fontScale)) {SriSuTheme(darkTheme=dark) {androidx.compose.material3.Surface {SparkRenderFixture(name)}}}
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(500);compose.waitForIdle()
        val file=File("build/reports/sparks/android-$name.png").apply {parentFile?.mkdirs()}
        compose.runOnIdle {
            val view=org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf {it.isShowing}?.window?.decorView ?: compose.activity.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap));file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        }
    }
    @Test fun recoveryUnlock()=render("recovery-unlock")
    @Test fun recoveryReady()=render("recovery-ready")
    @Test fun recoveryErrorLargeText()=render("recovery-error-small",fontScale=1.6f,small=true)
    @Test fun decks()=render("decks")
    @Test fun answer()=render("answer")
    @Test fun waiting()=render("waiting")
    @Test fun reveal()=render("reveal")
    @Test fun failed()=render("failed")
    @Test fun keys() {
        render("keys")
        compose.onNodeWithText("Both answers are submitted").assertIsDisplayed()
        compose.onNodeWithText("Answers unavailable on this device").assertIsDisplayed()
        compose.onNodeWithText("Both answers are open").assertDoesNotExist()
    }
    @Test fun skipped()=render("skipped")
    @Test fun dark()=render("decks-dark",dark=true)
    @Test fun smallLargeText()=render("answer-small-large-text",fontScale=1.6f,small=true)
}
