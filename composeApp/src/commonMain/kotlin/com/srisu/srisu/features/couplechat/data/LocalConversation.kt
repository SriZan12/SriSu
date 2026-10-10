@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlin.time.ExperimentalTime::class)
package com.srisu.srisu.features.couplechat.data

import com.srisu.srisu.features.couplechat.crypto.ChatJson
import com.srisu.srisu.features.couplechat.crypto.ProtectedChatStore
import com.srisu.srisu.features.couplechat.crypto.ProtectedEntry
import com.srisu.srisu.features.couplechat.crypto.ChatStorageFailure
import kotlinx.serialization.encodeToString
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** All callers hold the repository's local mutex. A native write commits all
 * projection records plus cursor together; native decryption has its own durable
 * inbox journal, making a crash between decryption and projection replay-safe.
 */
internal class ChatQueueFull : Exception()
internal class LocalConversation(val store: ProtectedChatStore, val roomId: String) {
    val accountId get() = store.accountId
    val cursorKey = "app/cursor/$roomId"
    val timelinePrefix = "app/timeline/$roomId/"
    val queuePrefix = "app/queue/$roomId/"
    val draftKey = "app/draft/$roomId"
    val replyKey = "app/reply/$roomId"
    val preferencesKey = "app/preferences/$roomId"
    val mediaDraftKey = "app/media-draft/$roomId"
    val cancelPrefix = "app/cancel-message/$roomId/"
    val unreadPrefix = "app/unread/$roomId/"
    val preparingMediaPrefix = "app/preparing-media/$roomId/"
    val releaseMediaPrefix = "app/release-media/$roomId/"
    fun mediaDraft(): MediaDraft? = store.record(mediaDraftKey)
    fun messageKey(id: String) = "app/message/$roomId/$id"
    private fun hiddenKey(id: String) = "app/hidden/$roomId/$id"
    fun seenKey(id: String) = "app/processed/$roomId/$id"
    fun ownPayloadKey(id: String) = "app/own-operation/$roomId/$id"
    fun queuePointer(id: String) = "app/queue-key/$roomId/$id"
    fun cursor(): Long = store.record<Long>(cursorKey) ?: 0
    fun preferences() = store.record<ConversationPreferences>(preferencesKey) ?: ConversationPreferences()
    fun draft(): String = store.record<String>(draftKey).orEmpty()
    fun reply(): ChatQuote? = store.record<ChatQuote>(replyKey)?.let {quote ->
        val target=rawMessage(quote.messageId)
        if(target==null || target.deleted || target.hidden || target.unavailable)quote.copy(preview="Message unavailable") else quote
    }

    fun rawMessage(id: String): LocalChatMessage? = store.record(messageKey(id))
    fun message(id: String): LocalChatMessage? {
        val message = rawMessage(id) ?: return null
        return sanitizeQuote(message)
    }
    private fun sanitizeQuote(message: LocalChatMessage): LocalChatMessage {
        val quote = when(val content=message.content) { is ChatContent.Text -> content.quote; is ChatContent.Media -> content.quote; else -> null } ?: return message
        val target = rawMessage(quote.messageId)
        if (target != null && !target.hidden && !target.deleted && !target.unavailable) return message
        val unavailable=quote.copy(preview="Message unavailable")
        return message.copy(content=when(val content=message.content) {
            is ChatContent.Text -> content.copy(quote=unavailable)
            is ChatContent.Media -> content.copy(quote=unavailable)
            else -> content
        })
    }
    fun page(before: String = "", limit: Int = 50): Pair<List<LocalChatMessage>, Boolean> {
        val index = store.page(timelinePrefix, before, limit)
        val keys = index.mapNotNull { it.value?.let { value -> messageKey(ChatJson.decodeFromString<String>(value)) } }
        return store.readMany(keys).mapNotNull { it?.let { value -> sanitizeQuote(ChatJson.decodeFromString<LocalChatMessage>(value)) } }
            .asReversed() to (index.size == limit)
    }
    fun queue(): List<QueuedChatOperation> = store.page(queuePrefix, limit = 100)
        .mapNotNull { it.value?.let { value -> ChatJson.decodeFromString<QueuedChatOperation>(value) } }.asReversed()
    fun queued(id: String): QueuedChatOperation? = store.record<String>(queuePointer(id))?.let { store.record(it) }
    fun writeQueue(value: QueuedChatOperation) = store.put(value.queueKey, value)

