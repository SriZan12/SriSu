@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package com.srisu.srisu.features.chat.matrix.presentation

import com.srisu.srisu.features.chat.matrix.*
import com.srisu.srisu.features.moments.data.*
import com.srisu.srisu.features.moments.presentation.MomentReferenceResolver
import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.features.couplechat.data.*
import com.srisu.srisu.features.couplechat.presentation.*
import com.srisu.srisu.core.session.SessionStorage
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.uuid.Uuid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srisu.srisu.features.chat.matrix.ChatPartnerIdentity
import com.srisu.srisu.features.chat.matrix.ChatPartnerSource
import com.srisu.srisu.features.chat.matrix.MatrixChatRepository
import com.srisu.srisu.features.chat.matrix.MatrixChatRoomOwner
import com.srisu.srisu.features.chat.matrix.MatrixChatRoomState
import com.srisu.srisu.features.chat.matrix.MatrixMessageDelivery
import com.srisu.srisu.features.chat.matrix.MATRIX_TEXT_MAX_PLAINTEXT_BYTES
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SecureChatViewModel(
    private val repository: MatrixChatRepository,
    private val partners: ChatPartnerSource? = null,
    private val storage: SessionStorage? = null,
    private val sessions: com.srisu.srisu.core.session.SessionCoordinator? = null,
    private val moments: MomentSource? = null,
) : ViewModel() {
    val momentReferences = MomentReferenceResolver(moments, sessions, viewModelScope)
    private val pendingMoment = MutableStateFlow<MomentReference?>(null)
    val momentReply = pendingMoment.asStateFlow()
    private var routeMoment: MomentReference? = null
    fun setMomentReply(reference: MomentReference?) {
        pendingMoment.value=reference
        if(reference!=null) { local.update { it.copy(reply=null) } }
        saveUi()
    }
    fun openMomentReply(reference: MomentReference) {
        if(routeMoment==reference) return
        routeMoment=reference; setMomentReply(reference)
    }
    private val _state = MutableStateFlow<MatrixChatRoomState?>(null)
    val state: StateFlow<MatrixChatRoomState?> = _state.asStateFlow()
    private val _partner = MutableStateFlow<ChatPartnerIdentity?>(null)
    /** Header identity; null while unknown, in which case the screen shows a neutral title. */
    val partner: StateFlow<ChatPartnerIdentity?> = _partner.asStateFlow()
    private val _messageInput = MutableStateFlow("")
    val messageInput: StateFlow<String> = _messageInput.asStateFlow()
    private val _composerValidationError = MutableStateFlow<String?>(null)
    val composerValidationError: StateFlow<String?> = _composerValidationError.asStateFlow()
    private var mediaNavigation = false
    fun coverWithMedia() { mediaNavigation = true }
    fun destinationVisible(visible: Boolean) { if(visible) mediaNavigation=false; momentReferences.active(visible) }
    fun leaveDestination() { if (!mediaNavigation) closeRoom() }
    fun returnFromMedia() { mediaNavigation = false; dismissMedia() }
    private var roomId: String? = null
    private var observer: Job? = null
    private var sendJob: Job? = null
    private var partnerJob: Job? = null
    private var roomOperation: Long = 0
    private val roomOwner = MatrixChatRoomOwner()


    private val thumbnails = linkedMapOf<String, String>()
    private val local = MutableStateFlow(ConversationState())
    internal val uiState: StateFlow<ConversationState> = combine(_state, _partner, _messageInput, local) { room, identity, draft, ui ->
        val roomState = room ?: MatrixChatRoomState(roomId.orEmpty())
        ui.copy(accountId = roomState.accountId, roomId = roomState.roomId, partnerName = identity?.name ?: "Your partner",
            openedMedia = ui.openedMedia.takeIf { roomState.availability is MatrixChatAvailability.Ready },
            draft = draft, loading = roomState.availability is MatrixChatAvailability.Loading,
            security = if (roomState.availability is MatrixChatAvailability.Ready) ConversationSecurity.READY else ConversationSecurity.SETTING_UP,
            messages = roomState.messages.map { it.toDisplay(roomState, thumbnails) }, hasOlder = roomState.hasOlder,
            uploadProgress = roomState.messages.mapNotNull { message -> message.uploadProgress?.let { message.id to it } }.toMap())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ConversationState())
    private val effects = MutableSharedFlow<RomanticEffect>(extraBufferCapacity = 1, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    internal val romanticEffects = effects.asSharedFlow()
    private var arrivals = RomanticArrivalTracker()
    private val thumbnailJobs = mutableMapOf<String, Job>()
    private var mediaJob: Job? = null
    private var prepared: PreparedChatMedia? = null
    private var savedScope: String? = null
    private var savedStamp: com.srisu.srisu.core.session.SessionStamp? = null
    private var mediaGeneration = 0
    private var editedDuringOpening = false

    init {
        val sessionSource = sessions
        if (sessionSource != null) {
            var account = sessionSource.stamp().accountId
            viewModelScope.launch {
                sessionSource.state.collect { stamp ->
                    if (account != stamp.accountId) {
                        // Do not wait for repository logout/network cleanup to clear private UI bytes.
                        closeRoom()
                    } else if (savedStamp != null) {
                        // Same-account token renewal must not disable local draft persistence.
                        savedStamp = stamp
                    }
                    account = stamp.accountId
                }
            }
        }
    }

    @Serializable private data class SavedUi(val draft: String = "", val reply: ChatQuote? = null, val theme: String = ChatThemePreset.DEFAULT.id, val moment: MomentReference? = null)
    private fun restoreUi(room: MatrixChatRoomState) {
        val key = room.accountId?.let { "chat-ui-v1:$it:${room.roomId}" } ?: return
        if (savedScope == key) return
        savedScope = key
        savedStamp = sessions?.stamp()
        val saved = runCatching { storage?.getSession(key)?.let { Json.decodeFromString<SavedUi>(it) } }.getOrNull() ?: SavedUi()
        if (!editedDuringOpening) _messageInput.value = saved.draft
        if(routeMoment==null) pendingMoment.value=saved.moment
        local.update { it.copy(reply = saved.reply.takeIf {pendingMoment.value==null}, preferences = it.preferences.copy(chatTheme = saved.theme)) }
        if (editedDuringOpening) saveUi()
    }
    private fun saveUi() {
        val key = savedScope ?: return
        if (sessions != null && sessions.stamp() != savedStamp) return
        runCatching { storage?.saveSession(Json.encodeToString(SavedUi(_messageInput.value, local.value.reply, local.value.preferences.chatTheme, pendingMoment.value)), key) }
            .onFailure { _composerValidationError.value = "Couldn’t save this draft on your device." }
    }
    fun chatTheme(id: String) { local.update { it.copy(preferences = it.preferences.copy(chatTheme = ChatThemePreset.fromId(id).id)) }; saveUi() }
    fun setReply(id: String?) {
        if(id!=null) pendingMoment.value=null
        local.update { ui -> ui.copy(reply = id?.let { target -> uiState.value.messages.firstOrNull { it.id == target }?.let { message ->
            ChatQuote(target, message.authorId, when(val content = message.content) { is ChatContent.Text -> content.body; is ChatContent.Media -> content.caption.ifBlank { content.items.first().kind }; else -> "Message" })
        } }) }; saveUi()
    }
    fun prepareAttachments(items: List<PreparedChatMedia>, sendWhenReady: Boolean = false) {
        if(pendingMoment.value!=null) return mediaError("Send your Moment reply as text, or cancel its context before attaching media.")
        val item = items.singleOrNull() ?: return mediaError("Choose one attachment per message.")
        val room = roomId ?: return
        prepared = item
        val id = Uuid.random().toString()
        val media = ChatMedia(AttachmentDescriptor(id, room, id), item.kind, item.mime, item.width, item.height, item.durationMs, item.thumbnail, item.waveform)
        local.update { it.copy(mediaDraft = MediaDraft(id, listOf(media)), error = null) }
        if (sendWhenReady) sendMessage()
    }
    fun removeDraftAttachment(id: String) { if (local.value.mediaDraft?.items?.any { it.descriptor.id == id } == true) { prepared = null; dismissMedia(); local.update { it.copy(mediaDraft = null) } } }
    fun mediaError(message: String) { local.update { it.copy(error = message) } }
    fun clearError() { local.update { it.copy(error = null) }; _composerValidationError.value = null }
    fun dismissMedia() { mediaGeneration++; mediaJob?.cancel(); mediaJob = null; local.update { it.copy(openedMedia = null, mediaRequest = null, mediaLoading = false, mediaRequestError = null) } }
    fun openDraftAttachment(id: String) {
        val draft = local.value.mediaDraft ?: return
        val media = draft.items.firstOrNull { it.descriptor.id == id } ?: return
        val bytes = prepared?.bytes ?: return
        dismissMedia(); local.update { it.copy(openedMedia = OpenedChatMedia(draft.messageId, media, bytes)) }
    }
    fun openAttachment(messageId: String, attachmentId: String) {
        val room = roomId ?: return
        val display = uiState.value.messages.firstOrNull { it.id == messageId } ?: return
        val media = (display.content as? ChatContent.Media)?.items?.firstOrNull { it.descriptor.id == attachmentId } ?: return
        dismissMedia(); val generation = mediaGeneration; val operation = roomOperation
        local.update { it.copy(mediaRequest = ChatMediaRequest(messageId, attachmentId, media.kind), mediaLoading = true) }
        mediaJob = viewModelScope.launch {
            val result = repository.attachment(room, messageId)
            if (operation != roomOperation || generation != mediaGeneration) return@launch
            local.update { it.copy(mediaLoading = false, openedMedia = result.getOrNull()?.let { bytes -> OpenedChatMedia(messageId, media, bytes) },
                mediaRequestError = if (result.isFailure) "Couldn’t open this attachment. Tap to retry." else null) }
        }
    }
    fun loadThumbnail(id: String) {
        if (id in thumbnails || id in thumbnailJobs || _state.value?.availability !is MatrixChatAvailability.Ready) return
        val room = roomId ?: return; val operation = roomOperation
        thumbnailJobs[id] = viewModelScope.launch {
            val result = repository.attachment(room, id, thumbnail = true)
            if (operation != roomOperation) return@launch
            result.getOrNull()?.let { bytes ->
                if (thumbnails.size >= 32) thumbnails.remove(thumbnails.keys.first())
                thumbnails[id] = Base64.encode(bytes)
                // Only the affected immutable display message changes; playback does not flow through this state.
                local.update { it.copy(offlineMedia = thumbnails.keys.toSet()) }
            }
        }
    }
    fun react(id: String, emoji: String?) = richAction { repository.react(it, id, emoji) }
    fun deleteMessage(id: String) = richAction { repository.deleteMessage(it, id) }
    private fun richAction(action: suspend (String) -> Result<Unit>) {
        val room = roomId ?: return; val operation = roomOperation
        viewModelScope.launch { val result = action(room); if (operation == roomOperation && result.isFailure) mediaError("That change wasn’t saved. Please try again.") }
    }
    fun loadOlder() { roomId?.let(repository::loadOlder) }
    fun markVisible(id: String) { val room = roomId ?: return; viewModelScope.launch { repository.markRead(room, id) } }

    fun openRoom(value: String) {
        if (roomId == value && observer?.isActive == true) return
        saveUi()
        dismissMedia()
        thumbnailJobs.values.forEach { it.cancel() }; thumbnailJobs.clear(); thumbnails.clear()
        prepared = null; savedScope = null; arrivals = RomanticArrivalTracker(); local.value = ConversationState()
        val operation = ++roomOperation
        sendJob?.cancel()
        sendJob = null
        roomId?.let { repository.closeRoom(it, roomOwner) }
        observer?.cancel()
        _messageInput.value = ""
        editedDuringOpening = false
        _composerValidationError.value = null
        _state.value = null
        partnerJob?.cancel()
        _partner.value = null
        roomId = value
        observer = viewModelScope.launch {
            repository.observeRoom(value).collect {
                if (roomId == value && roomOperation == operation) {
                    restoreUi(it)
                    val quote = local.value.reply
                    if (quote != null && it.messages.any { message -> message.id == quote.messageId && (message.deleted || message.unavailable) }) {
                        local.update { ui -> ui.copy(reply = quote.copy(preview = "Original message unavailable")) }
                        saveUi()
                    }
                    _state.value = it
                    arrivals.accept(it).lastOrNull()?.let(effects::tryEmit)
                }
            }
        }
        repository.openRoom(value, roomOwner)
        loadPartner(value, operation)
    }

    private fun loadPartner(room: String, operation: Long) {
        val source = partners ?: return
        partnerJob = viewModelScope.launch {
            val identity = try {
                source.partnerFor(room)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The header falls back to a neutral title; chat availability is unaffected.
                null
            }
            if (roomId == room && roomOperation == operation) _partner.value = identity
        }
    }

    fun updateMessageInput(value: String) {
        if (value.encodeToByteArray().size <= MATRIX_TEXT_MAX_PLAINTEXT_BYTES) {
            editedDuringOpening = true
            _messageInput.value = value
            _composerValidationError.value = null
            saveUi()
        } else {
            _composerValidationError.value = "Message is too long."
        }
    }

    fun sendMessage() {
        if (sendJob?.isActive == true) return
        val activeRoom = roomId ?: return
        val operation = roomOperation
        val body = _messageInput.value.trim()
        val media = prepared
        val reply = local.value.reply
        val moment = pendingMoment.value
        if (body.isEmpty() && media == null) return
        sendJob = viewModelScope.launch {
            val result = when {
                moment != null -> {
                    val result=moments?.get(moment.id)
                    val eligible=(result as? NetworkAPIResult.Success)?.response
                    if(roomId!=activeRoom || roomOperation!=operation) return@launch
                    if(eligible==null || eligible.couple!=moment.couple || (!eligible.canReply && !eligible.canEdit)) {
                        _composerValidationError.value="This Moment is no longer available. Cancel its context to send your text on its own."
                        return@launch
                    }
                    repository.sendMomentReply(activeRoom,body,moment)
                }
                media != null -> repository.sendMedia(activeRoom, media, body, reply?.messageId)
                reply != null -> repository.sendReply(activeRoom, body, reply.messageId)
                else -> repository.sendText(activeRoom, body)
            }
            if (roomId == activeRoom && roomOperation == operation) {
                if (result.isSuccess) {
                    if (_messageInput.value.trim() == body) _messageInput.value = ""
                    if (prepared === media) { prepared = null; local.update { it.copy(mediaDraft = null) } }
                    if (local.value.reply == reply) local.update { it.copy(reply = null) }
                    if(pendingMoment.value==moment) pendingMoment.value=null
                    saveUi()
                } else _composerValidationError.value = "Message was not sent. Your draft is still here."
            }
        }
    }

    fun retry() {
        roomId?.let { repository.retryRoom(it, roomOwner) }
    }

    fun retryMessage(transactionId: String) {
        val activeRoom = roomId ?: return
        val operation = roomOperation
        viewModelScope.launch {
            val result = repository.retryMessage(activeRoom, transactionId)
            if (roomId == activeRoom && roomOperation == operation) {
                _composerValidationError.value = result.fold(
                    onSuccess = { null },
                    onFailure = { "Message retry failed. Try again." },
                )
            }
        }
    }

    fun retryFailedMessages() {
        _state.value?.messages.orEmpty()
            .filter { it.delivery == MatrixMessageDelivery.FAILED }
            .mapNotNull { it.retryTransactionId }
            .forEach(::retryMessage)
    }

    fun retryFailedChanges() { _state.value?.failedChangeIds.orEmpty().forEach(::retryMessage) }

    fun closeRoom() {
        saveUi(); dismissMedia(); momentReferences.clear(); pendingMoment.value=null; routeMoment=null
        thumbnailJobs.values.forEach { it.cancel() }; thumbnailJobs.clear(); thumbnails.clear()
        prepared = null; savedScope = null; local.value = ConversationState()
        roomOperation += 1
        sendJob?.cancel()
        sendJob = null
        observer?.cancel()
        observer = null
        partnerJob?.cancel()
        partnerJob = null
        _partner.value = null
        roomId?.let { repository.closeRoom(it, roomOwner) }
        roomId = null
        _messageInput.value = ""
        _composerValidationError.value = null
        _state.value = null
    }

    override fun onCleared() {
        closeRoom()
        super.onCleared()
    }
}
