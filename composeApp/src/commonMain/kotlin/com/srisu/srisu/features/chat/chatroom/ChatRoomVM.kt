package com.srisu.srisu.features.chat.chatroom

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.Uri
import com.srisu.srisu.core.data.dto.chatdto.ChatMessage
import com.srisu.srisu.core.data.dto.chatdto.ReactToMessageDTO
import com.srisu.srisu.core.data.dto.chatdto.TypingRequest
import com.srisu.srisu.core.data.dto.chatdto.UploadState
import com.srisu.srisu.core.data.repository.chat.ChatRepository
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.session.Session
import com.srisu.srisu.utils.Constants.ChatConstants.DELETE_MESSAGE
import com.srisu.srisu.utils.Constants.ChatConstants.IMAGE
import com.srisu.srisu.utils.Constants.ChatConstants.MESSAGE_READ
import com.srisu.srisu.utils.Constants.ChatConstants.SEND_MESSAGE
import com.srisu.srisu.utils.Constants.ChatConstants.TEXT
import com.srisu.srisu.utils.Constants.ChatConstants.TYPING
import com.srisu.srisu.utils.MediaFile
import com.srisu.srisu.utils.getMediaFileFromUri
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class ChatViewModel(
    private val repository: ChatRepository
) : ViewModel() {

    private val _chatState = MutableStateFlow(ChatState())
    val chatState: StateFlow<ChatState> = _chatState.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private var typingJob: Job? = null
    private val TYPING_TIMEOUT = 1200L // ms
    private var isCurrentlyTyping = false


    init {
        observeChatRoom()
        updateChatMessages()
    }


    // -----------------------------
    // User Actions
    // -----------------------------

    fun onMessageInputChanged(value: TextFieldValue) {
        _chatState.update { it.copy(messageInput = value) }

        if (value.text.isBlank()) {
            stopTyping()
            return
        }

        startTyping()
        scheduleStopTyping()
    }

    private fun startTyping() {
        if (!isCurrentlyTyping) {
            isCurrentlyTyping = true
            sendTypingRequest(isTyping = true)
        }
    }

    private fun scheduleStopTyping() {
        typingJob?.cancel()
        typingJob = viewModelScope.launch {
            delay(TYPING_TIMEOUT)
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

    fun sendTypingRequest(isTyping: Boolean) {
        val userId = chatState.value.session?.id ?: return

        val payload = TypingRequest(
            action = TYPING,
            isTyping = isTyping,
            userId = userId
        )

        val encodedPayload = Json.encodeToString(payload)

        viewModelScope.launch {
            repository.sendRequest(payload = encodedPayload)
        }
    }


    fun setMessageInputText(text: String) {
        _chatState.update {
            it.copy(
                messageInput = TextFieldValue(
                    text = text,
                    selection = TextRange(text.length)
                )
            )
        }
    }


    fun updateChatMessages() {
        viewModelScope.launch {
            repository.messages.collect { messages ->
                _chatState.update {
                    it.copy(chatMessages = messages)
                }
            }
        }
    }

    fun setReplyMessage(chatMessage: ChatMessage?, isOn: Boolean) {
        _chatState.update {
            it.copy(
                replyMessage = ChatState.ReplyMessage(
                    isOn = isOn,
                    message = chatMessage
                )
            )
        }
    }

    fun updateIsUploadingPhoto(isUploading: Boolean) {
        _chatState.update {
            it.copy(isUploadingPhoto = isUploading)
        }
    }

    @OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)
    fun updateSelectedPhotos(photos: List<Uri?>?) {
        _chatState.update {
            it.copy(selectedPhotos = photos)
        }

        val listOfMedia = _chatState.value.selectedPhotos


        val mediaMessage = ChatMessage(
            id = Clock.System.now().epochSeconds,
            action = SEND_MESSAGE,
            text = "",
            senderId = chatState.value.session?.id,
            receiverId = 95,
            couple = 2,
            reactions = null,
            deleteFor = null,
            messageDeletionDict = null,
            chatRoom = "7fe512b9-548b-4a21-93cd-0a25d1aed5b4",
            messageType = IMAGE,
            uploadingPhotos = listOfMedia?.map {
                ChatMessage.UploadingPhoto(
                    localUri = it.toString(),
                    progress = 0f,
                    state = UploadState.UPLOADING
                )
            },
            isLocalOnly = true,
            timestamp = Clock.System.now().toString()
        )

        repository.prependMessage(message = mediaMessage)
    }

    fun updateShowImageScreen(show: Boolean, startingIndex: Int, images: List<ChatMessage.Media?>) {

        val imageList = images.map { it?.mediaUrl }

        _chatState.update {
            it.copy(
                showImageScreen = ChatState.ShowImageScreen(
                    show = show,
                    images = imageList,
                    startingIndex = startingIndex
                )
            )

        }
    }


    private fun observeChatRoom() {
        viewModelScope.launch {

            repository.chatRoom.collect { room ->
                AppLogger.log("Collecting chatRoom: $room")

                val myUserId = chatState.value.session?.id ?: return@collect

                val typingUsers = room
                    ?.isTyping
                    ?.typingData
                    ?.typingUsers
                    .orEmpty()

                AppLogger.log("Typing users = $typingUsers")

                val isSomeoneElseTyping = typingUsers.any { (userId, isTyping) ->
                    userId != myUserId.toString() && isTyping
                }

                AppLogger.log("Is someone else typing = $isSomeoneElseTyping")

                _chatState.update { state ->
                    state.copy(
                        typingResponse = room?.isTyping,
                        isTyping = isSomeoneElseTyping
                    )
                }
            }


        }
    }


    fun updateLongClickedMessage(chatMessage: ChatMessage) {
        _chatState.update {
            it.copy(selectedMessageForAction = chatMessage)
        }
    }

    fun updateIsEditMessage(isEditMessage: Boolean) {
        _chatState.update {
            it.copy(isEditMessage = isEditMessage)
        }
    }

    fun showActionsForMessage(messageId: Long?, message: ChatMessage) {
        _chatState.update {
            it.copy(
                selectedMessageIdForActions = messageId,
                selectedMessageForAction = message
            )
        }
    }


    fun dismissActions() {
        _chatState.update {
            it.copy(selectedMessageIdForActions = null)
        }
    }

    fun updateSession(session: Session?) {
        _chatState.update {
            it.copy(session = session)
        }
    }


    /**
     * Send message and clear input
     */
    @OptIn(ExperimentalTime::class)
    fun sendTextMessage() {
        val text = chatState.value.messageInput.text.trim()
        if (text.isBlank()) return

        viewModelScope.launch {
            try {

                val replyTo = if (chatState.value.replyMessage.isOn) {
                    ChatMessage.ReplyMessage(
                        id = chatState.value.replyMessage.message?.id,
                        text = chatState.value.replyMessage.message?.text,
                        senderId = chatState.value.replyMessage.message?.senderId
                    )
                } else {
                    null
                }

                val sendMessagePayload = ChatMessage(
                    action = SEND_MESSAGE,
                    text = text,
                    senderId = chatState.value.session?.id,
                    receiverId = 97,
                    couple = 2,
                    reactions = null,
                    deleteFor = null,
                    messageDeletionDict = null,
                    chatRoom = "7fe512b9-548b-4a21-93cd-0a25d1aed5b4",
                    messageType = TEXT,
                    replyTo = replyTo
                )

                repository.sendRequest(
                    payload = Json.encodeToString(value = sendMessagePayload)
                )
                onMessageInputChanged(TextFieldValue())
                setReplyMessage(chatMessage = null, isOn = false)
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Failed to send message: ${e.message}"
            }
        }
    }

    fun sendMediaMessage(
        listOfMedia: List<ChatMessage.Media?>?
    ) {

        viewModelScope.launch {
            try {

                val replyTo = if (chatState.value.replyMessage.isOn) {
                    ChatMessage.ReplyMessage(
                        id = chatState.value.replyMessage.message?.id,
                        text = chatState.value.replyMessage.message?.text,
                        senderId = chatState.value.replyMessage.message?.senderId
                    )
                } else {
                    null
                }

                val sendMessagePayload = ChatMessage(
                    action = SEND_MESSAGE,
                    text = "",
                    senderId = chatState.value.session?.id,
                    receiverId = 97,
                    couple = 2,
                    reactions = null,
                    deleteFor = null,
                    messageDeletionDict = null,
                    chatRoom = "7fe512b9-548b-4a21-93cd-0a25d1aed5b4",
                    messageType = IMAGE,
                    medias = listOfMedia,
                    replyTo = replyTo
                )

                repository.sendRequest(
                    payload = Json.encodeToString(value = sendMessagePayload)
                )

                onMessageInputChanged(TextFieldValue())
                setReplyMessage(chatMessage = null, isOn = false)
                updateIsUploadingPhoto(isUploading = false)
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Failed to send message: ${e.message}"
            }
        }
    }

    fun editMessage(
        messageId: Long?
    ) {
        val text = chatState.value.messageInput.text.trim()
        if (text.isBlank()) return

        viewModelScope.launch {
            try {
                val editMessagePayload = ChatMessage(
                    action = "edit_message",
                    id = messageId,
                    text = text,
                    senderId = chatState.value.session?.id,
                    receiverId = 97,
                    couple = 2,
                    chatRoom = "7fe512b9-548b-4a21-93cd-0a25d1aed5b4",
                    messageType = "text"
                )
                repository.sendRequest(
                    payload = Json.encodeToString(editMessagePayload)
                )
                onMessageInputChanged(TextFieldValue())
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Failed to edit message: ${e.message}"
            }

            updateIsEditMessage(isEditMessage = false)
        }
    }

    fun deleteMessage(
        deleteOption: String,
        messageId: Long?
    ) {

        viewModelScope.launch {
            try {
                val deleteMessagePayload = ChatMessage(
                    action = DELETE_MESSAGE,
                    id = messageId,
                    user_id = chatState.value.session?.id,
                    deleteOption = deleteOption
                )

                repository.sendRequest(
                    payload = Json.encodeToString(deleteMessagePayload)
                )
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Failed to delete message: ${e.message}"
            }

        }
    }

    fun sendMessageReadRequest() {

        viewModelScope.launch {
            try {
                val readMessagePayload = ChatMessage(
                    action = MESSAGE_READ,
                    user_id = chatState.value.session?.id,
                    chatRoom = "7fe512b9-548b-4a21-93cd-0a25d1aed5b4",
                )
                repository.sendRequest(
                    payload = Json.encodeToString(readMessagePayload)
                )
            } catch (exception: Exception) {
                _error.value = "Failed to send read request: ${exception.message}"
            }
        }

    }

    fun sendMessageDeliveredRequest() {
        viewModelScope.launch {
            try {
                val deliveredMessagePayload = ChatMessage(
                    action = "message_delivered",
                    user_id = chatState.value.session?.id,
                    chatRoom = "7fe512b9-548b-4a21-93cd-0a25d1aed5b4"
                )
                repository.sendRequest(
                    payload = Json.encodeToString(deliveredMessagePayload)
                )

            } catch (exception: Exception) {
                _error.value = "Failed to send delivered request: ${exception.message}"
            }

        }
    }

    fun reactToMessage(messageId: Long?, reaction: String) {
        viewModelScope.launch {
            try {
                val reactToMessagePayload = ReactToMessageDTO(
                    action = "react_to_message",
                    messageId = messageId,
                    userId = chatState.value.session?.id?.toLong(),
                    reaction = reaction
                )

                repository.sendRequest(
                    payload = Json.encodeToString(reactToMessagePayload)

                )
            } catch (exception: Exception) {
                _error.value = "Failed to react to message: ${exception.message}"
            }
        }
    }

    fun fetchOlderMessages() {
        repository.fetchOlderMessages()
    }

    fun uploadMedias() {
        viewModelScope.launch {
            try {
                updateIsUploadingPhoto(isUploading = true)
                val mediaFile = getMediaFile()
                repository.uploadMedias(medias = mediaFile)
                    .onSuccess { mediaUploadResponse, message ->
                        AppLogger.log("Media upload response: $message")
                        val medias = arrayListOf<ChatMessage.Media>()
                        mediaUploadResponse?.media?.forEach { media ->
                            medias.add(
                                ChatMessage.Media(
                                    id = media?.id,
                                    mediaUrl = media?.file,
                                    uploadedAt = media?.uploadedAt
                                )
                            )
                        }
                        sendMediaMessage(
                            listOfMedia = medias
                        )
                    }.onError { error, errorType ->
                        _error.value = error
                        updateIsUploadingPhoto(isUploading = false)
                        AppLogger.log("ON ERROR UPLOADING PHOTOS = $error")
                    }
                _error.value = null
            } catch (exception: Exception) {
                updateIsUploadingPhoto(isUploading = false)
                _error.value = "Failed to upload media: ${exception.message}"
            }
        }
    }

    private suspend fun getMediaFile(): ArrayList<MediaFile?> {
        val mediaFile = arrayListOf<MediaFile?>()
        _chatState.value.selectedPhotos?.forEach {
            AppLogger.log("While building media file, URI = ${it}")
            mediaFile.add(getMediaFileFromUri(uri = it, id = null, removed = null))
        }

        return mediaFile
    }


    /**
     * Retry connection
     */
    fun retryConnection() {
        viewModelScope.launch {
//            repository.reconnect()
            _error.value = null
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _error.value = null
    }

    // -----------------------------
    // Lifecycle
    // -----------------------------

    override fun onCleared() {
        super.onCleared()
//        repository.stop()
    }
}
