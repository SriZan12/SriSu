package com.srisu.srisu.features.chat.presentation.chat.vm

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.Uri
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.features.chat.data.remote.dto.ChatMessage
import com.srisu.srisu.features.chat.data.remote.dto.UploadState
import com.srisu.srisu.features.chat.data.remote.api.ChatRepository
import com.srisu.srisu.features.chat.presentation.chat.state.ChatState
import com.srisu.srisu.core.session.Session
import com.srisu.srisu.features.chat.data.remote.response.ChatRoomItemDto
import com.srisu.srisu.utils.Constants
import com.srisu.srisu.utils.MediaFile
import com.srisu.srisu.utils.getMediaFileFromUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi

class ChatViewModel(
    private val repository: ChatRepository,
) : ViewModel() {

    private val _chatState = MutableStateFlow(ChatState())
    val chatState: StateFlow<ChatState> = _chatState.asStateFlow()

    private var typingJob: Job? = null
    private val typingTimeoutMillis = 1200L
    private var isCurrentlyTyping = false
    private var requestedPartnerId: Long? = null

    init {
        observeMessages()
        observeChatRooms()
        observeErrors()
        connectSocket()
    }

    private fun connectSocket() {
        repository.connect()
    }

    override fun onCleared() {
        super.onCleared()
        typingJob?.cancel()
        repository.clearActiveChatRoom()
        repository.disconnect("ChatViewModel cleared")
    }

    // -------------------------------------------------
    // Observers
    // -------------------------------------------------

    private fun observeMessages() {
        viewModelScope.launch {
            repository.messages.collect { messages ->
                _chatState.update { state ->
                    state.copy(chatMessages = messages)
                }
            }
        }
    }

    private fun observeChatRooms() {
        viewModelScope.launch {

            repository.chatRoomsList.collect { chatRooms ->
                val currentState = chatState.value
                val partnerRequest = requestedPartnerId
                val selectedRoom = if (partnerRequest != null) {
                    chatRooms.firstOrNull { room -> room.belongsToPartner(partnerRequest) }
                } else {
                    currentState.chatRoomData?.id?.let { selectedRoomId ->
                        chatRooms.firstOrNull { room -> room.id == selectedRoomId }
                    } ?: chatRooms.firstOrNull()
                }
                val myUserId = chatState.value.session?.id
                val selectedRoomChanged = selectedRoom?.id != currentState.chatRoomData?.id

                if (selectedRoom?.belongsToPartner(requestedPartnerId) == true) {
                    requestedPartnerId = null
                }

                _chatState.update { state ->
                    state.copy(
                        chatRoomList = chatRooms,
                        chatRoomData = selectedRoom,
                        isRoomDataSet = if (selectedRoomChanged) false else state.isRoomDataSet,
                        isTyping = isSomeoneElseTyping(
                            room = selectedRoom ?: state.chatRoomData,
                            myUserId = myUserId,
                        )
                    )
                }

                if (!_chatState.value.isRoomDataSet) {
                    setChatRoomData()
                }
            }


        }


    }

    private fun observeErrors() {
        viewModelScope.launch {
            repository.error.collect { errorMessage ->
                if (!errorMessage.isNullOrBlank()) {
                    showErrorMessage(
                        errorType = "Error",
                        message = errorMessage,
                    )
                }
            }
        }
    }

    // -------------------------------------------------
    // Session / room state
    // -------------------------------------------------

    fun updateSession(session: Session?) {
        _chatState.update { state ->
            state.copy(session = session)
        }
    }

    fun setChatRoomData() {
        AppLogger.log("ChatRoomID = ${_chatState.value.chatRoomData}")

        val chatRoomData = _chatState.value.chatRoomData ?: return

        viewModelScope.launch(Dispatchers.Default) {
            try {

                withContext(Dispatchers.Main) {
                    _chatState.update { state ->
                        state.copy(
                            chatRoomData = chatRoomData,
                            selectedMessageForAction = null,
                            selectedMessageIdForActions = null,
                            isEditMessage = false,
                            replyMessage = ChatState.ReplyMessage(),
                            messageInput = TextFieldValue(),
                            showImageScreen = ChatState.ShowImageScreen(),
                        )
                    }
                }

                chatRoomData.id?.let { roomId ->
                    repository.fetchInitialMessages(chatRoomId = roomId)
                    repository.markDelivered(chatRoomId = roomId)
                    repository.markRead(chatRoomId = roomId)
                }

                _chatState.update {
                    it.copy(isRoomDataSet = true)
                }

            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    showErrorMessage(
                        errorType = "Error",
                        message = "Invalid chat room data",
                    )
                }
            }
        }
    }

    /**
     * Call this when leaving ChatScreen.
     */
    fun clearActiveChatRoom() {
        stopTyping()
        repository.clearActiveChatRoom()

        _chatState.update { state ->
            state.copy(
                chatRoomData = null,
                chatMessages = emptyList(),
                selectedMessageForAction = null,
                selectedMessageIdForActions = null,
                isEditMessage = false,
                replyMessage = ChatState.ReplyMessage(),
                messageInput = TextFieldValue(),
                showImageScreen = ChatState.ShowImageScreen(),
                isTyping = false,
            )
        }
    }

    suspend fun fetchInitialChatRooms() {
        repository.fetchInitialChatRooms()
    }

    /**
     * Selects the direct chat for a known partner. If rooms have not arrived yet,
     * the request is retained and resolved after the room list is refreshed.
     */
    fun openPartnerChat(partnerId: Long) {
        requestedPartnerId = partnerId

        val partnerRoom = _chatState.value.chatRoomList
            .firstOrNull { room -> room.belongsToPartner(partnerId) }

        if (partnerRoom != null) {
            requestedPartnerId = null
            val roomChanged = partnerRoom.id != _chatState.value.chatRoomData?.id
            _chatState.update { state ->
                state.copy(
                    chatRoomData = partnerRoom,
                    isRoomDataSet = if (roomChanged) false else state.isRoomDataSet,
                )
            }
            if (!_chatState.value.isRoomDataSet) {
                setChatRoomData()
            }
            return
        }

        viewModelScope.launch {
            repository.fetchInitialChatRooms()
        }
    }

    fun fetchOlderChatRooms() {
        repository.fetchOlderChatRooms()
    }

    fun fetchOlderMessages() {
        val roomId = chatState.value.chatRoomData?.id ?: return
        repository.fetchOlderMessages(roomId)
    }

    // -------------------------------------------------
    // Message input / typing
    // -------------------------------------------------

    fun onMessageInputChanged(value: TextFieldValue) {
        _chatState.update { state ->
            state.copy(messageInput = value)
        }

        if (value.text.isBlank()) {
            stopTyping()
            return
        }

        startTyping()
        scheduleStopTyping()
    }

    fun setMessageInputText(text: String) {
        _chatState.update { state ->
            state.copy(
                messageInput = TextFieldValue(
                    text = text,
                    selection = TextRange(text.length),
                )
            )
        }
    }

    private fun startTyping() {
        if (isCurrentlyTyping) return

        isCurrentlyTyping = true
        sendTypingRequest(true)
    }

    private fun scheduleStopTyping() {
        typingJob?.cancel()
        typingJob = viewModelScope.launch {
            delay(typingTimeoutMillis.milliseconds)
            stopTyping()
        }
    }

    private fun stopTyping() {
        if (isCurrentlyTyping) {
            isCurrentlyTyping = false
            sendTypingRequest(false)
        }
        typingJob?.cancel()
        typingJob = null
    }

    private fun sendTypingRequest(isTyping: Boolean) {
        val roomId = chatState.value.chatRoomData?.id ?: return

        viewModelScope.launch {
            try {
                repository.setTyping(
                    chatRoomId = roomId,
                    isTyping = isTyping,
                )
            } catch (_: Exception) {
                showErrorMessage(
                    errorType = "Error",
                    message = "Failed to update typing state",
                )
            }
        }
    }

    private fun isSomeoneElseTyping(
        room: ChatRoomItemDto?,
        myUserId: Long?,
    ): Boolean {
        val typingUsers = room?.isTyping.orEmpty()
        return typingUsers.any { (userId, isTyping) ->
            userId != myUserId?.toString() && isTyping
        }
    }

    // -------------------------------------------------
    // Reply / edit / message action sheet
    // -------------------------------------------------

    fun setReplyMessage(chatMessage: ChatMessage?, isOn: Boolean) {
        _chatState.update { state ->
            state.copy(
                replyMessage = ChatState.ReplyMessage(
                    isOn = isOn,
                    message = chatMessage,
                )
            )
        }
    }

    fun updateIsEditMessage(isEditMessage: Boolean) {
        _chatState.update { state ->
            state.copy(isEditMessage = isEditMessage)
        }
    }

    fun showActionsForMessage(messageId: Long?, message: ChatMessage) {
        _chatState.update { state ->
            state.copy(
                selectedMessageIdForActions = messageId,
                selectedMessageForAction = message,
            )
        }
    }

    fun updateLongClickedMessage(chatMessage: ChatMessage) {
        _chatState.update { state ->
            state.copy(selectedMessageForAction = chatMessage)
        }
    }

    fun dismissActions() {
        _chatState.update { state ->
            state.copy(selectedMessageIdForActions = null)
        }
    }

    // -------------------------------------------------
    // Send / edit / delete / react
    // -------------------------------------------------

    fun sendTextMessage() {
        val roomId = chatState.value.chatRoomData?.id ?: return
        val text = chatState.value.messageInput.text.trim()

        if (text.isBlank()) return

        viewModelScope.launch {
            try {
                repository.sendMessage(
                    chatRoomId = roomId,
                    text = text,
                    messageType = Constants.ChatConstants.TEXT,
                    replyToId = chatState.value.replyMessage.message?.id,
                )

                onMessageInputChanged(TextFieldValue())
                setReplyMessage(null, false)
            } catch (_: Exception) {
                showErrorMessage(
                    errorType = "Error",
                    message = "Something went wrong",
                )
            }
        }
    }

    fun editMessage(message: ChatMessage?) {
        val messageId = message?.id ?: return
        val text = chatState.value.messageInput.text.trim()

        if (text.isBlank()) return

        viewModelScope.launch {
            try {
                repository.editMessage(
                    messageId = messageId,
                    text = text,
                )

                onMessageInputChanged(TextFieldValue())
                updateIsEditMessage(false)
                dismissActions()
            } catch (_: Exception) {
                showErrorMessage(
                    errorType = "Error",
                    message = "Something went wrong",
                )
            }
        }
    }

    fun deleteMessage(
        deleteOption: String,
        messageId: Long?,
    ) {
        val safeMessageId = messageId ?: return

        viewModelScope.launch {
            try {
                repository.deleteMessage(
                    messageId = safeMessageId,
                    deleteOption = deleteOption,
                )
                dismissActions()
            } catch (_: Exception) {
                showErrorMessage(
                    errorType = "Error",
                    message = "Something went wrong",
                )
            }
        }
    }

    fun reactToMessage(messageId: Long?, reaction: String) {
        val safeMessageId = messageId ?: return

        viewModelScope.launch {
            try {
                repository.reactToMessage(
                    messageId = safeMessageId,
                    reaction = reaction,
                )
            } catch (_: Exception) {
                showErrorMessage(
                    errorType = "Error",
                    message = "Something went wrong",
                )
            }
        }
    }

    fun sendMessageReadRequest() {
        val roomId = chatState.value.chatRoomData?.id ?: return

        viewModelScope.launch {
            try {
                repository.markRead(roomId)
            } catch (_: Exception) {
                showErrorMessage(
                    errorType = "Error",
                    message = "Something went wrong",
                )
            }
        }
    }

    fun sendMessageDeliveredRequest() {
        val roomId = chatState.value.chatRoomData?.id ?: return

        viewModelScope.launch {
            try {
                repository.markDelivered(roomId)
            } catch (_: Exception) {
                showErrorMessage(
                    errorType = "Error",
                    message = "Something went wrong",
                )
            }
        }
    }

    // -------------------------------------------------
    // Media upload / optimistic local placeholder
    // -------------------------------------------------

    fun updateIsUploadingPhoto(isUploading: Boolean) {
        _chatState.update { state ->
            state.copy(isUploadingPhoto = isUploading)
        }
    }

    @OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)
    fun updateSelectedPhotos(photos: List<Uri?>?) {
        _chatState.update { state ->
            state.copy(selectedPhotos = photos.orEmpty())
        }

        val selectedPhotos = _chatState.value.selectedPhotos
        val roomId = _chatState.value.chatRoomData?.id

        if (selectedPhotos.isNullOrEmpty() || roomId == null) return

        val localMediaMessage = ChatMessage(
            id = Clock.System.now().epochSeconds,
            chatRoomId = roomId,
            senderId = chatState.value.session?.id,
            receiverId = chatState.value.chatRoomData?.otherUser?.id,
            messageType = Constants.ChatConstants.IMAGE,
            text = "",
            uploadingPhotos = selectedPhotos.map {
                ChatMessage.UploadingPhoto(
                    localUri = it.toString(),
                    progress = 0f,
                    state = UploadState.UPLOADING,
                )
            },
            isLocalOnly = true,
            timestamp = Clock.System.now().toString(),
        )

        repository.addLocalMessage(localMediaMessage)
    }

    fun uploadMedias() {
        viewModelScope.launch {
            try {
                updateIsUploadingPhoto(true)

                val mediaFiles = getMediaFiles()

                repository.uploadMedias(mediaFiles)
                    .onSuccess { mediaUploadResponse, _ ->
                        val uploadedMedias = mediaUploadResponse
                            ?.media
                            .orEmpty()
                            .map { media ->
                                ChatMessage.Media(
                                    id = media?.id,
                                    mediaUrl = media?.file ?: media?.file,
                                    uploadedAt = media?.uploadedAt,
                                )
                            }

                        sendMediaMessage(uploadedMedias)
                    }
                    .onError { error, errorType ->
                        updateIsUploadingPhoto(false)
                        showErrorMessage(
                            errorType = errorType.name,
                            message = error,
                        )
                    }
            } catch (_: Exception) {
                updateIsUploadingPhoto(false)
                showErrorMessage(
                    errorType = "Error",
                    message = "Something went wrong",
                )
            }
        }
    }

    private fun sendMediaMessage(
        medias: List<ChatMessage.Media>,
    ) {
        val roomId = chatState.value.chatRoomData?.id ?: return

        viewModelScope.launch {
            try {
                repository.sendMessage(
                    chatRoomId = roomId,
                    text = "",
                    messageType = Constants.ChatConstants.IMAGE,
                    mediaIds = medias.mapNotNull { it.id },
                    replyToId = chatState.value.replyMessage.message?.id,
                )

                onMessageInputChanged(TextFieldValue())
                setReplyMessage(null, false)
                updateIsUploadingPhoto(false)
            } catch (_: Exception) {
                updateIsUploadingPhoto(false)
                showErrorMessage(
                    errorType = "Error",
                    message = "Something went wrong",
                )
            }
        }
    }

    private suspend fun getMediaFiles(): ArrayList<MediaFile?> {
        val mediaFiles = arrayListOf<MediaFile?>()
        _chatState.value.selectedPhotos?.forEach { uri ->
            mediaFiles.add(
                getMediaFileFromUri(
                    uri = uri,
                    id = null,
                    removed = null,
                )
            )
        }
        return mediaFiles
    }

    // -------------------------------------------------
    // Image preview
    // -------------------------------------------------

    fun updateShowImageScreen(
        show: Boolean,
        startingIndex: Int,
        images: List<String?>,
    ) {
        _chatState.update { state ->
            state.copy(
                showImageScreen = ChatState.ShowImageScreen(
                    show = show,
                    images = images,
                    startingIndex = startingIndex,
                )
            )
        }
    }

    // -------------------------------------------------
    // UI state helpers
    // -------------------------------------------------

    private fun <T> showSuccessMessage(
        data: T? = null,
        message: String,
    ) {
        _chatState.update { state ->
            state.copy(
                baseUIState = BaseUIState.Success(
                    data = data,
                    message = message,
                )
            )
        }
    }

    private fun showErrorMessage(
        errorType: String?,
        message: String?,
    ) {
        _chatState.update { state ->
            state.copy(
                baseUIState = BaseUIState.Error(
                    errorType = errorType,
                    message = message,
                )
            )
        }
    }

    private fun showLoading() {
        _chatState.update { state ->
            state.copy(baseUIState = BaseUIState.Loading)
        }
    }

    fun idleScreen() {
        _chatState.update { state ->
            state.copy(baseUIState = BaseUIState.Idle)
        }
    }
}

private fun ChatRoomItemDto.belongsToPartner(partnerId: Long?): Boolean {
    if (partnerId == null) return false
    return otherUser?.id == partnerId || userOneId == partnerId || userTwoId == partnerId
}