    private inline fun <reified T> record(key: String, value: T) = ProtectedEntry(key, ChatJson.encodeToString(value))

    fun enqueue(kind: String, content: ChatContent, targetId: String? = null, replyTo: String? = null,
                registration: ChatRegistration? = null, partner: ChatDevice? = null,
                receiptMarker: String? = null, messageId: String? = null, clearComposer: Boolean = true,
                completedMarker: String? = null, completedMarkers: List<String> = emptyList()): QueuedChatOperation {
        if (store.count(queuePrefix) >= 100) throw ChatQueueFull()
        val id = messageId ?: Uuid.random().toString()
        val time = Clock.System.now()
        val order = maxOf(time.toEpochMilliseconds(), (store.record<Long>("app/last-order") ?: 0) + 1)
        val suffix = order.toString().padStart(20, '0') + ":" + id
        val queueKey = queuePrefix + suffix
        val target = targetId ?: id
        val versionKey = "app/version/$roomId/$kind/$target"
        val version = if (kind == "message") 0 else (store.record<Long>(versionKey) ?: 0) + 1
        val operation = QueuedChatOperation(id, roomId, kind, target, ChatPrivatePayload(content = content), queueKey,
            replyTo, version, registration?.deviceId, partner?.id)
        val entries = mutableListOf(record("app/last-order", order), record(queueKey, operation), record(queuePointer(id), queueKey),
            record(ownPayloadKey(id), operation.content), record(versionKey, version))
        if (receiptMarker != null) {
            entries += record(receiptMarker, id)
            entries += ProtectedEntry("app/pending-receipt/$roomId/$kind/$target", null)
        }
        if(completedMarker!=null) entries += ProtectedEntry(completedMarker,null)
        entries += completedMarkers.map { ProtectedEntry(it,null) }
        if (kind == "message") {
            val timeline = timelinePrefix + "p:" + suffix
            val message = LocalChatMessage(id, roomId, accountId, time.toString(), content = content, replyTo = replyTo, timelineKey = timeline)
            entries += record(messageKey(id), message)
            entries += record(timeline, id)
            if(clearComposer) {
                entries += record(draftKey, "")
                entries += ProtectedEntry(replyKey,null)
                if (content is ChatContent.Media) entries += ProtectedEntry(mediaDraftKey,null)
            }
        }
        store.write(entries)
        return operation
    }

    fun receipt(target: LocalChatMessage, kind: String, registration: ChatRegistration, partner: ChatDevice) {
        if (target.authorId == accountId || target.unavailable) return
        val marker = "app/receipt/$roomId/$kind/${target.id}"
        if (store.read(marker) != null) {
            store.write(listOf(ProtectedEntry("app/pending-receipt/$roomId/$kind/${target.id}", null)))
            return
        }
        if (store.count(queuePrefix) >= 90) {
            store.put("app/pending-receipt/$roomId/$kind/${target.id}", target.id)
            return
        }
        enqueue(kind, ChatContent.Receipt, target.id, registration = registration, partner = partner, receiptMarker = marker)
    }

    fun accepted(operation: QueuedChatOperation, result: ChatAcceptance) {
        val entries = mutableListOf(ProtectedEntry(operation.queueKey, null), ProtectedEntry(queuePointer(operation.id), null))
        entries += mediaCleanup(operation.content.content, delete=rawMessage(operation.targetId)?.hidden==true)
        if (operation.kind == "message") rawMessage(operation.targetId)?.let { old ->
            val timeline = timelinePrefix + "c:" + result.sequence.toString().padStart(20, '0') + ":" + old.id
            entries += ProtectedEntry(old.timelineKey, null)
            entries += record(timeline, old.id)
            entries += record(messageKey(old.id), old.copy(transport = ChatTransport.SENT, sequence = result.sequence,
                createdAt = result.acceptedAt, failure = null, timelineKey = timeline))
        }
        store.write(entries)
    }

