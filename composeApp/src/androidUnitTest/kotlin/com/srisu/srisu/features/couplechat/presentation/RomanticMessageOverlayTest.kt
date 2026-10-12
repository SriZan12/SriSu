package com.srisu.srisu.features.couplechat.presentation

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.srisu.srisu.features.couplechat.data.*
import com.srisu.srisu.theme.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RomanticMessageOverlayTest {
    @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private fun effect(intensity:RomanticIntensity, id:String="message")=RomanticEffect(1,"room",id,intensity)
    @OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
    private fun content(dark: () -> Boolean = {false}, block: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {org.jetbrains.compose.resources.PreviewContextConfigurationEffect()}
            SriSuTheme(darkTheme=dark()) {SriSuChatTheme(ChatThemePreset.DEFAULT) {block()} }
        }
    }
    private fun screenshot(name:String) = compose.runOnIdle {
        val view=compose.activity.window.decorView
        val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        val file=File("build/reports/chat-romance/$name.png");file.parentFile?.mkdirs()
        file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
    private fun romanticPixelCount(): Int {
        var count=0
        compose.runOnIdle {
            val view=compose.activity.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            val pixels=IntArray(bitmap.width*bitmap.height)
            bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
            count=pixels.count { pixel ->
                val r=android.graphics.Color.red(pixel);val g=android.graphics.Color.green(pixel);val b=android.graphics.Color.blue(pixel) // theme-audit: image pixels; renderer verification
                r>g+28 && r>b+10 && b>g
            }
            bitmap.recycle()
        }
        return count
    }
    @Test fun renderAllStylesOverTheConversationWithDeterministicMotionFrames() {
        var effect by mutableStateOf(effect(RomanticIntensity.SWEET))
        var progress by mutableStateOf(0f)
        var dark by mutableStateOf(false)
        val messages=listOf(
            LocalChatMessage("one","room",2,"2026-10-12T10:00:00Z",ChatContent.Text("Thinking of you. Hope your day is going well."),ChatTransport.SENT,sequence=1,timelineKey="1"),
            LocalChatMessage("two","room",1,"2026-10-12T10:00:01Z",ChatContent.Text("I love you so much"),ChatTransport.SENT,sequence=2,read=true,timelineKey="2"))
        val state=ConversationState(accountId=1,roomId="room",partnerId=2,partnerName="Alex",security=ConversationSecurity.READY,loading=false,messages=messages)
        content(dark={dark}) {Box(Modifier.fillMaxSize()) {
            ConversationSurface(state,rememberLazyListState(),{},{},{},{},{},{},{},{},
                composer={ConversationComposer(state,false,false,{},{},{},{},voice={})},
                messageContent={_,m -> MessageBubble(m,m.authorId==1L,"Alex",1,false,{},{},{},{},{},null,emptySet())})
            RomanticParticles(effect,{progress},Modifier.matchParentSize())
        }}
        for(intensity in RomanticIntensity.entries) {
            compose.runOnIdle {effect=effect(intensity)}
            for((index,time) in listOf(0f,.15f,.3f,.45f,.6f,.75f,.9f,1f).withIndex()) {
                compose.runOnIdle {progress=time}
                screenshot("${intensity.name.lowercase()}-$index")
                if(time==0f || time==1f) assertEquals(0,romanticPixelCount())
                if(time==.45f) assertTrue("Visible custom artwork for $intensity",romanticPixelCount()>0)
            }
        }
        compose.runOnIdle {dark=true;progress=.45f}
        screenshot("deep-dark")
    }
    @Test fun liveOverlayAllowsTypingAndTapsAndDropsHiddenOrForeignRoomEvents() {
        val events=MutableSharedFlow<RomanticEffect>(extraBufferCapacity=1)
        var enabled by mutableStateOf(true);var text by mutableStateOf("");var taps=0
        compose.mainClock.autoAdvance=false
        content {Box(Modifier.fillMaxSize()) {
            Column {Button(onClick={taps++}) {Text("Underlying action")};TextField(text,{text=it},label={Text("Draft")})}
            RomanticMessageOverlay(events,1,"room",enabled,Modifier.matchParentSize())
        }}
        compose.waitForIdle()
        compose.runOnIdle {events.tryEmit(effect(RomanticIntensity.DEEP))}
        compose.mainClock.advanceTimeBy(1200)
        assertTrue(romanticPixelCount()>0)
        compose.onNodeWithText("Underlying action").performTouchInput {click()}
        compose.onNodeWithText("Draft").performTextInput("Kept while hearts float")
        compose.runOnIdle {assertEquals(1,taps);assertEquals("Kept while hearts float",text);enabled=false}
        compose.mainClock.advanceTimeByFrame()
        assertEquals(0,romanticPixelCount())
        compose.runOnIdle {events.tryEmit(effect(RomanticIntensity.DEEP));enabled=true}
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle {events.tryEmit(effect(RomanticIntensity.DEEP).copy(roomId="another-room"))}
        compose.mainClock.advanceTimeBy(1200)
        assertEquals(0,romanticPixelCount())
        compose.onNodeWithText("Kept while hearts float").assertExists()
    }
    @Test fun animationFinishesCancelsAndRespectsReducedMotion() {
        lateinit var state:RomanticAnimationState
        lateinit var scope:kotlinx.coroutines.CoroutineScope
        compose.mainClock.autoAdvance=false
        content {state=remember {RomanticAnimationState()};scope=rememberCoroutineScope()}
        lateinit var job:kotlinx.coroutines.Job
        compose.runOnIdle {job=scope.launch {state.play(effect(RomanticIntensity.SWEET))}}
        compose.mainClock.advanceTimeBy(1000)
        compose.runOnIdle {assertNotNull(state.effect);assertTrue(state.progress.value>0f);job.cancel()}
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle {assertNull(state.effect);scope.launch {state.play(effect(RomanticIntensity.AFFECTIONATE))}}
        compose.mainClock.advanceTimeBy(4000)
        compose.runOnIdle {assertNull(state.effect)}
        val reduce=object:MotionDurationScale {override val scaleFactor=0f}
        compose.runOnIdle {scope.launch(reduce) {state.play(effect(RomanticIntensity.DEEP))}}
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle {assertNull(state.effect)}
    }
}
