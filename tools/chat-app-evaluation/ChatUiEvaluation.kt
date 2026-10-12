package com.srisu.srisu.features.couplechat.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import com.srisu.srisu.features.couplechat.data.*
import com.srisu.srisu.theme.*
import androidx.navigation.*
import androidx.navigation.compose.*
import com.srisu.srisu.navigation.AppNavigator
import com.srisu.srisu.navigation.graph.ChatNav
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.io.encoding.Base64
import platform.UIKit.UIViewController

/** Synthetic simulator UI only; this folder is excluded from normal/release builds. */
class ChatUiEvaluation(private val photo: ByteArray, private val voice: ByteArray, private val native: NativeChatMedia) {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
    private fun message(id:String,own:Boolean,text:String)=LocalChatMessage(id,"fixture-room",if(own)1 else 2,
        "2026-10-12T10:00:00Z",ChatContent.Text(text),transport=ChatTransport.SENT,sequence=1,timelineKey=id)
    private fun attachment(kind:String,id:String)=ChatMedia(AttachmentDescriptor(id,"fixture-room",id,"synthetic","synthetic",1,"synthetic"),
        kind,if(kind=="voice")"audio/wav" else "image/jpeg",width=1200,height=800,durationMs=18000,
        thumbnail=if(kind=="voice")null else Base64.encode(photo))
    private val audio=attachment("voice","voice")
    private val state=MutableStateFlow(ConversationState(accountId=1,roomId="fixture-room",partnerId=2,partnerName="Alex",
        security=ConversationSecurity.READY,loading=false,messages=listOf(
            message("one",false,"Made it home. How was your afternoon?"),
            message("two",true,"A little busy, but this made me smile.").copy(delivered=true),
            message("three",false,"Tell me about it when you have a moment."),
            message("voice",true,"").copy(content=ChatContent.Media(listOf(audio))),
            message("five",true,"Tea and a walk later?").copy(read=true,reactions=mapOf(2L to "❤️")),
            message("photo",false,"").copy(content=ChatContent.Media(listOf(attachment("photo","photo")),"The long way home")),
            message("portrait",true,"").copy(content=ChatContent.Media(listOf(attachment("photo","portrait").copy(width=800,height=1200)),
                "One more from today",ChatQuote("photo",2,"Photo · The long way home"))),
            message("video",false,"").copy(content=ChatContent.Media(listOf(attachment("video","video").copy(mime="video/mp4")),"A moment in motion")),
            message("failed",true,"Saved here, ready to retry.").copy(transport=ChatTransport.FAILED),
            message("deleted",false,"").copy(deleted=true))))
    private val player=ChatPlaybackOwner(native,scope,state,{})
    private val page=MutableStateFlow(0)
    private val appearance=MutableStateFlow(false)
    private val romanticEffects=MutableSharedFlow<RomanticEffect>(extraBufferCapacity=1)
    private var romanticSerial=0
    fun showRomanticMessage(text: String) {
        val id="romance-${++romanticSerial}"
        val next=message(id,romanticSerial%2==0,text).copy(sequence=100L+romanticSerial)
        state.value=state.value.copy(messages=state.value.messages+next)
        page.value=state.value.messages.lastIndex
        confirmedMessageArrival(1,null,next,visible=true,live=true)?.let { arrival ->
            RomanticPhrases.match(arrival.text)?.let { romanticEffects.tryEmit(RomanticEffect(1,"fixture-room",id,it)) }
        }
    }
    private var navigation: NavHostController? = null
    private var video=ByteArray(0)
    private var videoPlayer: ChatPlaybackOwner? = null
    private var reading: androidx.compose.foundation.lazy.LazyListState? = null
    val readingIndex get()=reading?.firstVisibleItemIndex ?: -1
    val videoPosition get()=videoPlayer?.position ?: 0
    val videoPlaying get()=videoPlayer?.playing == true
    fun setVideoFixture(bytes: ByteArray) { video=bytes }
    fun selectTheme(id: String) {state.value=state.value.copy(preferences=state.value.preferences.copy(chatTheme=id))}
    fun showAppearance() {appearance.value=true}
    fun hideAppearance() {appearance.value=false}
    fun showViewer(kind: String) {player.stop();navigation?.let {AppNavigator(it).open(ChatNav.MediaViewer("fixture-room",kind,kind))}}
    fun returnToChat() {navigation?.let {AppNavigator(it).back()}}
    fun pauseVideo() {videoPlayer?.togglePlayback()}
    fun seekVideo() {videoPlayer?.seek(.5f)}
    val playing get()=player.playing
    val position get()=player.position
    val activeMessage get()=player.activeMessageId
    fun playVoice() {state.value=state.value.copy(openedMedia=OpenedChatMedia("voice",audio,voice))}
    fun showMedia() {page.value=5}
    fun showReply() {state.value=state.value.copy(draft="A thought\nand another line",reply=ChatQuote("one",2,"Made it home. How was your afternoon?"))}
    fun showEmptyConversation(deviceRequired: Boolean) {
        state.value=state.value.copy(messages=emptyList(),draft="",reply=null,error=null,
            security=if(deviceRequired)ConversationSecurity.REPLACE_DEVICE else ConversationSecurity.READY)
    }
    fun close() {player.close();scope.cancel();native.close()}
    fun controller(): UIViewController = ComposeUIViewController {
        val avatar=privatePhotoBitmap(photo)
        val current by state.collectAsState()
        val target by page.collectAsState()
        val controller=rememberNavController()
        SideEffect {navigation=controller}
        val choosing by appearance.collectAsState()
        SriSuTheme(darkTheme=false) {
        NavHost(controller,startDestination=ChatNav.CoupleConversation("fixture-room")) {
        composable<ChatNav.CoupleConversation> {
            val list=rememberLazyListState()
            SideEffect {reading=list}
            LaunchedEffect(target) {list.scrollToItem(target)}
            SriSuChatTheme(ChatThemePreset.fromId(current.preferences.chatTheme)) {
            Box(Modifier.fillMaxSize()) {
            ConversationSurface(current,list,{}, {}, {}, {}, {}, {}, {}, {},partnerPhoto=avatar,
                composer={ConversationComposer(current,false,false,{state.value=current.copy(draft=it)}, {}, {},
                    {state.value=current.copy(reply=null)},voice={FilledIconButton(onClick={}) {Icon(ChatIcons.Mic,"Record voice message")}})},
                messageContent={index,message -> MessageBubble(message,message.authorId==1L,"Alex",1,
                    messagesGroup(current.messages.getOrNull(index-1),message),{}, {state.value=current.copy(reply=ChatQuote(message.id,message.authorId,"A thought"))}, {}, {}, {},null,emptySet(),
                    groupContinues=messagesGroup(message,current.messages.getOrNull(index+1)),partnerPhoto=avatar,
                    afterReactions=current.messages.getOrNull(index-1)?.hasVisibleReactions == true,
                    voiceContent={media,modifier ->
                        val active=player.activeMessageId==message.id
                        InlineVoicePlayer(media,active && player.playing,if(active)player.position else 0,if(active)player.duration else 0,
                            active && player.playbackLoading,if(active)player.playbackError else null,true,
                            {if(active)player.togglePlayback() else playVoice()},player::seek,modifier)
                    })})
            RomanticMessageOverlay(romanticEffects,1,"fixture-room",!choosing,Modifier.matchParentSize().imePadding().safeDrawingPadding())
            }
            }
        }
        composable<ChatNav.MediaViewer> { entry ->
            val route=entry.toRoute<ChatNav.MediaViewer>()
            val driver=rememberNativeChatMedia()
            val owner=remember {ChatPlaybackOwner(driver,scope,state,{},autoPlayKind="video")}
            SideEffect {videoPlayer=owner}
            LaunchedEffect(route) {
                val item=attachment(route.attachmentId,route.attachmentId).copy(mime=if(route.attachmentId=="video")"video/mp4" else "image/jpeg")
                state.value=state.value.copy(openedMedia=OpenedChatMedia(route.messageId,item,if(item.kind=="video")video else photo))
            }
            DisposableEffect(owner) {onDispose {owner.close();driver.close();videoPlayer=null;state.value=state.value.copy(openedMedia=null)}}
            ChatMediaViewerSurface(current.openedMedia,false,null,"A moment from today",AppNavigator(controller,entry)::back,{},
                video={NativeChatVideo(driver,Modifier.fillMaxSize())},videoControls={VideoControls(owner,{})})
        }
        }
        if(choosing)ChatAppearanceSheet(current.preferences.chatTheme,{selectTheme(it);appearance.value=false},{appearance.value=false})
        }
    }
}