    fun failed(operation: QueuedChatOperation, code: String, permanent: Boolean, retryAt: Long) {
        val updated = operation.copy(attempts = operation.attempts + 1, failure = code, permanentFailure = permanent, retryAt = retryAt)
        val entries = if (permanent) mutableListOf(ProtectedEntry(operation.queueKey, null), ProtectedEntry(queuePointer(operation.id), null),
            record("app/failed-operation/$roomId/${operation.id}", updated)) else mutableListOf(record(operation.queueKey, updated))
        if (operation.kind == "message") rawMessage(operation.targetId)?.let {
            entries += record(messageKey(it.id), it.copy(transport = if (permanent) ChatTransport.FAILED else ChatTransport.QUEUED, failure = code))
        }
        if(permanent)entries += mediaCleanup(operation.content.content,delete=false)
        store.write(entries)
    }

    private fun mediaCleanup(content:ChatContent?,delete:Boolean):List<ProtectedEntry> =
        (content as? ChatContent.Media)?.items.orEmpty().map {record((if(delete)preparingMediaPrefix else releaseMediaPrefix)+it.descriptor.id,true)}

    fun releaseArchivedDraft() {
        val draft=mediaDraft() ?: return
        store.write(draft.items.map {record(releaseMediaPrefix+it.descriptor.id,true)})
    }

    fun hide(id: String) {
        val old = rawMessage(id) ?: return
        val entries=mutableListOf(record(hiddenKey(id), true), record(messageKey(id), old.copy(hidden = true, content = null, reactions = emptyMap(),cardResponses=emptyMap())),
            ProtectedEntry(unreadPrefix+id,null),ProtectedEntry("app/spark/$roomId/$id",null),ProtectedEntry("app/spark-answer/$roomId/$id",null),ProtectedEntry("app/spark-watch/$roomId/$id",null))
        // An uncertain sealed send still needs its ciphertext until acknowledged.
        if(queued(id)==null) {
            entries += mediaCleanup(old.content,delete=true)
            entries += ProtectedEntry("app/failed-operation/$roomId/$id",null)
            entries += ProtectedEntry(ownPayloadKey(id),null)
        }
        store.write(entries)
    }

    fun sending(operation: QueuedChatOperation) {
        if(operation.kind=="message") rawMessage(operation.id)?.let {store.put(messageKey(it.id),it.copy(transport=ChatTransport.SENDING))}
    }
    fun recoverSending() {
        val entries=queue().mapNotNull { operation -> rawMessage(operation.id)?.takeIf {it.transport==ChatTransport.SENDING}
            ?.let {record(messageKey(it.id),it.copy(transport=ChatTransport.QUEUED))} }
        if(entries.isNotEmpty())store.write(entries)
    }
    fun cancelUnsealed(operation: QueuedChatOperation) {
        val message=rawMessage(operation.id) ?: return
        val entries=mutableListOf(ProtectedEntry(operation.queueKey,null),ProtectedEntry(queuePointer(operation.id),null),
            ProtectedEntry(ownPayloadKey(operation.id),null),record(hiddenKey(operation.id),true),
            record(messageKey(operation.id),message.copy(hidden=true,content=null,transport=ChatTransport.FAILED,failure="cancelled")))
        for(item in (operation.content.content as? ChatContent.Media)?.items.orEmpty())
            entries+=listOf(record("app/cancel-upload/$roomId/${item.descriptor.id}",true),record(preparingMediaPrefix+item.descriptor.id,true))
        store.write(entries)
    }

    fun dropUnsealedReceipt(operation: QueuedChatOperation) {
        require(operation.kind in setOf("receipt.read","receipt.played"))
        store.write(listOf(ProtectedEntry(operation.queueKey,null),ProtectedEntry(queuePointer(operation.id),null),
            ProtectedEntry(ownPayloadKey(operation.id),null),
            ProtectedEntry("app/receipt/$roomId/${operation.kind}/${operation.targetId}",null)))
    }

