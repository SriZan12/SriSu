package com.srisu.srisu.features.couplechat.presentation

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.srisu.srisu.features.couplechat.data.*
import kotlinx.serialization.json.Json as ChatJson
import com.srisu.srisu.features.chat.matrix.presentation.SecureChatViewModel
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

internal class ChatMediaController(private val native: NativeChatMedia, private val repository: SecureChatViewModel,
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
    val player = ChatPlaybackOwner(native, scope, repository.uiState, {})
    private var relinquished = false
    private var sendRecording = false
    private var generation = 0

    private fun receive(raw: String, bytes: ByteArray?, ownGeneration: Int, account: Long?, room: String?) {
        scope.launch(Dispatchers.Main) {
            if (generation != ownGeneration || repository.uiState.value.accountId != account || repository.uiState.value.roomId != room) return@launch
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
        // Pause inline audio before the system picker stops our foreground view.
        // Otherwise that lifecycle stop would invalidate the picker callback.
        dismiss()
        val state=repository.uiState.value; val current=++generation
        sendRecording=false; preparing=true
        native.pick(source) {raw,bytes -> receive(raw,bytes,current,state.accountId,state.roomId)}
    }
    fun record(lock: Boolean = false) {
        if (preparing || recording) return
        val state=repository.uiState.value; val current=++generation
        dismiss()
        sendRecording=false; locked=lock; elapsed=0; levels=emptyList(); preparing=true
        native.startRecording {raw,bytes -> receive(raw,bytes,current,state.accountId,state.roomId)}
    }
    fun lock() { locked=true }
    fun stop(discard: Boolean, send: Boolean = false) {
        sendRecording=send && !discard
        native.stopRecording(discard)
        recording=false; preparing=false; locked=false
    }
    fun play(opened: OpenedChatMedia) = player.play(opened)
    fun openVoice(messageId: String, attachmentId: String, draft: Boolean = false) {
        if (player.activeMessageId == messageId && player.activeAttachmentId == attachmentId && player.playbackError == null) player.togglePlayback()
        else if (draft) repository.openDraftAttachment(attachmentId)
        else repository.openAttachment(messageId, attachmentId)
    }
    fun dismiss() { player.stop(); repository.dismissMedia() }
    fun export(opened: OpenedChatMedia) { native.export(opened.bytes,opened.media.mime) { result ->
        if(result=="error") scope.launch(Dispatchers.Main) { repository.mediaError("The export could not be completed.") }
    } }
    fun resumeConversation() { relinquished=false }
    fun handoffToViewer() { interrupt(); relinquished=true }
    fun interrupt() { generation++; player.stop(); if(!relinquished)repository.dismissMedia(); native.interrupt(); recording=false; locked=false; preparing=false; sendRecording=false }
    fun close() { player.close(); interrupt(); native.close() }
}

@Composable internal fun rememberChatMediaController(repository: SecureChatViewModel): ChatMediaController {
    val native=rememberNativeChatMedia(); val scope=rememberCoroutineScope()
    val controller=remember(native,repository) {ChatMediaController(native,repository,scope)}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle,controller) {
        val observer=LifecycleEventObserver {_,event -> if(event==Lifecycle.Event.ON_STOP) {
            // Opening a system picker also stops the activity. It has no active
            // recording/player; preserve only that explicitly requested callback.
            if(controller.recording || controller.player.activeMessageId != null || repository.uiState.value.mediaLoading) controller.interrupt()
        }}
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); controller.close() }
    }
    return controller
}
