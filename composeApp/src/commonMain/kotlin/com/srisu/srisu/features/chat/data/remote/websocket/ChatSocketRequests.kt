package com.srisu.srisu.features.chat.data.remote.websocket

import com.srisu.srisu.features.chat.data.remote.dto.ChatRoomDTO
import com.srisu.srisu.features.chat.data.remote.dto.ChatSocketDto
import com.srisu.srisu.features.chat.data.remote.dto.DeleteMessageDto
import com.srisu.srisu.features.chat.data.remote.dto.EditMessageDto
import com.srisu.srisu.features.chat.data.remote.dto.FetchMessagesDto
import com.srisu.srisu.features.chat.data.remote.dto.MarkDeliveredDto
import com.srisu.srisu.features.chat.data.remote.dto.MarkReadDto
import com.srisu.srisu.features.chat.data.remote.dto.ReactToMessageDto
import com.srisu.srisu.features.chat.data.remote.dto.SendMessageDto
import com.srisu.srisu.features.chat.data.remote.dto.SetTypingDto
import kotlinx.serialization.json.Json

object ChatSocketRequests {

    fun getChatRooms(
        limit: Int = 10,
        lastUpdated: String? = null,
        requestId: String? = null,
    ): String {
        return Json.encodeToString(
            ChatSocketDto(
                action = ChatSocketActions.GET_CHAT_ROOMS,
                requestId = requestId,
                payload = ChatRoomDTO(
                    limit = limit,
                    lastUpdated = lastUpdated,
                )
            )
        )
    }

    fun fetchMessages(
        chatRoomId: String,
        cursor: Long? = null,
        limit: Int = 20,
        requestId: String? = null,
    ): String {
        return Json.encodeToString(
            ChatSocketDto(
                action = ChatSocketActions.FETCH_MESSAGES,
                requestId = requestId,
                payload = FetchMessagesDto(
                    chatRoomId = chatRoomId,
                    cursor = cursor,
                    limit = limit,
                )
            )
        )
    }

    fun reactToMessage(
        messageId: Long,
        reaction: String,
        requestId: String? = null,
    ): String {
        return Json.encodeToString(
            ChatSocketDto(
                action = ChatSocketActions.REACT_TO_MESSAGE,
                requestId = requestId,
                payload = ReactToMessageDto(
                    messageId = messageId,
                    reaction = reaction,
                )
            )
        )
    }

    fun setTyping(
        chatRoomId: String,
        isTyping: Boolean,
        requestId: String? = null,
    ): String {
        return Json.encodeToString(
            ChatSocketDto(
                action = ChatSocketActions.SET_TYPING,
                requestId = requestId,
                payload = SetTypingDto(
                    chatRoomId = chatRoomId,
                    isTyping = isTyping,
                )
            )
        )
    }

    fun markRead(
        chatRoomId: String,
        requestId: String? = null,
    ): String {
        return Json.encodeToString(
            ChatSocketDto(
                action = ChatSocketActions.MARK_READ,
                requestId = requestId,
                payload = MarkReadDto(chatRoomId = chatRoomId)
            )
        )
    }

    fun markDelivered(
        chatRoomId: String,
        requestId: String? = null,
    ): String {
        return Json.encodeToString(
            ChatSocketDto(
                action = ChatSocketActions.MARK_DELIVERED,
                requestId = requestId,
                payload = MarkDeliveredDto(chatRoomId = chatRoomId)
            )
        )
    }

    fun deleteMessage(
        messageId: Long,
        deleteOption: String,
        requestId: String? = null,
    ): String {
        return Json.encodeToString(
            ChatSocketDto(
                action = ChatSocketActions.DELETE_MESSAGE,
                requestId = requestId,
                payload = DeleteMessageDto(
                    messageId = messageId,
                    deleteOption = deleteOption,
                )
            )
        )
    }

    fun editMessage(
        messageId: Long,
        text: String,
        requestId: String? = null,
    ): String {
        return Json.encodeToString(
            ChatSocketDto(
                action = ChatSocketActions.EDIT_MESSAGE,
                requestId = requestId,
                payload = EditMessageDto(
                    messageId = messageId,
                    text = text,
                )
            )
        )
    }

    fun sendMessage(
        chatRoomId: String,
        text: String? = null,
        messageType: String = "text",
        mediaIds: List<Long> = emptyList(),
        replyToId: Long? = null,
        mediaUrl: String? = null,
        stickerUrl: String? = null,
        requestId: String? = null,
    ): String {
        return Json.encodeToString(
            ChatSocketDto(
                action = ChatSocketActions.SEND_MESSAGE,
                requestId = requestId,
                payload = SendMessageDto(
                    chatRoomId = chatRoomId,
                    text = text,
                    messageType = messageType,
                    mediaIds = mediaIds,
                    replyToId = replyToId,
                    mediaUrl = mediaUrl,
                    stickerUrl = stickerUrl,
                )
            )
        )
    }
}