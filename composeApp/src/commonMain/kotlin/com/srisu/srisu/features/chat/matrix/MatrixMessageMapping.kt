package com.srisu.srisu.features.chat.matrix

import de.connect2x.trixnity.client.store.RoomOutboxMessage
import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.client.store.isEncrypted
import de.connect2x.trixnity.core.model.events.RedactedEventContent
import de.connect2x.trixnity.core.model.events.MessageEventContent
import de.connect2x.trixnity.core.model.events.m.RelatesTo
import de.connect2x.trixnity.core.model.events.m.ReactionEventContent
import de.connect2x.trixnity.core.model.events.m.room.RedactionEventContent
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

internal fun RoomMessageEventContent.attachment(): MatrixChatAttachment? = when (val content = typed()) {
    is RoomMessageEventContent.FileBased.Image -> content.file?.let {
        MatrixChatAttachment("photo", content.info?.mimeType ?: "image/jpeg", it,
            content.info?.width ?: 0, content.info?.height ?: 0, thumbnail = content.info?.thumbnailFile)
    }
    is RoomMessageEventContent.FileBased.Video -> content.file?.let {
        MatrixChatAttachment("video", content.info?.mimeType ?: "video/mp4", it,
            content.info?.width ?: 0, content.info?.height ?: 0, content.info?.duration ?: 0, content.info?.thumbnailFile)
    }
    is RoomMessageEventContent.FileBased.Audio -> content.file?.let {
        val voice = voiceExtension()
        MatrixChatAttachment("voice", content.info?.mimeType ?: "audio/wav", it,
            durationMs = content.info?.duration ?: (voice?.get("duration") as? JsonPrimitive)?.longOrNull ?: 0,
            waveform = decodeVoiceWaveform(voice?.get("waveform")))
    }
    else -> null
}

/** Strip the Matrix plaintext reply fallback; the attached quote has its own UI. */
internal fun RoomMessageEventContent.displayBody(): String = typed().typedDisplayBody()

private fun RoomMessageEventContent.typedDisplayBody(): String {
    val text = if (relatesTo?.replyTo != null && body.startsWith("> ") && "\n\n" in body)
        body.substringAfter("\n\n") else body
    return if (this is RoomMessageEventContent.FileBased && text in setOf("Photo", "Video", "Voice message", fileName)) "" else text
}

internal fun matrixTimelineMessages(events: List<TimelineEvent>, ownId: String, partnerId: String, readId: String?): List<MatrixChatMessage> {
    val allowed = events.filter { it.event.sender.full in setOf(ownId, partnerId) }
    val reactions = allowed.filter { it.event.isEncrypted }
        .filter { (it.content?.getOrNull() as? MessageEventContent)?.relatesTo is RelatesTo.Annotation }
        .sortedBy { it.event.originTimestamp }
    val ordered = allowed.sortedBy { it.event.originTimestamp }
    val readIndex = ordered.indexOfFirst { it.event.id.full == readId }
    return ordered.mapIndexedNotNull { index, item ->
        val event = item.event
        // Redaction replaces the ciphertext object itself in Trixnity's timeline.
        // Retain its stable row as a deletion notice without exposing cached content.
        val encryptedTombstone = (event.content as? RedactedEventContent)?.eventType == "m.room.encrypted"
        if (!event.isEncrypted && !encryptedTombstone) return@mapIndexedNotNull null
        val content = item.content?.getOrNull()
        if (content is ReactionEventContent || content is RedactionEventContent || (content as? MessageEventContent)?.relatesTo is RelatesTo.Annotation) return@mapIndexedNotNull null
        val deleted = encryptedTombstone || content is RedactedEventContent || event.unsigned?.redactedBecause != null
        val roomMessage = (content as? RoomMessageEventContent).takeUnless { deleted }
        val media = roomMessage?.attachment()
        val related = reactions.filter { ((it.content?.getOrNull() as? MessageEventContent)?.relatesTo as? RelatesTo.Annotation)?.eventId == event.id }
            .associateBy { it.event.sender.full }
        MatrixChatMessage(
            id = event.id.full, body = roomMessage?.displayBody().orEmpty(), senderMatrixId = event.sender.full,
            mine = event.sender.full == ownId, timestampMillis = event.originTimestamp, delivery = MatrixMessageDelivery.SENT,
            unavailable = !deleted && (roomMessage == null || roomMessage.typed() is RoomMessageEventContent.FileBased && media == null),
            transactionId = event.unsigned?.transactionId?.takeIf { event.sender.full == ownId },
            planReference = com.srisu.srisu.features.plans.data.PlanReference.parse(roomMessage?.externalUrl),
            challengeReference = com.srisu.srisu.features.challenges.data.ChallengeReference.parse(roomMessage?.externalUrl),
            sparkReference = com.srisu.srisu.features.sparks.data.SparkReference.parse(roomMessage?.externalUrl),
            momentReference = com.srisu.srisu.features.moments.data.MomentReference.parse(roomMessage?.externalUrl),
            attachment = media, replyTo = roomMessage?.relatesTo?.replyTo?.eventId?.full,
            reactions = related.mapNotNull { (sender, reaction) ->
                ((reaction.content?.getOrNull() as? MessageEventContent)?.relatesTo as? RelatesTo.Annotation)?.key?.takeIf(String::isNotBlank)?.let { sender to it }
            }.toMap(), ownReactionId = related[ownId]?.event?.id?.full,
            read = readIndex >= 0 && index <= readIndex, deleted = deleted,
        )
    }
}

internal fun RoomOutboxMessage<*>.toMessage(ownMatrixId: String): MatrixChatMessage? {
    val message = content as? RoomMessageEventContent ?: return null
    if (message.relatesTo is RelatesTo.Annotation) return null
    val progress = mediaUploadProgress.value
    return MatrixChatMessage(
        id = eventId?.full ?: "txn:$transactionId", body = message.displayBody(), senderMatrixId = ownMatrixId,
        mine = true, timestampMillis = createdAt.toEpochMilliseconds(),
        delivery = when { sendError != null -> MatrixMessageDelivery.FAILED; eventId == null -> MatrixMessageDelivery.SENDING; else -> MatrixMessageDelivery.SENT },
        retryTransactionId = transactionId.takeIf { sendError != null }, transactionId = transactionId,
        planReference = com.srisu.srisu.features.plans.data.PlanReference.parse(message.externalUrl),
        challengeReference = com.srisu.srisu.features.challenges.data.ChallengeReference.parse(message.externalUrl),
        sparkReference = com.srisu.srisu.features.sparks.data.SparkReference.parse(message.externalUrl),
        momentReference = com.srisu.srisu.features.moments.data.MomentReference.parse(message.externalUrl),
        attachment = message.attachment(), replyTo = message.relatesTo?.replyTo?.eventId?.full,
        uploadProgress = progress?.total?.takeIf { it > 0 }?.let { (progress.transferred.toFloat() / it).coerceIn(0f, 1f) },
    )
}