    fun apply(change: ChatChange, payload: ChatPrivatePayload?, unreadable: Boolean = false) {
        if (change.sequence != cursor() + 1) throw ChatStorageFailure("history_gap")
        val entries = mutableListOf(record(cursorKey, change.sequence))
        val operation = change.operation
        if (operation == null) { store.write(entries); return }
        if (store.read(seenKey(operation.operationId)) != null) { store.write(entries); return }
        val old = rawMessage(operation.targetId)
        val hidden = store.record<Boolean>(hiddenKey(operation.targetId)) == true || old?.hidden == true
        val deleted = old?.deleted == true || operation.targetDeleted
        var next = old
        if (operation.kind == "message") {
            val timeline = timelinePrefix + "c:" + change.sequence.toString().padStart(20, '0') + ":" + operation.targetId
            old?.let { entries += ProtectedEntry(it.timelineKey, null) }
            val body = if (hidden || deleted || unreadable) null else payload?.content ?: old?.content
            next = LocalChatMessage(operation.targetId, roomId, operation.actorId, change.createdAt,
                content = body, transport = ChatTransport.SENT, sequence = change.sequence, deleted = deleted, hidden = hidden,
                unavailable = !deleted && !hidden && (unreadable || (body == null)), replyTo = operation.replyTo,
                reactions = old?.reactions.orEmpty(), delivered = old?.delivered == true, read = old?.read == true,
                played = old?.played == true, timelineKey = timeline)
            entries += record(timeline, operation.targetId)
        } else if (old != null && operation.applied && !unreadable) {
            next = when {
                operation.kind == "delete" && operation.actorId == old.authorId -> old.copy(deleted = true, content = null, reactions = emptyMap())
                operation.kind == "reaction" && !deleted && !hidden && payload?.content is ChatContent.Reaction -> {
                    val reaction = (payload?.content as? ChatContent.Reaction)?.emoji
                    old.copy(reactions = if (reaction == null) old.reactions - operation.actorId else old.reactions + (operation.actorId to reaction))
                }
                operation.kind == "receipt.delivered" && operation.actorId != old.authorId -> old.copy(delivered = true)
                operation.kind == "receipt.read" && operation.actorId != old.authorId -> old.copy(delivered = true, read = true)
                operation.kind == "receipt.played" && operation.actorId != old.authorId -> old.copy(delivered = true, played = true)
                operation.kind == "card.response" && !deleted && !hidden && payload?.content is ChatContent.CardResponse &&
                    validAskReply((old.content as? ChatContent.Card)?.card, payload.content.reply) && operation.actorId != old.authorId ->
                    old.copy(cardResponses=old.cardResponses+(operation.actorId to payload.content.reply))
                else -> old
            }
        }
        // Server tombstones dominate even a failed/unsupported encrypted control.
        if (next != null && deleted) next = next.copy(deleted = true, content = null, reactions = emptyMap(),cardResponses=emptyMap())
        if(next?.deleted==true)entries += mediaCleanup(old?.content,delete=true)
        if(deleted)entries+=ProtectedEntry(unreadPrefix+operation.targetId,null)
        next?.let { entries += record(messageKey(it.id), it) }
        if (operation.kind == "message" && operation.actorId != accountId && !unreadable && !operation.unavailable) {
            if(!deleted && !hidden)entries+=record(unreadPrefix+operation.targetId,true)
            val marker = "app/receipt/$roomId/receipt.delivered/${operation.targetId}"
            if (store.read(marker) == null) entries += record("app/pending-receipt/$roomId/receipt.delivered/${operation.targetId}", operation.targetId)
        }
        entries += record(seenKey(operation.operationId), true)
        if (operation.actorId == accountId) {
            queued(operation.operationId)?.let { queued ->
                entries += mediaCleanup(queued.content.content,delete=hidden || deleted)
                entries += ProtectedEntry(queued.queueKey, null)
                entries += ProtectedEntry(queuePointer(queued.id), null)
            }
            entries += ProtectedEntry(ownPayloadKey(operation.operationId), null)
        }
        store.write(entries)
    }
}

internal fun validAskReply(card: ChatCard?, reply: CardReply): Boolean = card is ChatCard.Ask && reply.answer.length<=2000 &&
    if(card.options.isEmpty()) reply.answer.isNotBlank() && reply.vote==null
    else reply.vote in card.options.indices && reply.answer.isEmpty()
