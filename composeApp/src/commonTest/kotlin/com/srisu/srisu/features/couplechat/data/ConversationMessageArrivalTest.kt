package com.srisu.srisu.features.couplechat.data

import kotlin.test.*

class ConversationMessageArrivalTest {
    private val pending=LocalChatMessage("message","room",1,"2026-10-12T10:00:00Z",ChatContent.Text("I love you"),timelineKey="local")
    private val confirmed=pending.copy(transport=ChatTransport.SENT,sequence=20)
    private fun event(before: LocalChatMessage?=pending, after: LocalChatMessage?=confirmed, visible:Boolean=true, live:Boolean=true)=
        confirmedMessageArrival(1,before,after,visible,live)
    @Test fun outgoingAcceptanceAndLiveIncomingMessagesEmitOnce() {
        assertEquals("I love you",event()?.text)
        assertEquals("message",event(before=null,after=confirmed.copy(authorId=2))?.messageId)
        assertNull(event(before=confirmed)) // echo / duplicate acknowledgement / recomposition
        assertNull(event(before=confirmed,after=confirmed.copy(read=true,reactions=mapOf(2L to "❤️"))))
    }
    @Test fun loadingHistoryBackgroundAndUnavailableMessagesNeverEmit() {
        assertNull(event(live=false));assertNull(event(visible=false));assertNull(event(after=null))
        for(after in listOf(confirmed.copy(hidden=true),confirmed.copy(deleted=true),confirmed.copy(unavailable=true),
            confirmed.copy(sequence=null),pending,pending.copy(transport=ChatTransport.FAILED),pending.copy(transport=ChatTransport.SENDING)))
            assertNull(event(after=after))
    }
    @Test fun successfulRetryEmitsButReactionQuoteAndCardTextDoNot() {
        assertNotNull(event(before=pending.copy(transport=ChatTransport.FAILED)))
        assertEquals("Fine",event(after=confirmed.copy(content=ChatContent.Text("Fine",ChatQuote("other",2,"I love you"))))?.text)
        assertNull(event(after=confirmed.copy(content=ChatContent.Reaction("❤️"))))
        assertNull(event(after=confirmed.copy(content=ChatContent.Receipt)))
        assertEquals("Miss you",event(after=confirmed.copy(content=ChatContent.Media(emptyList(),"Miss you")))?.text)
        assertNull(event(after=confirmed.copy(content=ChatContent.Media(emptyList()))))
        assertNull(event(after=confirmed.copy(roomId="other-room")))
    }
}
