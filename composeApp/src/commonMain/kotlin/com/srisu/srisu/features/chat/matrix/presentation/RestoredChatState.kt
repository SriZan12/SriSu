package com.srisu.srisu.features.chat.matrix.presentation

import com.srisu.srisu.features.chat.matrix.*
import com.srisu.srisu.features.couplechat.data.*
import com.srisu.srisu.features.couplechat.presentation.*
import kotlin.time.Instant

/** Local display identity only; all authorization remains in the Matrix repository. */
internal const val PARTNER_DISPLAY_ID = -1L

/** Own sends from another device are arrivals, not an instruction to leave the reading position. */
internal fun shouldFollowNewMessage(message: MatrixChatMessage, wasAtLatest: Boolean): Boolean =
    wasAtLatest || (message.mine && message.transactionId != null)

internal fun MatrixChatMessage.toDisplay(room: MatrixChatRoomState, thumbnails: Map<String, String>): LocalChatMessage {
    val self = room.accountId ?: 0L
    val quote = replyTo?.let { id ->
        val original = room.messages.firstOrNull { it.id == id }
        ChatQuote(id, if (original?.mine == true) self else PARTNER_DISPLAY_ID,
            original?.takeUnless { it.deleted || it.unavailable }?.let { it.body.ifBlank { it.attachment?.kind ?: "Message" } } ?: "Original message unavailable")
    }
    val content = challengeReference?.takeUnless { deleted || unavailable }?.let { ChatContent.Challenge(body, it) } ?: sparkReference?.takeUnless { deleted || unavailable }?.let { ChatContent.Spark(body, it) } ?: momentReference?.takeUnless { deleted || unavailable }?.let { ChatContent.Moment(body, it) } ?: attachment?.let { media ->
        ChatContent.Media(listOf(ChatMedia(AttachmentDescriptor(id, room.roomId, id), media.kind, media.mime,
            media.width, media.height, media.durationMs, thumbnails[id])), body, quote)
    } ?: ChatContent.Text(body, quote)
    return LocalChatMessage(id, room.roomId, if (mine) self else PARTNER_DISPLAY_ID,
        Instant.fromEpochMilliseconds(timestampMillis).toString(), content,
        transport = when (delivery) { MatrixMessageDelivery.SENDING -> ChatTransport.SENDING; MatrixMessageDelivery.FAILED -> ChatTransport.FAILED; MatrixMessageDelivery.SENT -> ChatTransport.SENT },
        deleted = deleted, unavailable = unavailable, read = read, delivered = read,
        reactions = reactions.mapKeys { if (it.key == senderMatrixId && mine || it.key != senderMatrixId && !mine) self else PARTNER_DISPLAY_ID },
        timelineKey = stableKey)
}

/** History, pagination, receipt changes and duplicate acknowledgements cannot start an effect. */
internal class RomanticArrivalTracker(private val openedAt: Long = kotlin.time.Clock.System.now().toEpochMilliseconds()) {
    private var room: String? = null
    private var baseline: Long? = null
    private val confirmed = mutableSetOf<String>()
    fun accept(state: MatrixChatRoomState): List<RomanticEffect> {
        if (room != state.roomId || state.availability !is MatrixChatAvailability.Ready) {
            room = state.roomId; baseline = null; confirmed.clear()
        }
        if (state.availability !is MatrixChatAvailability.Ready) return emptyList()
        val sent = state.messages.filter { it.delivery == MatrixMessageDelivery.SENT }
        if (baseline == null) {
            baseline = maxOf(openedAt, sent.maxOfOrNull { it.timestampMillis } ?: 0L)
            confirmed.addAll(sent.map { it.stableKey })
            return emptyList()
        }
        val result = sent.filter { confirmed.add(it.stableKey) && it.timestampMillis > baseline!! && !it.deleted && !it.unavailable }
        return result.mapNotNull { message ->
            val account = state.accountId ?: return@mapNotNull null
            RomanticPhrases.match(message.body)?.let { RomanticEffect(account, state.roomId, message.id, it) }
        }
    }
}
