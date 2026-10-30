@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.srisu.srisu.features.couplechat.presentation

import com.srisu.srisu.features.couplechat.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*

class ChatPlaybackOwnerTest {
    private val dispatcher=StandardTestDispatcher()
    @BeforeTest fun before() {Dispatchers.setMain(dispatcher)}
    @AfterTest fun after() {Dispatchers.resetMain()}
    private fun opened(message: String, attachment: String=message) = OpenedChatMedia(message,
        ChatMedia(AttachmentDescriptor(attachment,"room",message),
            "voice","audio/wav",durationMs=18000),byteArrayOf(1))

    @Test fun videoOwnerReportsBufferingAndIgnoresThemeChangesAndLateCallbacks() = runTest(dispatcher) {
        val native=PlayerDouble();val state=MutableStateFlow(ConversationState(accountId=1,roomId="room"))
        val player=ChatPlaybackOwner(native,backgroundScope,state,{},autoPlayKind="video")
        runCurrent()
        state.value=state.value.copy(openedMedia=opened("voice"));runCurrent();assertTrue(native.callbacks.isEmpty())
        val clip=opened("video").let {it.copy(media=it.media.copy(kind="video",mime="video/mp4"))}
        state.value=state.value.copy(openedMedia=clip);runCurrent()
        val callback=native.callbacks.single()
        callback("""{"event":"buffering"}""");runCurrent();assertTrue(player.playbackLoading)
        callback("""{"event":"playing"}""");runCurrent();assertTrue(player.playing)
        state.value=state.value.copy(preferences=ConversationPreferences(chatTheme="calm"));runCurrent()
        assertEquals(1,native.callbacks.size);assertTrue(player.playing)
        player.stop();callback("""{"event":"playing"}""");runCurrent();assertFalse(player.playing)
        player.close()
    }
    @Test fun replacementRejectsLateCallbacksAndReactionsNeverRestartPlayback() = runTest(dispatcher) {
        val native=PlayerDouble();val state=MutableStateFlow(ConversationState(accountId=1,roomId="room"))
        val played=mutableListOf<String>();val player=ChatPlaybackOwner(native,backgroundScope,state,played::add)
        runCurrent();state.value=state.value.copy(openedMedia=opened("one"));runCurrent()
        assertTrue(player.playbackLoading);assertEquals("one",player.activeMessageId)
        native.callbacks[0]("""{"event":"playing"}""");runCurrent()
        native.callbacks[0]("""{"event":"position","positionMs":4000,"durationMs":18000}""");runCurrent()
        assertEquals(4000,player.position);assertEquals(listOf("one"),played)
        state.value=state.value.copy(draft="Typing while listening")
        runCurrent();assertEquals(1,native.callbacks.size);assertEquals(4000,player.position)
        // A second attachment in the SAME message must also be a new owner.
        state.value=state.value.copy(openedMedia=opened("one","second"));runCurrent()
        native.callbacks[0]("""{"event":"position","positionMs":15000,"durationMs":18000}""")
        runCurrent();assertEquals(0,player.position);assertEquals("second",player.activeAttachmentId)
        native.callbacks[1]("""{"event":"playing"}""");runCurrent();assertTrue(player.playing)
        assertTrue(native.actions.count {it.first=="stop"}>=2)
        player.close()
    }
    @Test fun pauseSeekEndRetryAndAccountChangeUseTheNativeOwner() = runTest(dispatcher) {
        val native=PlayerDouble();val state=MutableStateFlow(ConversationState(accountId=1,roomId="room"))
        val player=ChatPlaybackOwner(native,backgroundScope,state,{})
        runCurrent();state.value=state.value.copy(openedMedia=opened("one"));runCurrent()
        native.callbacks.last()("""{"event":"playing"}""");runCurrent()
        player.togglePlayback();assertEquals("pause",native.actions.last().first)
        native.callbacks.last()("""{"event":"paused"}""");runCurrent();assertFalse(player.playing)
        player.seek(.5f);assertEquals("seek" to 9000.0,native.actions.last())
        player.togglePlayback();assertEquals("resume",native.actions.last().first)
        native.callbacks.last()("""{"event":"ended"}""");runCurrent()
        player.togglePlayback();assertEquals(listOf("seek" to 0.0,"resume" to 0.0),native.actions.takeLast(2))
        native.callbacks.last()("""{"event":"error"}""");runCurrent();assertNotNull(player.playbackError)
        player.play(state.value.openedMedia!!);assertNull(player.playbackError);assertTrue(player.playbackLoading)
        val stale=native.callbacks.last()
        val previous=state.value.openedMedia!!
        state.value=ConversationState(accountId=2);runCurrent()
        val count=native.callbacks.size;player.play(previous);assertEquals(count,native.callbacks.size)
        stale("""{"event":"playing"}""");runCurrent()
        assertNull(player.activeMessageId);assertFalse(player.playing);assertEquals(0,player.position)
        player.close()
    }
    @Test fun deletionAndClosingReleasePlayerAndDoNotAcceptPendingCallbacks() = runTest(dispatcher) {
        val native=PlayerDouble();val state=MutableStateFlow(ConversationState(accountId=1,roomId="room",openedMedia=opened("one")))
        val player=ChatPlaybackOwner(native,backgroundScope,state,{})
        runCurrent();val stale=native.callbacks.single()
        state.value=state.value.copy(openedMedia=null);runCurrent()
        assertNull(player.activeMessageId)
        state.value=state.value.copy(openedMedia=opened("two"));runCurrent()
        player.close();stale("""{"event":"playing"}""");runCurrent()
        assertFalse(player.playing);assertNull(player.activeMessageId)
        state.value=state.value.copy(openedMedia=opened("three"));runCurrent()
        assertEquals(2,native.callbacks.size)
    }
}

private class PlayerDouble : NativeChatMedia {
    val callbacks=mutableListOf<(String)->Unit>()
    val actions=mutableListOf<Pair<String,Double>>()
    override fun play(bytes:ByteArray,mime:String,result:(String)->Unit) {callbacks+=result}
    override fun playback(action:String,value:Double) {actions+=action to value}
    override fun safetyQr(text:String):ByteArray?=null
    override fun pickPhotos(limit:Int,result:(com.srisu.srisu.features.couplechat.presentation.NativePhotoBatch)->Unit)=Unit
    override fun pick(source:String,result:(String,ByteArray?)->Unit)=Unit
    override fun startRecording(result:(String,ByteArray?)->Unit)=Unit
    override fun stopRecording(discard:Boolean)=Unit
    override fun export(bytes:ByteArray,mime:String,result:(String)->Unit)=Unit
    override fun interrupt()=Unit
    override fun close()=Unit
}
