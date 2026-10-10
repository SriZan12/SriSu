package com.srisu.srisu.features.couplechat.presentation

import com.srisu.srisu.features.couplechat.data.*
import com.srisu.srisu.core.data.remote.SocketState
import kotlin.test.*

class ChatPresentationTest {
    private fun message(time: String = "2026-10-10T10:00:00Z") = LocalChatMessage("one","room",1,time,timelineKey="one")
    @Test fun receiptFlagsCannotTurnAnUnconfirmedSendIntoSuccess() {
        for(transport in listOf(ChatTransport.QUEUED,ChatTransport.SENDING,ChatTransport.FAILED)) {
            assertEquals(transport.name.lowercase().replaceFirstChar(Char::uppercase),messageStatus(message().copy(transport=transport,read=true,delivered=true)))
        }
        assertEquals("Sent",messageStatus(message().copy(transport=ChatTransport.SENT)))
        assertEquals("Delivered",messageStatus(message().copy(transport=ChatTransport.SENT,delivered=true)))
        assertEquals("Read",messageStatus(message().copy(transport=ChatTransport.SENT,read=true)))
    }
    @Test fun groupingUsesARealTimeGapAndRespectsSenderAndDeletion() {
        assertTrue(messagesGroup(message(),message("2026-10-10T10:04:59Z")))
        assertFalse(messagesGroup(message(),message("2026-10-10T10:05:01Z")))
        assertFalse(messagesGroup(message(),message().copy(authorId=2)))
        assertFalse(messagesGroup(message().copy(deleted=true),message()))
        assertFalse(messagesGroup(message("invalid"),message()))
    }
    @Test fun presenceIsNotInventedAndPrivacyDisablesIt() {
        val state=ConversationState(roomId="room",loading=false,security=ConversationSecurity.READY)
        assertEquals("End-to-end encrypted",conversationSubtitle(state))
        val active=state.copy(partnerActivity=PartnerChatActivity("room",online=true,typing=true))
        assertEquals("Typing…",conversationSubtitle(active))
        assertEquals("End-to-end encrypted",conversationSubtitle(active.copy(preferences=ConversationPreferences(presence=false))))
        assertEquals("End-to-end encrypted",conversationSubtitle(state.copy(partnerActivity=PartnerChatActivity("another-room",online=true))))
        assertFalse(state.copy(security=ConversationSecurity.IDENTITY_CHANGED).canCompose)
        assertFalse(state.copy(security=ConversationSecurity.KEY_LOST).canCompose)
        assertEquals("Reconnecting…",conversationSubtitle(state,SocketState.Reconnecting(2)))
        assertEquals("Live updates paused",conversationSubtitle(state,SocketState.Disconnected))
        assertEquals("Verify your partner’s new identity",conversationSubtitle(state.copy(security=ConversationSecurity.IDENTITY_CHANGED),SocketState.Reconnecting(2)))
    }
}
