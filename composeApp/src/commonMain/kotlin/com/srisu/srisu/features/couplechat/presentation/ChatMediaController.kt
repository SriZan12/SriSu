package com.srisu.srisu.features.couplechat.presentation

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.srisu.srisu.features.couplechat.data.*
import com.srisu.srisu.features.couplechat.crypto.ChatJson
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

internal class ChatMediaController(private val native: NativeChatMedia, private val repository: ConversationRepository,
    private val scope: CoroutineScope) {
    var recording by mutableStateOf(false)
        private set
    var locked by mutableStateOf(false)
        private set
    var elapsed by mutableLongStateOf(0)
        private set
    var levels by mutableStateOf<List<Int>>(emptyList())
        private set
    var preparing by mutableStateOf(false)
        private set
    var playing by mutableStateOf(false)
        private set
    var position by mutableLongStateOf(0)
        private set
    var duration by mutableLongStateOf(0)
        private set
    var speed by mutableFloatStateOf(1f)
        private set
    var playbackLoading by mutableStateOf(false)
        private set
    var playbackError by mutableStateOf<String?>(null)
        private set
    private var playbackEnded = false
    private var sendRecording = false
    private var generation = 0

    fun safetyQr(value: String): ByteArray? = native.safetyQr("srisu-safety:1:$value")
    fun scanSafetyQr(fromPhoto: Boolean) {
        val account=repository.state.value.accountId;val room=repository.state.value.roomId
        native.pick(if(fromPhoto)"safety-photo" else "safety-camera") {raw,_ ->
            scope.launch(Dispatchers.Main) {
                if(repository.state.value.accountId!=account || repository.state.value.roomId!=room || repository.state.value.safetyCode==null)return@launch
                try {val event=ChatJson.parseToJsonElement(raw).jsonObject
                    if(event["event"]?.jsonPrimitive?.content=="qr")repository.verifySafetyQr(event["value"]?.jsonPrimitive?.content.orEmpty())
                    else if(event["event"]?.jsonPrimitive?.content!="cancelled")repository.mediaError("The QR could not be read. Allow camera access or choose a clear QR photo.")
                } catch(_: kotlinx.serialization.SerializationException) {repository.mediaError("The QR could not be read.")}
            }
        }
    }

    private fun receive(raw: String, bytes: ByteArray?, ownGeneration: Int, account: Long?, room: String?) {
        scope.launch(Dispatchers.Main) {
            if (generation != ownGeneration || repository.state.value.accountId != account || repository.state.value.roomId != room) return@launch
            try {
                val value = ChatJson.parseToJsonElement(raw).jsonObject
                when (value["event"]?.jsonPrimitive?.content) {
                    "recording" -> { recording = true; preparing = false }
                    "level" -> {
                        elapsed = value["durationMs"]!!.jsonPrimitive.long
                        levels = (levels + value["level"]!!.jsonPrimitive.int.coerceIn(0,100)).takeLast(100)
                    }
                    "cancelled" -> { recording = false; preparing = false; locked = false }
                    "ready" -> {
                        recording = false; preparing = false; locked = false
                        if (bytes == null || bytes.isEmpty() || bytes.size > 16*1024*1024) error("Invalid media")
                        repository.prepareAttachments(listOf(PreparedChatMedia(bytes,
                            value["kind"]!!.jsonPrimitive.content,value["mime"]!!.jsonPrimitive.content,
                            value["width"]?.jsonPrimitive?.int ?: 0,value["height"]?.jsonPrimitive?.int ?: 0,
                            value["durationMs"]?.jsonPrimitive?.long ?: 0,value["thumbnail"]?.jsonPrimitive?.contentOrNull,
                            value["waveform"]?.jsonArray?.map {it.jsonPrimitive.int} ?: emptyList())),sendWhenReady=sendRecording)
                        sendRecording = false
                    }
                    "error" -> { recording = false; preparing = false; locked = false
                        repository.mediaError(when(value["code"]?.jsonPrimitive?.content) {
                            "permission" -> "Microphone or camera access is off. You can allow it in system settings."
                            "interrupted" -> "Recording stopped because audio was interrupted. Nothing was sent."
                            "limit" -> "Choose media under 16 MiB and two minutes. Photos are resized before sending."
                            else -> "This media could not be prepared. Try another file."
                        })
                    }
                }
            } catch (_: Exception) { preparing = false; recording = false; repository.mediaError("This media could not be prepared.") }
        }
    }
    fun pick(source: String) {
        if (preparing || recording) return
        val state=repository.state.value; val current=++generation
        sendRecording=false; preparing=true
        native.pick(source) {raw,bytes -> receive(raw,bytes,current,state.accountId,state.roomId)}
    }
    fun record(lock: Boolean = false) {
        if (preparing || recording) return
        val state=repository.state.value; val current=++generation
        sendRecording=false; locked=lock; elapsed=0; levels=emptyList(); preparing=true
        native.startRecording {raw,bytes -> receive(raw,bytes,current,state.accountId,state.roomId)}
    }
    fun lock() { locked=true }
    fun stop(discard: Boolean, send: Boolean = false) {
        sendRecording=send && !discard
        native.stopRecording(discard)
        recording=false; preparing=false; locked=false
    }
    fun play(opened: OpenedChatMedia) {
        position=0; duration=0; speed=1f; playing=false; playbackLoading=true; playbackError=null; playbackEnded=false
        native.play(opened.bytes,opened.media.mime) {raw -> scope.launch(Dispatchers.Main) {
            if(repository.state.value.openedMedia?.media?.descriptor?.id != opened.media.descriptor.id) return@launch
            try {
                val value=ChatJson.parseToJsonElement(raw).jsonObject
                when(value["event"]?.jsonPrimitive?.content) {
                    "playing" -> { playing=true; playbackLoading=false; playbackEnded=false; if(opened.media.kind=="voice")repository.voicePlayed(opened.messageId) }
                    "position" -> { position=value["positionMs"]!!.jsonPrimitive.long; duration=value["durationMs"]!!.jsonPrimitive.long }
                    "paused" -> playing=false
                    "ended" -> {playing=false;playbackEnded=true;position=duration}
                    "error" -> { playing=false; playbackLoading=false; playbackError="This media could not play. Try again." }
                }
            } catch (_: Exception) { playing=false; playbackLoading=false; playbackError="Playback could not continue. Try again." }
        } }
    }
    fun togglePlayback() {
        if(playbackEnded) {native.playback("seek",0.0);playbackEnded=false}
        native.playback(if(playing) "pause" else "resume",0.0)
    }
    fun seek(fraction: Float) { native.playback("seek",duration*fraction.toDouble()) }
    fun speed() { speed=when(speed) {1f->1.5f;1.5f->2f;else->1f}; native.playback("speed",speed.toDouble()) }
    fun dismiss() { stopPlayer(); repository.dismissMedia() }
    fun stopPlayer() { native.playback("stop",0.0); playing=false; position=0; duration=0; speed=1f; playbackLoading=false; playbackError=null; playbackEnded=false }
    fun export(opened: OpenedChatMedia) { native.export(opened.bytes,opened.media.mime) { result ->
        if(result=="error") scope.launch(Dispatchers.Main) { repository.mediaError("The export could not be completed.") }
    } }
    fun interrupt() { generation++; native.interrupt(); recording=false; locked=false; preparing=false; playing=false; sendRecording=false }
    fun close() { interrupt(); native.close() }
}

@Composable internal fun rememberChatMediaController(repository: ConversationRepository): ChatMediaController {
    val native=rememberNativeChatMedia(); val scope=rememberCoroutineScope()
    val controller=remember(native,repository) {ChatMediaController(native,repository,scope)}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle,controller) {
        val observer=LifecycleEventObserver {_,event -> if(event==Lifecycle.Event.ON_STOP) {
            // Opening a system picker also stops the activity. It has no active
            // recording/player; preserve only that explicitly requested callback.
            if(controller.recording || controller.playing) controller.interrupt()
        }}
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); controller.close() }
    }
    return controller
}
