package com.srisu.srisu.features.chat.presentation.chat.state

import androidx.compose.runtime.Stable
import androidx.compose.ui.text.input.TextFieldValue
import coil3.Uri
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.features.chat.data.remote.dto.ChatMessage
import com.srisu.srisu.features.chat.data.remote.response.ChatRoomResponse
import com.srisu.srisu.features.chat.data.remote.response.TypingResponse
import com.srisu.srisu.session.Session

@Stable
data class ChatState(
    val session: Session? = null,
    val chatRoomData: ChatRoomResponse.Data.ChatRoom? = null,
    val messageInput: TextFieldValue = TextFieldValue(),
    val chatMessages: List<ChatMessage?>? = null,
    val selectedMessageForAction: ChatMessage? = null,
    val selectedMessageIdForActions: Long? = null,
    val isEditMessage: Boolean = false,
    val typingResponse: TypingResponse? = null,
    val isTyping: Boolean = false,
    val reactionEmojiList: List<Reactions> = emptyList(),
    val selectedEmoji: Reactions? = null,
    val replyMessage: ReplyMessage = ReplyMessage(),
    val selectedPhotos: List<Uri?>? = null,
    val isUploadingPhoto: Boolean = false,
    val showImageScreen: ShowImageScreen = ShowImageScreen(),
    val chatRoomList: List<ChatRoomResponse.Data.ChatRoom?> = emptyList(),
    val chatRoomLastUpdatedAt: String?? = null,
    val baseUIState: BaseUIState = BaseUIState.Idle

) {
    data class Reactions(
        val reactionEmoji: String,
        val contentDescription: String
    )

    data class ReplyMessage(
        val isOn: Boolean = false,
        val message: ChatMessage? = null
    )

    data class ShowImageScreen(
        val show: Boolean = false,
        val images: List<String?>? = null,
        val startingIndex: Int = 0
    )
}