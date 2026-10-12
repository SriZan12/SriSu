package com.srisu.srisu.features.couplechat.data

/** Ephemeral local UI signal. Never persisted, logged, or sent over the network. */
internal data class ConversationMessageArrival(val accountId: Long, val roomId: String, val messageId: String, val text: String)

/** Called after a durable confirmation, never for optimistic sends, history, or receipts. */
internal fun confirmedMessageArrival(accountId: Long, before: LocalChatMessage?, after: LocalChatMessage?,
    visible: Boolean, live: Boolean): ConversationMessageArrival? {
    if (!visible || !live || after == null || after.transport != ChatTransport.SENT || after.sequence == null ||
        after.hidden || after.deleted || after.unavailable || before?.transport == ChatTransport.SENT) return null
    if (before != null && (before.id != after.id || before.roomId != after.roomId)) return null
    val text = when (val content = after.content) {
        is ChatContent.Text -> content.body
        is ChatContent.Media -> content.caption
        else -> return null
    }
    return text.takeIf { it.isNotBlank() }?.let { ConversationMessageArrival(accountId, after.roomId, after.id, it) }
}
