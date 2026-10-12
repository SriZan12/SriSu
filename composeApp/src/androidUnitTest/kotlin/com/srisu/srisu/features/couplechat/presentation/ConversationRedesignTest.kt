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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.srisu.srisu.features.couplechat.data.*
import com.srisu.srisu.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.ByteArrayOutputStream
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Base64

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
    private var deviceConfirmations = 0
    private var replacingDevice by mutableStateOf(false)
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
        SriSuChatTheme(ChatThemePreset.fromId(state.preferences.chatTheme)) {
        ConversationSurface(state,rememberLazyListState(),{}, {}, {},{state=state.copy(draft=it)}, {}, {}, {retries++}, {replacingDevice=true},
            composer={ConversationComposer(state,false,false,{state=state.copy(draft=it)},{sends++},{},{state=state.copy(reply=null)},
                voice={FilledIconButton(onClick={}) {Icon(Icons.Default.Mic,"Record voice message")}})},
            messageContent={index,m -> MessageBubble(m,m.authorId==1L,state.partnerName,1L,messagesGroup(state.messages.getOrNull(index-1),m),
                {menus++},{replies++},{},{retries++},{},null,emptySet(),groupContinues=messagesGroup(m,state.messages.getOrNull(index+1)),
                afterReactions=state.messages.getOrNull(index-1)?.hasVisibleReactions == true)})
        }
        if (replacingDevice) DeviceReplacementConfirmation(onConfirm = {deviceConfirmations++;replacingDevice=false},
            onDismiss = {replacingDevice=false})
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val target=File("build/reports/chat-ui/$name.png");target.parentFile?.mkdirs()
        compose.runOnIdle {
            val view=if(name=="theme-selector") org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView else compose.activity.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            try {view.draw(android.graphics.Canvas(bitmap));target.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}
            finally {bitmap.recycle()}
        }
    }
    private fun renderThemes(dark: Boolean) {
        state=initial().copy(messages=initial().messages.map {if(it.id=="two")it.copy(content=ChatContent.Text("A little busy, but this made me smile.",ChatQuote("one",2,"Made it home."))) else it})
        screen(dark=dark)
        for(preset in ChatThemePreset.entries) {
            compose.runOnIdle {state=state.copy(preferences=state.preferences.copy(chatTheme=preset.id))}
            screenshot("theme-${preset.id}-${if(dark)"dark" else "light"}")
            compose.onNodeWithContentDescription("Message").assertIsDisplayed()
        }
    }
    @Test fun themePresetsUseTheSameConversationInLight() = renderThemes(false)
    @Test fun themePresetsUseTheSameConversationInDark() = renderThemes(true)
    @Test @Config(qualifiers="w320dp-h420dp-xhdpi") fun themeSwitchKeepsLargeTextDraftReplyAndFocus() {
        state=initial().copy(messages=emptyList(),draft="Keep this thought")
        screen(large=true)
        compose.runOnIdle {state=state.copy(reply=ChatQuote("one",2,"Original thought"))}
        compose.onNodeWithContentDescription("Message").assertIsFocused()
        for(preset in ChatThemePreset.entries) {
            compose.runOnIdle {state=state.copy(preferences=state.preferences.copy(chatTheme=preset.id))}
            compose.onNodeWithContentDescription("Message").assertIsFocused()
            compose.onNodeWithContentDescription("Send message").assertIsDisplayed()
            assertEquals("Keep this thought",state.draft);assertEquals("one",state.reply?.messageId)
            screenshot("theme-${preset.id}-large-composer")
        }
    }
    @Test fun appearancePreviewCanCancelAndOnlyApplyChangesPreference() {
        var saved by mutableStateOf("original");var visible by mutableStateOf(true)
        content {if(visible) ChatAppearanceSheet(saved,{saved=it;visible=false},{visible=false})}
        compose.onNodeWithContentDescription("SriSu Blend").performScrollTo().performClick()
        assertEquals("original",saved)
        screenshot("theme-selector")
        compose.onNodeWithText("Cancel").performClick();assertEquals("original",saved)
        compose.runOnIdle {visible=true}
        compose.onNodeWithContentDescription("Original SriSu").assertIsSelected()
        compose.onNodeWithContentDescription("SriSu Calm").performScrollTo().performClick()
        compose.onNodeWithText("Apply theme").performClick();assertEquals("calm",saved)
    }
    @Test fun fullScreenPhotoAndUnavailableViewerHaveBackAndRetryWithoutDialog() {
        val photo=attachment("photo").copy(mime="image/jpeg",width=800,height=1200)
        var opened by mutableStateOf<OpenedChatMedia?>(OpenedChatMedia("photo",photo,Base64.decode(syntheticThumbnail(true),Base64.NO_WRAP)))
        var back=0;var retry=0
        content {ChatMediaViewerSurface(opened,false,null,"A moment from today",{back++},{retry++})}
        compose.waitUntil(5000) {compose.onAllNodesWithContentDescription("Shared photo").fetchSemanticsNodes().isNotEmpty()}
        compose.onAllNodes(isDialog()).assertCountEquals(0)
        screenshot("viewer-photo")
        compose.onNodeWithContentDescription("Back to conversation").performClick();assertEquals(1,back)
        compose.runOnIdle {opened=null}
        compose.onNodeWithText("This attachment is no longer available.").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick();assertEquals(1,retry)
        screenshot("viewer-unavailable")
    }
    @Test fun conversationHasRealReceiptsAndComposerSendsMultilineDraft() {
        screen()
        compose.onNodeWithContentDescription(", Delivered", substring=true).assertExists()
        compose.onNodeWithContentDescription(", Read", substring=true).assertExists()
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
    @Test fun emptyConversationAllowsFirstMessageDuringSecureSetupAndWhenReady() {
        state=initial().copy(messages=emptyList(),security=ConversationSecurity.SETTING_UP);screen()
        for (security in listOf(ConversationSecurity.SETTING_UP,ConversationSecurity.PARTNER_SETUP,ConversationSecurity.READY)) {
            compose.runOnIdle {state=state.copy(security=security)}
            compose.onNodeWithContentDescription("Message").assertIsDisplayed().performTextReplacement("Our first message")
            compose.onNodeWithContentDescription("Send message").assertIsEnabled().performClick()
            assertEquals("Our first message",state.draft)
        }
        assertEquals(3,sends)
    }
    @Test fun emptyDeviceBlockedConversationOffersConfirmationWithoutAnErrorString() {
        state=initial().copy(messages=emptyList(),security=ConversationSecurity.REPLACE_DEVICE,error=null,draft="Keep this draft");screen()
        compose.onNodeWithText("Use chat on this device").assertIsDisplayed()
        compose.onNodeWithText("Just the two of you").assertDoesNotExist()
        compose.onNodeWithText("What made you smile today?").assertDoesNotExist()
        compose.onNodeWithContentDescription("Message").assertDoesNotExist()
        screenshot("conversation-device-required")
        compose.onNodeWithText("Use this device").performClick()
        compose.onNodeWithText("Use chat on this device?").assertIsDisplayed()
        assertEquals(0,deviceConfirmations)
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0,deviceConfirmations);assertEquals("Keep this draft",state.draft)
        compose.onNodeWithText("Use this device").performClick()
        compose.onNode(hasText("Use this device") and hasAnyAncestor(isDialog())).performClick()
        assertEquals(1,deviceConfirmations)
        // Readiness comes from the repository, never from tapping the UI action.
        compose.onNodeWithContentDescription("Message").assertDoesNotExist()
        compose.runOnIdle {state=state.copy(security=ConversationSecurity.READY)}
        compose.onNodeWithContentDescription("Message").assertIsDisplayed()
        assertEquals("Keep this draft",state.draft)
    }
    @Test fun deviceBlockedHistoryStillOffersActionWithNoErrorString() {
        state=initial().copy(security=ConversationSecurity.REPLACE_DEVICE,error=null);screen()
        compose.onNodeWithText("Use this device").assertIsDisplayed().performClick()
        compose.onNodeWithText("Use chat on this device?").assertIsDisplayed()
        assertEquals(0,deviceConfirmations)
    }
    @Test @Config(qualifiers="w320dp-h640dp-xhdpi") fun deviceSetupActionRemainsReachableWithLargeTextAndAnError() {
        state=initial().copy(messages=emptyList(),security=ConversationSecurity.REPLACE_DEVICE,error="Connection interrupted. Try again.")
        screen(large=true)
        compose.onNodeWithText("Retry").assertIsDisplayed().performClick();assertEquals(1,retries)
        compose.onNodeWithText("Use this device").performScrollTo().assertIsDisplayed()
        screenshot("conversation-device-required-large-text")
    }
    @Test fun failedSendAndDisconnectedNoticePreserveDraftAndExposeRetry() {
        state=initial().copy(error="Connection interrupted. Retrying…",draft="Keep this draft",
            messages=listOf(message("one",true,"Try this again",ChatTransport.FAILED).copy(read=true,delivered=true)))
        screen();compose.onNodeWithContentDescription(", Read", substring=true).assertDoesNotExist();compose.onNodeWithContentDescription(", Delivered", substring=true).assertDoesNotExist()
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
        content(large=true) {Column(Modifier.padding(20.dp)) {InlineVoicePlayer(attachment("voice"),false,0,0,false,"Playback failed",false,
            {retried=true},{})}}
        compose.onNodeWithContentDescription("Recorded audio waveform").assertDoesNotExist()
        compose.onNodeWithContentDescription("Seek voice message").assertDoesNotExist()
        val root=compose.onRoot().getUnclippedBoundsInRoot()
        for(label in listOf("Retry playback")) {
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
        compose.onNodeWithContentDescription(", Sending", substring=true).assertIsDisplayed()
        compose.onNodeWithContentDescription(", Delivered", substring=true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Play video").performClick();assertTrue(opened)
        screenshot("mixed-media-compact")
    }
    @Test fun thumbnailsUseTheExistingEncryptedByteBudgetAtHigherResolution() {
        val bitmap=Bitmap.createBitmap(640,480,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap);val paint=Paint()
        paint.color=0xff507c70.toInt();canvas.drawPaint(paint) // theme-audit: image pixels
        val thumbnail=AndroidChatMedia.thumbnail(bitmap)
        bitmap.recycle()
        val bytes=Base64.decode(thumbnail,Base64.NO_WRAP)
        assertTrue(bytes.size<=6144)
        val decoded=android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size)
        assertEquals(320,decoded.width);assertEquals(240,decoded.height);decoded.recycle()
    }
    private fun syntheticThumbnail(portrait: Boolean): String {
        val bitmap=Bitmap.createBitmap(if(portrait)80 else 120,if(portrait)120 else 80,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap);val paint=Paint()
        paint.color=0xffb9ced1.toInt();canvas.drawPaint(paint) // theme-audit: image pixels; synthetic landscape
        paint.color=0xfff4d99c.toInt();canvas.drawCircle(58f,25f,12f,paint) // theme-audit: image pixels
        paint.color=0xff507c70.toInt();canvas.drawOval(-20f,52f,110f,170f,paint) // theme-audit: image pixels
        paint.color=0xff314f50.toInt();canvas.drawOval(45f,65f,160f,170f,paint) // theme-audit: image pixels
        val bytes=ByteArrayOutputStream().use {bitmap.compress(Bitmap.CompressFormat.JPEG,80,it);it.toByteArray()}
        bitmap.recycle();return Base64.encodeToString(bytes,Base64.NO_WRAP)
    }
    @Test fun representativeMediaConversationAndGroupsRenderRealThumbnails() {
        val landscape=attachment("photo").copy(mime="image/jpeg",width=1200,height=800,thumbnail=syntheticThumbnail(false))
        val portrait=landscape.copy(width=800,height=1200,thumbnail=syntheticThumbnail(true))
        state=initial().copy(messages=listOf(
            message("group-1",false,"A little colour from our walk."),
            message("landscape",false,"").copy(content=ChatContent.Media(listOf(landscape),"The long way home"),reactions=mapOf(1L to "❤️",2L to "❤️")),
            message("reply",true,"").copy(content=ChatContent.Text("Let's do that again soon.",ChatQuote("landscape",2,"Photo · The long way home"))),
            message("portrait",true,"").copy(content=ChatContent.Media(listOf(portrait),"One more from today")),
            message("video",false,"").copy(content=ChatContent.Media(listOf(landscape.copy(kind="video",mime="video/mp4",durationMs=12000)),"A moment in motion")),
            message("queued",true,"I’ll send the rest when I’m back online.",ChatTransport.QUEUED),
            message("failed",true,"Saved here, ready to retry.",ChatTransport.FAILED),
            message("deleted",false,"Erased fixture").copy(deleted=true),
            message("unavailable",false,"").copy(unavailable=true)))
        screen()
        compose.onNodeWithText("The long way home").assertExists()
        compose.waitUntil(5000) {compose.onAllNodesWithContentDescription("Photo preview").fetchSemanticsNodes().isNotEmpty()}
        compose.onAllNodesWithContentDescription("Alex, partner avatar").assertCountEquals(2) // header + group start
        compose.onNodeWithContentDescription("❤️, 2 reactions, including yours").assertIsSelected()
        screenshot("redesign-landscape-reply")
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(3)
        screenshot("redesign-portrait-video")
        for(preset in ChatThemePreset.entries) {
            compose.runOnIdle {state=state.copy(preferences=state.preferences.copy(chatTheme=preset.id))}
            screenshot("media-${preset.id}-light")
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(5)
        compose.onNodeWithContentDescription(", Queued", substring=true).assertExists();compose.onNodeWithText("Retry send").assertExists()
        screenshot("redesign-message-states")
    }
    @Test @Config(qualifiers="w320dp-h800dp-xhdpi") fun photoFooterStaysOutsideImageAndKeepsFailureRecoveryAtLargeText() {
        val photo=attachment("photo").copy(mime="image/jpeg",width=1200,height=800,thumbnail=syntheticThumbnail(false))
        var item by mutableStateOf(message("photo",true,"",ChatTransport.SENDING).copy(
            content=ChatContent.Media(listOf(photo),"A longer caption with a little context from today.",ChatQuote("one",2,"That view from the walk"))))
        content(large=true) {SriSuChatTheme(ChatThemePreset.DEFAULT) {Column(Modifier.padding(16.dp)) {
            MessageBubble(item,true,"Alex",1L,false,{},{},{},{retries++},{},
                if(item.transport==ChatTransport.SENDING).42f else null,emptySet())
        }}}
        compose.waitUntil(5000) {compose.onAllNodesWithContentDescription("Photo preview",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        val photoBounds=compose.onNodeWithContentDescription("Photo preview",useUnmergedTree=true).getUnclippedBoundsInRoot()
        val footerBounds=compose.onNodeWithContentDescription(", Sending",substring=true,useUnmergedTree=true).getUnclippedBoundsInRoot()
        assertTrue("Footer $footerBounds must follow photo $photoBounds",footerBounds.top>=photoBounds.bottom)
        compose.onNodeWithText("Uploading 42%").assertIsDisplayed()
        screenshot("photo-large-upload")
        compose.runOnIdle {item=item.copy(transport=ChatTransport.FAILED)}
        compose.onNodeWithText("Retry send").assertIsDisplayed().performClick();assertEquals(1,retries)
        screenshot("photo-large-failed")
        compose.runOnIdle {item=item.copy(transport=ChatTransport.SENT,read=true,content=ChatContent.Media(listOf(photo)))}
        compose.onNodeWithContentDescription(", Read",substring=true).assertIsDisplayed()
        compose.onNodeWithText("Retry send").assertDoesNotExist()
        screenshot("photo-large-no-caption")
    }
    @Test fun photoAndVideoHaveTheSameEdgeToEdgeTreatmentInDarkAppearance() {
        val photo=attachment("photo").copy(mime="image/jpeg",width=1200,height=800,thumbnail=syntheticThumbnail(false))
        state=initial().copy(messages=listOf(
            message("photo",true,"").copy(read=true,content=ChatContent.Media(listOf(photo),"The long way home")),
            message("video",false,"").copy(content=ChatContent.Media(listOf(photo.copy(kind="video",durationMs=12000)))),
            message("missing",true,"").copy(content=ChatContent.Media(listOf(photo.copy(thumbnail=null))))))
        screen(dark=true)
        compose.waitUntil(5000) {compose.onAllNodesWithContentDescription("Photo preview").fetchSemanticsNodes().isNotEmpty()}
        screenshot("media-iris-dark")
    }

    @Test fun inlineVoiceControlsShowElapsedProgressAndNeverOpenAModal() {
        var position by mutableStateOf(4000L);var playing by mutableStateOf(true)
        content {Column(Modifier.padding(16.dp)) {
            InlineVoicePlayer(attachment("voice"),playing,position,18000,false,null,true,
                {playing=!playing},{position=(it*18000).toLong()})
            Text("The conversation stays here")
        }}
        compose.onNodeWithText("0:04 / 0:18").assertIsDisplayed()
        compose.onNodeWithContentDescription("Pause voice message").performClick()
        compose.onNodeWithContentDescription("Play voice message").assertIsDisplayed()
        compose.onNodeWithContentDescription("Seek voice message").performSemanticsAction(SemanticsActions.SetProgress) {it(.5f)}
        compose.onNodeWithText("0:09 / 0:18").assertIsDisplayed()
        compose.onAllNodes(isDialog()).assertCountEquals(0)
        screenshot("redesign-inline-voice")
    }
    @Test @Config(qualifiers="w320dp-h420dp-xhdpi") fun constrainedLargeTextReplyFocusAndDraftRemainUsable() {
        state=initial().copy(draft="Keep every word of this draft",messages=emptyList())
        screen(large=true)
        compose.runOnIdle {state=state.copy(reply=ChatQuote("one",2,"This is the original thought"))}
        val input=compose.onNodeWithContentDescription("Message")
        input.assertIsFocused()
        val root=compose.onRoot().getUnclippedBoundsInRoot()
        val inputBounds=input.getUnclippedBoundsInRoot()
        assertTrue(inputBounds.right-inputBounds.left>(root.right-root.left)*.8f)
        for(label in listOf("Message","Send message","Cancel reply","Attach or share")) {
            val bounds=compose.onNodeWithContentDescription(label).getUnclippedBoundsInRoot()
            assertTrue("$label vertical bounds",bounds.top>=root.top && bounds.bottom<=root.bottom)
        }
        screenshot("review-constrained-reply")
        compose.onNodeWithContentDescription("Cancel reply").performClick()
        assertNull(state.reply);assertEquals("Keep every word of this draft",state.draft)
        input.assertIsFocused()
    }
    @Test fun reactionTargetsAndThumbnailDecodeKeepTheReadingAnchor() {
        val photo=attachment("photo").copy(width=1200,height=800,thumbnail="AQID")
        var media by mutableStateOf(photo)
        content {Column(Modifier.padding(16.dp)) {
            MessageBubble(message("reaction",false,"A thought").copy(reactions=mapOf(1L to "❤️")),false,"Alex",1,false,
                {},{}, {},{}, {},null,emptySet())
            MediaPreview(media,{},Modifier.width(240.dp))
            Text("Stable content below the preview")
        }}
        val chip=compose.onNodeWithContentDescription("❤️, 1 reaction, including yours")
        chip.assertIsSelected()
        val bounds=chip.getUnclippedBoundsInRoot()
        assertTrue(bounds.right-bounds.left>=48.dp);assertTrue(bounds.bottom-bounds.top>=48.dp)
        compose.waitUntil(5000) {compose.onAllNodesWithText("Preview unavailable").fetchSemanticsNodes().isNotEmpty()}
        val before=compose.onNodeWithText("Stable content below the preview").getUnclippedBoundsInRoot()
        compose.runOnIdle {media=photo.copy(thumbnail=syntheticThumbnail(false))}
        compose.waitUntil(5000) {compose.onAllNodesWithContentDescription("Photo preview").fetchSemanticsNodes().isNotEmpty()}
        assertEquals(before,compose.onNodeWithText("Stable content below the preview").getUnclippedBoundsInRoot())
        screenshot("review-thumbnail-reaction")
    }
    @Test fun voiceSeekGestureDoesNotStartAReplyAndLoadingHasNoSeekHandle() {
        var position by mutableStateOf(4000L)
        var loading by mutableStateOf(false)
        content {Column(Modifier.padding(16.dp)) {
            MessageBubble(message("voice",true,"").copy(content=ChatContent.Media(listOf(attachment("voice")))),true,"Alex",1,false,
                {},{replies++},{},{},{},null,emptySet(),voiceContent={media,modifier ->
                    InlineVoicePlayer(media,true,position,18000,loading,null,true,{}, {position=(it*18000).toLong()},modifier)
                })
        }}
        compose.onNodeWithContentDescription("Seek voice message").performTouchInput {swipe(androidx.compose.ui.geometry.Offset(width*.23f,centerY),androidx.compose.ui.geometry.Offset(width*.85f,centerY))}
        assertTrue("Seek position $position, reply callbacks $replies",position>4000);assertEquals(0,replies)
        compose.runOnIdle {loading=true}
        compose.onNodeWithContentDescription("Seek voice message").assertDoesNotExist()
        compose.onNodeWithContentDescription("Opening voice message").assertExists()
        screenshot("review-voice-loading")
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
