package com.srisu.srisu.features.couplechat.presentation

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.srisu.srisu.features.couplechat.data.*
import com.srisu.srisu.theme.SriSuTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], qualifiers="w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConversationRedesignTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private fun message(id: String, own: Boolean, text: String, transport: ChatTransport = ChatTransport.SENT) =
        LocalChatMessage(id,"room",if(own)1 else 2,"2026-10-10T10:00:00Z",ChatContent.Text(text),
            transport=transport,sequence=if(transport==ChatTransport.SENT)1 else null,timelineKey=id)
    private fun attachment(kind: String, waveform: List<Int> = emptyList()) = ChatMedia(
        AttachmentDescriptor("attachment","room","voice","synthetic","synthetic",16,"synthetic"),kind,
        if(kind=="voice")"audio/wav" else "video/mp4",durationMs=18000,waveform=waveform)
    private fun initial() = ConversationState(accountId=1,roomId="room",partnerId=2,partnerName="Alex",
        security=ConversationSecurity.READY,loading=false,messages=listOf(
            message("one",false,"Made it home. How was your afternoon?"),
            message("two",true,"A little busy, but this made me smile." ).copy(delivered=true),
            message("three",false,"Tell me about it when you have a moment."),
            message("voice",true,"").copy(content=ChatContent.Media(listOf(attachment("voice", listOf(12,35,63,20,90,42,60,25,35,10,55,85,30,42,15))))),
            message("five",true,"Tea and a walk later?").copy(read=true,reactions=mapOf(2L to "❤️"))
        ))
    private var state by mutableStateOf(initial())
    private var retries = 0
    private var menus = 0
    private var replies = 0
    private var sends = 0
    @OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
    private fun content(dark: Boolean=false, large: Boolean=false, block: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(androidx.compose.ui.platform.LocalInspectionMode provides true) { org.jetbrains.compose.resources.PreviewContextConfigurationEffect() }
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,if(large)1.8f else 1f)) {
                SriSuTheme(darkTheme=dark) {Surface(Modifier.fillMaxSize()) {block()} }
            }
        }
    }
    private fun screen(dark: Boolean=false, large: Boolean=false) = content(dark,large) {
        ConversationSurface(state,rememberLazyListState(),{}, {}, {},{state=state.copy(draft=it)}, {}, {}, {retries++}, {},
            composer={ConversationComposer(state,false,false,{state=state.copy(draft=it)},{sends++},{},{state=state.copy(reply=null)},
                voice={FilledIconButton(onClick={}) {Icon(Icons.Default.Mic,"Record voice message")}})},
            messageContent={index,m -> MessageBubble(m,m.authorId==1L,state.partnerName,1L,messagesGroup(state.messages.getOrNull(index-1),m),
                {menus++},{replies++},{},{retries++},{},null,emptySet())})
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val target=File("build/reports/chat-ui/$name.png");target.parentFile?.mkdirs()
        compose.runOnIdle {
            val view=compose.activity.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            try {view.draw(android.graphics.Canvas(bitmap));target.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}
            finally {bitmap.recycle()}
        }
    }
    @Test fun conversationHasRealReceiptsAndComposerSendsMultilineDraft() {
        screen()
        compose.onNodeWithText("Delivered").assertExists()
        compose.onNodeWithText("Read").assertExists()
        screenshot("conversation-light")
        compose.onNodeWithContentDescription("Message").performTextInput("A thought\nand another line")
        compose.onNodeWithContentDescription("Send message").performClick()
        assertEquals("A thought\nand another line",state.draft);assertEquals(1,sends)
    }
    @Test @Config(qualifiers="w320dp-h640dp-xhdpi") fun smallLargeTextComposerAndReplyStayInsideScreen() {
        state=initial().copy(partnerName="Alexandria with a very long partner name",draft="A longer thought that wraps onto more than one line",
            reply=ChatQuote("one",2,"A quoted message with enough words to wrap at a larger text size."),messages=emptyList())
        screen(large=true)
        val root=compose.onRoot().getUnclippedBoundsInRoot()
        for(label in listOf("Send message","Cancel reply","Attach or share","Message")) {
            val node=compose.onNodeWithContentDescription(label);node.assertIsDisplayed()
            val bounds=node.getUnclippedBoundsInRoot()
            assertTrue("$label left",bounds.left>=root.left);assertTrue("$label right",bounds.right<=root.right)
            assertTrue("$label bottom",bounds.bottom<=root.bottom)
        }
        screenshot("small-large-text-reply")
        compose.onNodeWithContentDescription("Cancel reply").performClick();assertNull(state.reply)
    }
    @Test @Config(qualifiers="w600dp-h960dp-xhdpi") fun wideDarkConversation() {
        screen(dark=true);compose.onNodeWithText("Alex").assertIsDisplayed();screenshot("conversation-wide-dark")
    }
    @Test fun emptySuggestionCreatesAnEditableDraft() {
        state=initial().copy(messages=emptyList());screen()
        compose.onNodeWithText("Just the two of you").assertIsDisplayed()
        screenshot("conversation-empty")
        compose.onNodeWithText("What made you smile today?").performClick()
        assertEquals("What made you smile today?",state.draft)
        compose.onNodeWithContentDescription("Send message").assertIsEnabled()
    }
    @Test fun failedSendAndDisconnectedNoticePreserveDraftAndExposeRetry() {
        state=initial().copy(error="Connection interrupted. Retrying…",draft="Keep this draft",
            messages=listOf(message("one",true,"Try this again",ChatTransport.FAILED).copy(read=true,delivered=true)))
        screen();compose.onNodeWithText("Read").assertDoesNotExist();compose.onNodeWithText("Delivered").assertDoesNotExist()
        compose.onNodeWithText("Retry send").performClick();assertEquals(1,retries)
        assertEquals("Keep this draft",state.draft);screenshot("conversation-offline-retry")
    }
    @Test fun longPressAndSwipeStillExposeMessageActions() {
        state=initial().copy(messages=listOf(message("one",false,"An incoming message")));screen()
        compose.onNodeWithText("An incoming message").performTouchInput {longClick()};assertEquals(1,menus)
        compose.onNodeWithText("An incoming message").performTouchInput {swipeRight()};assertEquals(1,replies)
    }
    @Test fun deletingMessageHidesItsReactionsAndPrivateQuote() {
        state=initial().copy(messages=listOf(message("one",true,"Do not display this").copy(deleted=true,reactions=mapOf(2L to "❤️"))))
        screen();compose.onNodeWithText("This message was deleted").assertExists()
        compose.onNodeWithText("Do not display this").assertDoesNotExist();compose.onNodeWithText("❤️").assertDoesNotExist()
    }
    @Test @Config(qualifiers="w320dp-h640dp-xhdpi") fun playbackUsesNativePositionAndErrorOffersRetry() {
        var retried=false
        content(large=true) {Column(Modifier.padding(20.dp)) {VoicePlaybackPanel(attachment("voice"),false,0,0,1f,false,"Playback failed",
            {retried=true},{},{})}}
        compose.onNodeWithContentDescription("Audio waveform unavailable").assertExists()
        compose.onNodeWithContentDescription("Seek voice message").assertIsNotEnabled()
        val root=compose.onRoot().getUnclippedBoundsInRoot()
        for(label in listOf("Retry playback","Playback speed, 1.0 times")) {
            val control=compose.onNodeWithContentDescription(label)
            control.assertIsDisplayed()
            val bounds=control.getUnclippedBoundsInRoot()
            assertTrue(bounds.left>=root.left);assertTrue(bounds.right<=root.right)
        }
        compose.onNodeWithContentDescription("Retry playback").performClick();assertTrue(retried)
        screenshot("voice-playback-unavailable")
    }
    @Test @Config(qualifiers="w320dp-h640dp-xhdpi") fun videoReplyKeepsCaptionAndConfirmedUploadProgress() {
        var opened=false
        content {Column(Modifier.padding(16.dp)) {
            val video=attachment("video").copy(width=640,height=360)
            val message=message("video",true,"",ChatTransport.SENDING).copy(content=ChatContent.Media(listOf(video),
                "A little part of today",ChatQuote("one",2,"Show me how your day went")))
            MessageBubble(message,true,"Alex",1,false,{}, {}, {}, {}, {opened=true},.3f,emptySet())
        }}
        compose.onNodeWithText("Preview unavailable").assertIsDisplayed()
        compose.onNodeWithText("Show me how your day went").assertIsDisplayed()
        compose.onNodeWithText("Uploading 30%").assertIsDisplayed()
        compose.onNodeWithText("Sending").assertIsDisplayed()
        compose.onNodeWithText("Delivered").assertDoesNotExist()
        compose.onNodeWithContentDescription("Play video").performClick();assertTrue(opened)
        screenshot("mixed-media-compact")
    }
    @Test fun reactionUpdateDoesNotChangeReadingPosition() {
        state=initial().copy(messages=(1..70).map {message("message-$it",it%2==0,"A synthetic message number $it")},atLatest=false)
        screen();compose.onNode(hasScrollToIndexAction()).performScrollToIndex(11)
        val before=compose.onNodeWithText("A synthetic message number 12").getUnclippedBoundsInRoot()
        compose.runOnIdle {state=state.copy(messages=state.messages.map {if(it.id=="message-60")it.copy(reactions=mapOf(2L to "❤️")) else it})}
        assertEquals(before,compose.onNodeWithText("A synthetic message number 12").getUnclippedBoundsInRoot())
        compose.runOnIdle {state=state.copy(messages=(1..10).map {message("older-$it",false,"An earlier message $it")}+state.messages)}
        assertEquals(before,compose.onNodeWithText("A synthetic message number 12").getUnclippedBoundsInRoot())
    }
}
