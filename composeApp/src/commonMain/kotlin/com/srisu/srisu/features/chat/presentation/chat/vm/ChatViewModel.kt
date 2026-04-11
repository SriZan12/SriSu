package com.srisu.srisu.features.chat.presentation.chat.vm

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.Uri
import com.srisu.srisu.baseframework.BaseUIState
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
import kotlinx.serialization.json.Json
import kotlin.time.Clock
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
                val selectedRoomId = chatState.value.chatRoomData?.id
                val selectedRoom = chatRooms.firstOrNull { it.id == selectedRoomId }
                val myUserId = chatState.value.session?.id

                _chatState.update { state ->
                    state.copy(
                        chatRoomList = chatRooms,
                        chatRoomData = selectedRoom ?: state.chatRoomData,
                        isTyping = isSomeoneElseTyping(
                            room = selectedRoom,
                            myUserId = myUserId,
                        )
                    )
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

    fun setChatRoomData(chatRoomData: String?) {
        if (chatRoomData.isNullOrBlank()) return

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val room = Json.decodeFromString<ChatRoomItemDto>(chatRoomData)

                withContext(Dispatchers.Main) {
                    _chatState.update { state ->
                        state.copy(chatRoomData = room)
                    }
                }

                room.id?.let { roomId ->
                    repository.fetchInitialMessages(roomId)
                    repository.markDelivered(roomId)
                    repository.markRead(roomId)
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

    suspend fun fetchInitialChatRooms() {
        repository.fetchInitialChatRooms()
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
            delay(typingTimeoutMillis)
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
    // Reply / edit / actions
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
    // Media upload / optimistic placeholder
    // -------------------------------------------------

    fun updateIsUploadingPhoto(isUploading: Boolean) {
        _chatState.update { state ->
            state.copy(isUploadingPhoto = isUploading)
        }
    }

    @OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)
    fun updateSelectedPhotos(photos: List<Uri?>?) {
        _chatState.update { state ->
            state.copy(selectedPhotos = photos)
        }

        val selectedPhotos = _chatState.value.selectedPhotos.orEmpty()
        val roomId = _chatState.value.chatRoomData?.id

        if (selectedPhotos.isEmpty() || roomId == null) return

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
        _chatState.value.selectedPhotos.orEmpty().forEach { uri ->
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