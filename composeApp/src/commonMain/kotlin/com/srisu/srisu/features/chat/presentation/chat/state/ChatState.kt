package com.srisu.srisu.features.chat.presentation.chat.state

import androidx.compose.runtime.Stable
import androidx.compose.ui.text.input.TextFieldValue
import coil3.Uri
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.features.chat.data.remote.dto.ChatMessage
import com.srisu.srisu.features.chat.data.remote.response.ChatRoomResponse
import com.srisu.srisu.features.chat.data.remote.response.TypingResponse
import com.srisu.srisu.core.session.Session
import com.srisu.srisu.features.chat.data.remote.response.ChatRoomItemDto

@Stable
data class ChatState(
    val session: Session? = null,
    val chatRoomData: ChatRoomItemDto? = null,
    val messageInput: TextFieldValue = TextFieldValue(),
    val chatMessages: List<ChatMessage> = emptyList(),
    val selectedMessageForAction: ChatMessage? = null,
    val selectedMessageIdForActions: Long? = null,
    val isEditMessage: Boolean = false,
    val isTyping: Boolean = false,
    val reactionEmojiList: List<Reactions> = emptyList(),
    val selectedEmoji: Reactions? = null,
    val replyMessage: ReplyMessage = ReplyMessage(),
    val selectedPhotos: List<Uri?>? = emptyList(),
    val isUploadingPhoto: Boolean = false,
    val showImageScreen: ShowImageScreen = ShowImageScreen(),
    val chatRoomList: List<ChatRoomItemDto> = emptyList(),
    val chatRoomLastUpdatedAt: String? = null,
    val baseUIState: BaseUIState = BaseUIState.Idle,
) {

    data class Reactions(
        val reactionEmoji: String,
        val contentDescription: String,
    )

    data class ReplyMessage(
        val isOn: Boolean = false,
        val message: ChatMessage? = null,
    )

    data class ShowImageScreen(
        val show: Boolean = false,
        val images: List<String?> = emptyList(),
        val startingIndex: Int = 0,
    )
}