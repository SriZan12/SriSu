package com.srisu.srisu.features.couplechat.presentation

import androidx.compose.runtime.*
import com.srisu.srisu.features.couplechat.data.*
import com.srisu.srisu.features.couplechat.crypto.ChatJson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

/** One native player for the destination, keyed by message AND attachment.
 * A replaced/stopped player's late callbacks cannot modify the current bubble. */
internal class ChatPlaybackOwner(private val native: NativeChatMedia, private val scope: CoroutineScope,
    private val state: StateFlow<ConversationState>, private val onPlayed: (String) -> Unit, private val autoPlayKind: String = "voice") {
    var playing by mutableStateOf(false)
        private set
    var position by mutableLongStateOf(0)
        private set
    var duration by mutableLongStateOf(0)
        private set
    var playbackLoading by mutableStateOf(false)
        private set
    var playbackError by mutableStateOf<String?>(null)
        private set
    var activeMessageId by mutableStateOf<String?>(null)
        private set
    var activeAttachmentId by mutableStateOf<String?>(null)
        private set
    private var playbackGeneration = 0
    private var playbackEnded = false
    private val observer = scope.launch {
            state.map { Triple(it.accountId, it.roomId, it.openedMedia) }.distinctUntilChanged().collect { (_, _, opened) ->
                stop()
                if (opened?.media?.kind == autoPlayKind) play(opened)
            }
    }

    fun play(opened: OpenedChatMedia) {
        // A stale UI callback must not replay private bytes after leaving/switching accounts.
        val current = state.value
        if(current.accountId == null || current.openedMedia !== opened || current.roomId != opened.media.descriptor.roomId) return
        stop()
        val ownGeneration = playbackGeneration
        val account = state.value.accountId
        val room = state.value.roomId
        val target = ChatMediaRequest(opened.messageId, opened.media.descriptor.id, opened.media.kind)
        activeMessageId=target.messageId; activeAttachmentId=target.attachmentId
        duration=opened.media.durationMs; playbackLoading=true
        native.play(opened.bytes,opened.media.mime) {raw -> scope.launch(Dispatchers.Main) {
            val current = state.value
            if (playbackGeneration != ownGeneration || current.accountId != account || current.roomId != room ||
                current.openedMedia?.messageId != target.messageId ||
                current.openedMedia.media.descriptor.id != target.attachmentId) return@launch
            try {
                val value=ChatJson.parseToJsonElement(raw).jsonObject
                when(value["event"]?.jsonPrimitive?.content) {
                    "playing" -> { playing=true; playbackLoading=false; playbackEnded=false; if(target.kind=="voice")onPlayed(target.messageId) }
                    "position" -> { position=value["positionMs"]!!.jsonPrimitive.long.coerceAtLeast(0); duration=value["durationMs"]!!.jsonPrimitive.long.coerceAtLeast(0) }
                    "buffering" -> {playbackLoading=true}
                    "paused" -> {playing=false; playbackLoading=false}
                    "ended" -> {playing=false;playbackLoading=false;playbackEnded=true;position=duration}
                    "error" -> { playing=false; playbackLoading=false; playbackError=if(target.kind=="video")"Couldn’t play this video. Tap to retry." else "Couldn’t play this recording. Tap to retry." }
                }
            } catch (_: Exception) { playing=false; playbackLoading=false; playbackError="Playback stopped. Tap to retry." }
        } }
    }
    fun togglePlayback() {
        if(playbackEnded) {native.playback("seek",0.0);playbackEnded=false}
        native.playback(if(playing) "pause" else "resume",0.0)
    }
    fun seek(fraction: Float) { if (duration > 0 && !playbackLoading) { playbackEnded=false; native.playback("seek",duration*fraction.coerceIn(0f,1f).toDouble()) } }
    fun stop() { playbackGeneration++; activeMessageId=null; activeAttachmentId=null; native.playback("stop",0.0); playing=false; position=0; duration=0; playbackLoading=false; playbackError=null; playbackEnded=false }
    fun close() { observer.cancel(); stop() }
}
