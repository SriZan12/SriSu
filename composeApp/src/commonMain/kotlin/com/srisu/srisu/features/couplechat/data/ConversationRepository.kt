@file:OptIn(kotlin.time.ExperimentalTime::class, kotlin.uuid.ExperimentalUuidApi::class)
package com.srisu.srisu.features.couplechat.data

import com.srisu.srisu.core.data.remote.*
import com.srisu.srisu.core.lifecycle.ApplicationLifetime
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.core.session.SessionStamp
import com.srisu.srisu.features.couplechat.crypto.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.math.min
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.uuid.Uuid

private class ChatRequestFailure(val failure: ApiError) : Exception("Chat request failed")
private class ConversationChanged : Exception()
/** A missing/invalid acknowledgement cannot establish whether the server committed. */
internal fun ApiError.chatRetryable(): Boolean = retryable || status == null || status >= 500 ||
    (chatCode ?: code) in setOf("recipient_backlog_full", "rate_limited", "session_refreshed", "prekeys_exhausted", "partner_device_changed")

/** UI state is a bounded projection of protected local records. Network work never
 * holds the local mutex, so a stalled request cannot block draft or optimistic send.
 */
class ConversationRepository(
    private val api: CoupleChatApi,
    private val metadata: CoupleChatRepository,
    private val sessions: SessionCoordinator,
    private val driver: NativeChatDriver,
    private val lifetime: ApplicationLifetime,
) {
    private val scope = CoroutineScope(lifetime.scope.coroutineContext + SupervisorJob(lifetime.scope.coroutineContext[Job]) + Dispatchers.Default)
    // Foreground account synchronization survives leaving the conversation.
    // Only visible messages may create read receipts; no background service.
    private val selected = MutableStateFlow<String?>("")
    private var screenVisible = false
    private val resetEpoch = MutableStateFlow(0)
    private val work = Channel<Unit>(Channel.CONFLATED)
    private val actions = Channel<Triple<SessionStamp, String?, (LocalConversation) -> Unit>>(256)
    private val localMutex = Mutex()
    private var local: LocalConversation? = null
    private var connected: CoupleChatState.SetupRequired? = null
    private var registration: ChatRegistration? = null
    private data class VerifiedSession(val account:Long,val room:String,val ownDevice:String,val peerDevice:String,val peerIdentity:String)
    private var verifiedSession:VerifiedSession?=null
    private fun verifiedSession(db:LocalConversation,native:ChatRegistration,peer:ChatDevice)=
        VerifiedSession(db.accountId,db.roomId,native.deviceId,peer.id,peer.identityKey)
    private var mediaJob: Job? = null
    private var mediaGeneration = 0
    private var safetyIdentity: String? = null
    private val _state = MutableStateFlow(ConversationState())
    val state = _state.asStateFlow()

    init {
        metadata.enter()
        scope.launch {metadata.activity.collect {activity -> _state.update {it.copy(partnerActivity=activity)} }}
        scope.launch {
            for ((stamp, room, block) in actions) {
                try {
                    localMutex.withLock {
                        if (sessions.stamp() != stamp || selected.value == null || !lifetime.foreground.value) return@withLock
                        val db = local ?: return@withLock
                        if (db.accountId != stamp.accountId || db.roomId != room) return@withLock
                        block(db)
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: ChatStorageFailure) { storageFailure(failure) }
                catch (_: ChatQueueFull) { _state.update { it.copy(error = "The local queue is full. Wait for queued messages to send.") } }
                catch (_: Exception) { storageFailure(ChatStorageFailure("storage_or_protocol_failure")) }
            }
        }
        scope.launch {
            combine(sessions.state, lifetime.foreground, selected, resetEpoch) { stamp, foreground, room, _ -> Triple(stamp, foreground, room) }
                .collectLatest { (stamp, foreground, requested) ->
                    mediaJob?.cancel(); mediaJob=null
                    localMutex.withLock { local = null; connected = null; registration = null;verifiedSession=null; _state.value = ConversationState(accountId = stamp.accountId) }
                    val account = stamp.accountId
                    if (!foreground || requested == null || account == null) return@collectLatest
                    val store = ProtectedChatStore(driver, account)
                    try {
                        localMutex.withLock {
                            sessions.ensureCurrent(stamp)
                            val cachedId = requested.ifEmpty { store.record<String>("app/current-room").orEmpty() }
                            if (cachedId.isNotEmpty()) {
                                Uuid.parse(cachedId)
                                openLocal(store, cachedId)
                                connected = store.record<CoupleChatRoom>("app/room/$cachedId")?.let {
                                    CoupleChatState.SetupRequired(it, store.record("app/devices/$cachedId"))
                                }
                            }
                        }
                        coroutineScope {
                            var retryJob: Job? = null
                            var failures = 0
                            launch {
                                metadata.state.collect { value ->
                                    localMutex.withLock {
                                        sessions.ensureCurrent(stamp)
                                        when (value) {
                                            is CoupleChatState.SetupRequired -> {
                                                if (requested.isNotEmpty() && requested != value.room.id) {
                                                    archiveLocal()
                                                } else {
                                                    if (local?.roomId != value.room.id) { archiveLocal(); openLocal(store, value.room.id) }
                                                    connected = value
                                                    store.put("app/current-room", value.room.id)
                                                    store.put("app/room/${value.room.id}", value.room)
                                                    value.devices?.let { devices ->
                                                        store.put("app/devices/${value.room.id}", devices)
                                                        devices.partnerDevice?.let { store.put("app/peer-device/${it.id}", it) }
                                                    }
                                                    val partnerId = value.room.participantIds.singleOrNull { id -> id != account }
                                                    _state.update { it.copy(partnerId = partnerId, relationshipId=value.room.relationshipId,
                                                        partnerName = value.room.participantNames[partnerId.toString()]?.takeIf(String::isNotBlank) ?: "Your partner", loading = false) }
                                                    work.trySend(Unit)
                                                }
                                            }
                                            CoupleChatState.NotConnected -> archiveLocal()
                                            is CoupleChatState.Unavailable -> _state.update { it.copy(loading = false, error = value.message) }
                                            CoupleChatState.Loading -> Unit
                                        }
                                    }
                                }
                            }
                            work.trySend(Unit)
                            for (ignored in work) {
                                retryJob?.cancel()
                                var retryDelay: Long? = null
                                try {
                                    val more = reconcile(stamp)
                                    failures = 0
                                    if (more) retryDelay = localMutex.withLock {
                                        ((local?.queue()?.firstOrNull()?.retryAt ?: 0) - Clock.System.now().toEpochMilliseconds()).coerceIn(100, 30000)
                                    }
                                } catch (cancelled: CancellationException) { throw cancelled }
                                catch (_: ConversationChanged) { retryDelay = 100 }
                                catch (failure: ChatRequestFailure) {
                                    val code = failure.failure.chatCode
                                    localMutex.withLock {
                                        if (failure.failure.status in listOf(403, 404)) archiveLocal()
                                        else _state.update { it.copy(error = errorMessage(code ?: failure.failure.code), synchronizing = false, loading = false) }
                                    }
                                    if (failure.failure.chatRetryable()) {
                                        failures = (failures + 1).coerceAtMost(8)
                                        retryDelay = min(30000L, 500L shl failures) + Random.nextLong(250, 1000)
                                    }
                                } catch (failure: ChatStorageFailure) { storageFailure(failure) }
                                catch (_: SerializationException) {
                                    _state.update { it.copy(error = "The conversation returned an unsupported response. Pending sends are saved.", synchronizing = false) }
                                    retryDelay = 30000
                                }
                                catch (_: Exception) { storageFailure(ChatStorageFailure("storage_or_protocol_failure")) }
                                if (retryDelay != null) retryJob = launch {
                                    delay(retryDelay)
                                    metadata.refresh()
                                    work.trySend(Unit)
                                }
                            }
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: ChatStorageFailure) { storageFailure(failure); awaitCancellation() }
                    catch (_: Exception) { storageFailure(ChatStorageFailure("storage_or_protocol_failure")); awaitCancellation() }
                    finally {
                        withContext(NonCancellable) {
                            localMutex.withLock {
                                store.close()
                                local = null; connected = null; registration = null;verifiedSession=null
                                _state.value = ConversationState(accountId = sessions.stamp().accountId)
                            }
                        }
                    }
                }
        }
    }

    fun enter(roomId: String?) { roomId?.let { Uuid.parse(it) };screenVisible=true; selected.value = roomId.orEmpty();work.trySend(Unit) }
    fun leave() {screenVisible=false;metadata.stopActivity();dismissMedia();selected.value=""}
    fun retry() { metadata.refresh(); work.trySend(Unit) }
    fun close() { metadata.leave();scope.cancel(); work.close(); actions.close() }

    private fun openLocal(store: ProtectedChatStore, roomId: String) {
        val db = LocalConversation(store, roomId)
        db.recoverSending()
        cleanInterruptedMedia(db)
        local = db
        val anchor=store.record<ChatReadingAnchor>("app/anchor/$roomId")
        val end=anchor?.let {db.rawMessage(it.windowEnd)}?.timelineKey
        var (messages, older) = db.page(end?.plus("z").orEmpty())
        while(anchor!=null && end!=null && messages.none {it.id==anchor.messageId} && older && messages.size<300) {
            val (page,more)=db.page(messages.firstOrNull()?.timelineKey.orEmpty())
            messages=page+messages;older=more
        }
        val restored=anchor?.takeIf {value->messages.any {it.id==value.messageId}}
        val room=store.record<CoupleChatRoom>("app/room/$roomId")
        val partnerId=room?.participantIds?.singleOrNull {it!=store.accountId}
        _state.value = ConversationState(accountId = store.accountId, roomId = roomId, messages = messages,
            partnerId=partnerId,relationshipId=room?.relationshipId,partnerName=room?.participantNames?.get(partnerId.toString()) ?: "Your partner",
            draft = db.draft(),reply=db.reply(), preferences = db.preferences(), hasOlder = older, loading = false,mediaDraft=db.mediaDraft(),
            atLatest=restored==null,readingAnchor=restored)
        refreshWindow()
    }

    private fun cleanInterruptedMedia(db: LocalConversation) {
        for((prefix,command) in listOf(db.releaseMediaPrefix to "media_release",db.preparingMediaPrefix to "media_delete")) {
            for(entry in db.store.page(prefix,limit=100)) {
                try {db.store.call(command,buildJsonObject {put("id",entry.key.substringAfterLast('/'))})}
                catch(failure:ChatStorageFailure) {
                    if(failure.code=="storage_key_lost")throw failure
                    // Retain the durable cleanup marker. A cache-file failure
                    // must not prevent opening the protected text history.
                    continue
                }
                db.store.write(listOf(ProtectedEntry(entry.key,null)))
            }
        }
    }

    private fun archiveLocal() {
        metadata.stopActivity()
        verifiedSession=null
        local?.let { db -> db.queue().forEach { db.failed(it, "room_revoked", true, 0) };db.releaseArchivedDraft();cleanInterruptedMedia(db) }
        connected = null
        _state.update { it.copy(security = ConversationSecurity.ARCHIVED, loading = false, synchronizing = false,
            error = "This relationship is no longer active. Saved history stays on this device.") }
    }

    private fun refreshWindow() {
        val db = local ?: return
        cleanInterruptedMedia(db)
        val old = _state.value
        if (old.atLatest) {
            val (messages, older) = db.page()
            _state.update { it.copy(messages = messages, hasOlder = older, draft = db.draft(),reply=db.reply(), preferences = db.preferences(),mediaDraft=db.mediaDraft()) }
        } else {
            val current = old.messages.mapNotNull { db.message(it.id) }
            _state.update { it.copy(messages = current, draft = db.draft(),reply=db.reply(), preferences = db.preferences(),mediaDraft=db.mediaDraft()) }
        }
        val ids=_state.value.messages.flatMap {(it.content as? ChatContent.Media)?.items.orEmpty().map {media->media.descriptor.id}}.distinct()
        val available=try {ids.chunked(200).flatMap {chunk -> db.store.call("media_available",buildJsonObject {put("ids",ChatJson.encodeToJsonElement(chunk))})
            .jsonArray.map {it.jsonPrimitive.content} }.toSet()}
            catch(failure: ChatStorageFailure) {if(failure.code=="storage_key_lost")throw failure;emptySet()}
        val sparks=_state.value.messages.filter {(it.content as? ChatContent.Card)?.card is ChatCard.Spark}
            .associate {it.id to (db.store.record<LocalSparkState>("app/spark/${db.roomId}/${it.id}") ?: LocalSparkState())}
        val formPrefix="app/form/${db.roomId}/"
        val drafts=db.store.page(formPrefix,limit=100).associate {it.key.removePrefix(formPrefix) to ChatJson.decodeFromString<String>(it.value!!)}
        _state.update {it.copy(offlineMedia=available,sparks=sparks,privateDrafts=drafts,unreadCount=db.store.count(db.unreadPrefix),pendingCards=db.queue().filter {it.kind=="card.response"}.map {it.targetId}.toSet()+
            db.store.page("app/spark-answer/${db.roomId}/",limit=10).map {it.key.substringAfterLast('/')})}
    }

    private suspend fun <T> request(stamp: SessionStamp, roomId: String? = null, block: suspend () -> ResultHandler<T?>): T {
        sessions.ensureCurrent(stamp)
        val response = block().result
        sessions.ensureCurrent(stamp)
        if (roomId != null) localMutex.withLock { if (local?.roomId != roomId || connected?.room?.id != roomId) throw ConversationChanged() }
        return when (response) {
            is NetworkAPIResult.Success -> response.response ?: throw SerializationException("Missing data")
            is NetworkAPIResult.Error -> throw ChatRequestFailure(response.failure)
        }
    }

    private suspend fun setup(stamp: SessionStamp): Pair<LocalConversation, ChatDevices>? {
        val context = localMutex.withLock {
            val db = local ?: return null
            val snapshot = connected ?: return null
            val devices = snapshot.devices ?: return null
            val native = ChatJson.decodeFromJsonElement<ChatRegistration>(db.store.call("registration"))
            registration = native
            Triple(db, devices, native)
        }
        val (db, devices, native) = context
        var current = devices
        val own = devices.currentDevice
        if (own == null) {
            val registered = request(stamp) { api.register(native) }
            current = devices.copy(currentDevice = registered, sessionOwnsDevice = true)
            metadata.refresh()
        } else if (!devices.sessionOwnsDevice || own.id != native.deviceId) {
            val approved = localMutex.withLock { db.store.record<String>("app/replacement-authorized") }
            if (approved != own.id) {
                _state.update { it.copy(security = ConversationSecurity.REPLACE_DEVICE, error = null) }; return null
            }
            val replacement = localMutex.withLock {
                val next = if (native.deviceId == own.id) ChatJson.decodeFromJsonElement<ChatRegistration>(db.store.call("replace")) else native
                next.copy(replaceDeviceId = own.id).also { registration = it }
            }
            val registered = request(stamp) { api.register(replacement) }
            current = devices.copy(currentDevice = registered, sessionOwnsDevice = true)
            metadata.refresh()
        }
        localMutex.withLock {
            sessions.ensureCurrent(stamp)
            db.store.write(listOf(ProtectedEntry("app/replacement-authorized", null)))
            if (connected?.room?.id == db.roomId) connected = connected?.copy(devices = current)
            db.store.put("app/devices/${db.roomId}", current)
            val peer=current.partnerDevice
            _state.update { it.copy(security = when {
                peer==null -> ConversationSecurity.PARTNER_SETUP
                it.security==ConversationSecurity.READY && verifiedSession==verifiedSession(db,native,peer) -> ConversationSecurity.READY
                else -> ConversationSecurity.SETTING_UP
            }, synchronizing = true, error = null) }
        }
        return db to current
    }

    private suspend fun reconcile(stamp: SessionStamp): Boolean {
        val (db, devices) = setup(stamp) ?: return false
        val native = localMutex.withLock { checkNotNull(registration) }
        val after = localMutex.withLock { db.cursor() }
        val page = request(stamp, db.roomId) { api.changes(db.roomId, native.deviceId, after) }
        if (page.changes.size > 50 || page.cursor > page.highWatermark || page.cursor < after ||
            page.hasMore != (page.cursor < page.highWatermark) || (page.changes.isEmpty() && page.cursor != after) ||
            (page.changes.isEmpty() && page.hasMore) ||
            page.changes.lastOrNull()?.sequence?.let { it != page.cursor } == true) throw SerializationException("Invalid change page")
        for (change in page.changes) {
            sessions.ensureCurrent(stamp)
            var peer: ChatDevice? = null
            val operation = change.operation
            if (operation != null && operation.actorId != db.accountId && !operation.unavailable && operation.ciphertext != null) {
                peer = localMutex.withLock { db.store.record<ChatDevice>("app/peer-device/${operation.senderDeviceId}") }
                if (peer == null) {
                    peer = request(stamp, db.roomId) { api.participantDevice(db.roomId, operation.senderDeviceId) }
                    if (peer.userId != operation.actorId || peer.id != operation.senderDeviceId) throw SerializationException("Incorrect participant device")
                    val resolved = peer
                    localMutex.withLock { db.store.put("app/peer-device/${resolved.id}", resolved) }
                }
            }
            val senderDevice = peer
            localMutex.withLock {
                sessions.ensureCurrent(stamp)
                if (local !== db || connected?.room?.id != db.roomId) throw ConversationChanged()
                if (change.sequence <= db.cursor()) return@withLock
                var payload: ChatPrivatePayload? = null
                var unreadable = false
                if (operation != null && db.store.read(db.seenKey(operation.operationId)) == null) {
                    if (operation.actorId == db.accountId) payload = db.store.record(db.ownPayloadKey(operation.operationId))
                    else if (senderDevice != null) {
                        try {
                            val content = db.store.call("open", buildJsonObject {
                                put("room_id", db.roomId); put("peer_user_id", senderDevice.userId.toString()); put("peer_identity", senderDevice.identityKey)
                                put("operation", ChatJson.encodeToJsonElement(operation))
                            })
                            val decoded = ChatJson.decodeFromJsonElement<ChatPrivatePayload>(content)
                            payload = decoded
                            if (!validPayload(operation, decoded)) unreadable = true
                        } catch (failure: ChatStorageFailure) {
                            if (failure.code !in setOf("invalid_request", "invalid_envelope", "invalid_ciphertext", "replay_rejected", "session_missing", "operation_conflict")) throw failure
                            unreadable = true
                        } catch (_: SerializationException) { unreadable = true }
                    } else unreadable = operation.unavailable
                }
                db.apply(change, payload, unreadable)
                if (operation?.targetDeleted==true) {
                    db.store.write(listOf(ProtectedEntry("app/spark/${db.roomId}/${operation.targetId}",null),
                        ProtectedEntry("app/spark-answer/${db.roomId}/${operation.targetId}",null),ProtectedEntry("app/spark-watch/${db.roomId}/${operation.targetId}",null)))
                    _state.update { if(it.openedMedia?.messageId==operation.targetId)it.copy(openedMedia=null) else it }
                }
                if (operation?.kind == "message" && operation.actorId != db.accountId && !_state.value.atLatest)
                    _state.update { it.copy(newMessages = it.newMessages + 1) }
            }
        }
        localMutex.withLock { refreshWindow() }
        val partner = devices.partnerDevice
        if (partner == null) {
            _state.update { it.copy(security = ConversationSecurity.PARTNER_SETUP, synchronizing = page.hasMore) }
            return page.hasMore
        }
        localMutex.withLock { db.store.call("trust", peerInput(partner)) }
        replenish(stamp, db, devices.currentDevice ?: return false)
        val hasSession = localMutex.withLock { db.store.call("has_session", buildJsonObject {
            put("room_id", db.roomId); put("peer_user_id", partner.userId.toString()); put("peer_device_id", partner.id)
        }).jsonPrimitive.boolean }
        if (!hasSession) {
            val claimKey = "app/claim/${db.roomId}/${native.deviceId}/${partner.id}"
            val claim = localMutex.withLock { db.store.record<String>(claimKey) ?: Uuid.random().toString().also { db.store.put(claimKey, it) } }
            val bundle = request(stamp, db.roomId) { api.claim(db.roomId, ChatBundleClaim(native.deviceId, partner.id, claim)) }
            if (bundle.id != partner.id || bundle.userId != partner.userId || bundle.identityKey != partner.identityKey)
                throw ChatStorageFailure("identity_changed")
            localMutex.withLock { db.store.call("establish", buildJsonObject {
                put("room_id", db.roomId); put("claim_id", claim); put("bundle", ChatJson.encodeToJsonElement(bundle))
            }) }
        }
        localMutex.withLock {
            verifiedSession=verifiedSession(db,native,partner)
            _state.update { it.copy(security = ConversationSecurity.READY, error = null) }
            metadata.activity(db.roomId,native.deviceId,screenVisible && db.preferences().presence)
            // A cancellation after sealing may race a durable acceptance. Keep
            // the original ciphertext retryable, then delete after its acceptance.
            for(entry in db.store.page(db.cancelPrefix,limit=100)) {
                val id=entry.key.removePrefix(db.cancelPrefix)
                val message=db.rawMessage(id) ?: continue
                if(message.sequence!=null) db.enqueue("delete",ChatContent.Delete,id,registration=native,partner=partner,completedMarker=entry.key)
            }
            val pending = db.store.page("app/pending-receipt/${db.roomId}/", limit = 50)
            for (entry in pending) {
                val target = entry.value?.let { db.rawMessage(ChatJson.decodeFromString<String>(it)) } ?: continue
                val kind = entry.key.removePrefix("app/pending-receipt/${db.roomId}/").substringBefore('/')
                db.receipt(target, kind, native, partner)
            }
        }
        val moreSends = flush(stamp, db, native, partner)
        reconcileSparks(stamp,db,native,partner)
        localMutex.withLock { refreshWindow(); _state.update { it.copy(synchronizing = page.hasMore) } }
        return page.hasMore || moreSends
    }

    private suspend fun replenish(stamp: SessionStamp, db: LocalConversation, device: ChatDevice) {
        val key = "app/pending-publication/${device.id}"
        val publication = localMutex.withLock {
            val replenish=(device.prekeysRemaining["ec"] ?: 10)<5 || (device.prekeysRemaining["kyber"] ?: 10)<5
            val pending=db.store.record<String>(key)
            if(!replenish && pending==null && !db.store.call("publication_needed").jsonPrimitive.boolean)return
            val id = db.store.record<String>(key) ?: Uuid.random().toString().also { db.store.put(key, it) }
            val count=if(replenish)minOf(10,100-maxOf(device.prekeysRemaining["ec"] ?: 0,device.prekeysRemaining["kyber"] ?: 0)) else 0
            ChatJson.decodeFromJsonElement<ChatKeyPublication>(db.store.call("publication", buildJsonObject { put("operation_id", id);put("prekey_count",count.coerceAtLeast(0)) }))
        }
        val updated = request(stamp) { api.replenish(publication) }
        localMutex.withLock {
            db.store.write(listOf(ProtectedEntry(key, null)))
            connected = connected?.let { it.copy(devices = it.devices?.copy(currentDevice = updated)) }
        }
        metadata.refresh()
    }

    private suspend fun flush(stamp: SessionStamp, db: LocalConversation, native: ChatRegistration, partner: ChatDevice): Boolean {
        val abandoned=localMutex.withLock {db.store.page("app/cancel-upload/${db.roomId}/",limit=100)}
        for(entry in abandoned) {
            val id=entry.key.substringAfterLast('/')
            try { request(stamp,db.roomId) {api.cancelAttachment(db.roomId,id,native.deviceId)} }
            catch(failure: ChatRequestFailure) {if(failure.failure.status!=404)throw failure}
            localMutex.withLock {db.store.write(listOf(ProtectedEntry(entry.key,null)))}
        }
        val queued = localMutex.withLock { db.queue().take(50) }
        for (original in queued) {
            sessions.ensureCurrent(stamp)
            val operation = localMutex.withLock {
                val current = db.queued(original.id) ?: return@withLock null
                if (current.retryAt > Clock.System.now().toEpochMilliseconds()) return true
                if ((current.senderDeviceId != null && current.senderDeviceId != native.deviceId) ||
                    (current.recipientDeviceId != null && current.recipientDeviceId != partner.id)) {
                    db.failed(current, "device_changed", true, 0); return@withLock null
                }
                current.copy(senderDeviceId = native.deviceId, recipientDeviceId = partner.id).also { db.writeQueue(it);db.sending(it);refreshWindow() }
            } ?: continue
            try {
                uploadAttachments(stamp,db,operation,native)
            } catch (failure: ChatRequestFailure) {
                localMutex.withLock { if(db.queued(operation.id)!=null) db.failed(operation,failure.failure.chatCode ?: failure.failure.code,!failure.failure.chatRetryable(),Clock.System.now().toEpochMilliseconds()+5000);refreshWindow() }
                throw failure
            }
            val wire = localMutex.withLock {
                if(db.queued(operation.id)==null) return@withLock null
                val routing = buildJsonObject {
                    put("device_id", native.deviceId); put("recipient_device_id", partner.id); put("operation_id", operation.id)
                    put("kind", operation.kind); put("target_id", operation.targetId); put("version", operation.version)
                    put("reply_to", operation.replyTo?.let(::JsonPrimitive) ?: JsonNull)
                    put("attachments",ChatJson.encodeToJsonElement((operation.content.content as? ChatContent.Media)?.items?.map { it.descriptor.id }.orEmpty()))
                }
                ChatJson.decodeFromJsonElement<ChatEnvelope>(db.store.call("seal", buildJsonObject {
                    put("room_id", db.roomId); put("peer_user_id", partner.userId.toString()); put("peer_identity", partner.identityKey)
                    put("operation", routing); put("content", ChatJson.encodeToJsonElement(operation.content))
                }))
            } ?: continue
            try {
                val acceptance = request(stamp, db.roomId) { api.send(db.roomId, wire) }
                if (acceptance.operationId != operation.id) throw SerializationException("Wrong acknowledgement")
                localMutex.withLock {
                    db.accepted(operation, acceptance)
                    if(operation.kind=="message" && db.store.read(db.cancelPrefix+operation.id)!=null)
                        db.enqueue("delete",ChatContent.Delete,operation.id,registration=native,partner=partner,completedMarker=db.cancelPrefix+operation.id)
                    cleanInterruptedMedia(db)
                    _state.update { it.copy(uploadProgress=it.uploadProgress-operation.id) }
                }
            } catch (failure: ChatRequestFailure) {
                val code = failure.failure.chatCode ?: failure.failure.code
                val transient = failure.failure.chatRetryable()
                val delay = maxOf((failure.failure.retryAfterSeconds ?: 0) * 1000,
                    min(30000L, 500L shl (operation.attempts + 1).coerceAtMost(6)) + Random.nextLong(250, 1000))
                localMutex.withLock { db.failed(operation, code, !transient, Clock.System.now().toEpochMilliseconds() + delay); refreshWindow() }
                throw failure
            }
        }
        return localMutex.withLock { db.queue().isNotEmpty() }
    }

    private suspend fun uploadAttachments(stamp: SessionStamp, db: LocalConversation, operation: QueuedChatOperation, native: ChatRegistration) {
        val media=operation.content.content as? ChatContent.Media ?: return
        for ((index,item) in media.items.withIndex()) {
            if(localMutex.withLock {db.queued(operation.id)==null})return
            val descriptor=item.descriptor
            val reserved=request(stamp,db.roomId) { api.reserve(db.roomId,AttachmentReservation(native.deviceId,descriptor.id,operation.id,descriptor.ciphertextSize,descriptor.sha256)) }
            if(reserved.state=="pending") {
                if(localMutex.withLock {db.queued(operation.id)==null})return
                val ciphertext=localMutex.withLock { db.store.attachment(ChatJson.encodeToJsonElement(descriptor).jsonObject,false) }
                    ?: throw ChatRequestFailure(ApiError(NetworkAPIResult.ErrorType.BAD_REQUEST,"attachment_unavailable","Select the missing attachment again.",status=400))
                request(stamp,db.roomId) { api.upload(db.roomId,descriptor.id,native.deviceId,ciphertext) { fraction ->
                    if(sessions.stamp()==stamp) _state.update { current -> if(current.roomId==db.roomId) current.copy(uploadProgress=current.uploadProgress+(operation.id to (index+fraction)/media.items.size)) else current }
                } }
            }
            request(stamp,db.roomId) { api.finalize(db.roomId,descriptor.id,native.deviceId) }
        }
    }

    private fun peerInput(peer: ChatDevice) = buildJsonObject { put("peer_user_id", peer.userId.toString()); put("peer_identity", peer.identityKey) }

    private fun validPayload(operation: ChatReceivedOperation, payload: ChatPrivatePayload): Boolean {
        if (payload.version != 1) return false
        return when (val content = payload.content) {
            is ChatContent.Text -> operation.kind == "message" && content.body.isNotBlank() && content.body.length <= 4000 &&
                operation.attachments.isEmpty() && content.quote?.let { it.messageId == operation.replyTo && it.preview.length <= 240 } != false
            is ChatContent.Media -> operation.kind=="message" && content.caption.length<=4000 && content.items.size in 1..3 &&
                content.items.map { it.descriptor.id }==operation.attachments && content.quote?.let { it.messageId==operation.replyTo && it.preview.length<=240 }!=false &&
                content.items.all { item -> item.kind in setOf("photo","video","voice") && item.mime in setOf("image/jpeg","video/mp4","audio/aac","audio/mp4","audio/wav") &&
                    item.descriptor.algorithm=="aes256gcm-v1" && item.descriptor.messageId==operation.targetId && item.descriptor.roomId==local?.roomId &&
                    item.descriptor.ciphertextSize in 29..16*1024*1024+28 && item.descriptor.key.length==44 && item.descriptor.sha256.matches(Regex("[0-9a-f]{64}")) &&
                    item.width in 0..4096 && item.height in 0..4096 && item.durationMs in 0..120000 && item.waveform.size<=100 && item.waveform.all { it in 0..100 } &&
                    (item.thumbnail?.length ?: 0)<=8192 }
            is ChatContent.Reaction -> operation.kind == "reaction" && (content.emoji == null || content.emoji.isNotBlank() && content.emoji.length <= 64)
            is ChatContent.Card -> operation.kind=="message" && operation.attachments.isEmpty() && validCard(content.card)
            is ChatContent.CardResponse -> operation.kind=="card.response" && content.reply.answer.length<=2000 &&
                content.reply.vote?.let {it in 0..3}!=false
            ChatContent.Delete -> operation.kind == "delete"
            ChatContent.Receipt -> operation.kind.startsWith("receipt.")
        }
    }

    private suspend fun storageFailure(failure: ChatStorageFailure) {
        localMutex.withLock { _state.update { it.copy(security = when (failure.code) {
            "identity_changed" -> ConversationSecurity.IDENTITY_CHANGED
            "storage_key_lost" -> ConversationSecurity.KEY_LOST
            else -> ConversationSecurity.STORAGE_ERROR
        }, loading = false, synchronizing = false, error = errorMessage(failure.code)) } }
    }

    private fun action(refresh: Boolean = true, block: (LocalConversation) -> Unit) {
        val action: (LocalConversation) -> Unit = { db -> block(db); if (refresh) refreshWindow() }
        if (actions.trySend(Triple(sessions.stamp(),_state.value.roomId,action)).isFailure)
            _state.update { it.copy(error = "Saving is busy. Please wait and try that change again.") }
    }

    fun updateDraft(value: String) = action(refresh = false) { db -> if (value.length <= 4000) {
        db.store.put(db.draftKey, value); _state.update { it.copy(draft = value) }
        if(_state.value.security==ConversationSecurity.READY)registration?.let {metadata.activity(db.roomId,it.deviceId,db.preferences().presence,value.isNotBlank())}
    } }
    fun prepareAttachments(items: List<PreparedChatMedia>, sendWhenReady: Boolean = false) = action { db ->
        if(_state.value.security in setOf(ConversationSecurity.ARCHIVED,ConversationSecurity.KEY_LOST,ConversationSecurity.STORAGE_ERROR)) return@action
        var draft=db.mediaDraft() ?: MediaDraft(Uuid.random().toString(),emptyList())
        if(items.isEmpty() || items.size+draft.items.size>3) { _state.update {it.copy(error="Choose up to three attachments per message.")};return@action }
        try {
            cleanInterruptedMedia(db)
            for(item in items) {
                if(item.bytes.isEmpty() || item.bytes.size>16*1024*1024 || item.kind !in setOf("photo","video","voice") ||
                    item.mime !in setOf("image/jpeg","video/mp4","audio/aac","audio/mp4","audio/wav") || item.durationMs !in 0..120000 ||
                    (item.thumbnail?.length ?: 0)>8192) {
                    _state.update { it.copy(error="Use a supported photo, video, or voice recording up to 16 MiB and two minutes.") };return@action
                }
                val id=Uuid.random().toString();val marker=db.preparingMediaPrefix+id
                db.store.put(marker,true)
                val descriptor=ChatJson.decodeFromJsonElement<AttachmentDescriptor>(db.store.binary("media_seal",buildJsonObject {
                    put("room_id",db.roomId);put("message_id",draft.messageId);put("id",id)
                },item.bytes))
                val media=ChatMedia(descriptor,item.kind,item.mime,item.width,item.height,item.durationMs,item.thumbnail,item.waveform)
                draft=draft.copy(items=draft.items+media)
                db.store.write(listOf(ProtectedEntry(db.mediaDraftKey,ChatJson.encodeToString(draft)),ProtectedEntry(marker,null)))
            }
            _state.update { it.copy(error=null) }
            if(sendWhenReady) sendDraft(db)
        } catch (failure: ChatStorageFailure) {
            if(failure.code=="storage_key_lost")throw failure
            _state.update { it.copy(error="The attachment could not be saved. Check available storage and try again.") }
        }
    }

    fun removeDraftAttachment(id: String) = action { db ->
        val draft=db.mediaDraft() ?: return@action
        if(draft.items.none {it.descriptor.id==id})return@action
        val remaining=draft.items.filterNot {it.descriptor.id==id}
        val marker=db.preparingMediaPrefix+id
        db.store.write(listOf(ProtectedEntry(db.mediaDraftKey,if(remaining.isEmpty())null else ChatJson.encodeToString(draft.copy(items=remaining))),ProtectedEntry(marker,"true")))
        cleanInterruptedMedia(db)
    }

    fun mediaError(message: String) { _state.update {it.copy(error=message)} }

    fun privateDraft(key: String,value: String) = action(refresh=false) {db ->
        if(!key.matches(Regex("[a-z0-9:-]{1,90}")) || value.length>4096)return@action
        val path="app/form/${db.roomId}/$key"
        if(value.isEmpty())db.store.write(listOf(ProtectedEntry(path,null)))
        else {
            if(db.store.read(path)==null && db.store.count("app/form/${db.roomId}/")>=100)throw ChatQueueFull()
            db.store.put(path,value)
        }
        _state.update {it.copy(privateDrafts=if(value.isEmpty())it.privateDrafts-key else it.privateDrafts+(key to value))}
    }

    /** Save the exact consented Plan request before contacting its domain API.
     * A lost acknowledgement or process restart reuses the same request/body. */
    suspend fun planIntent(title: String,date: String): JsonObject? {
        val stamp=sessions.stamp();val room=_state.value.roomId
        return withContext(Dispatchers.Default) {localMutex.withLock {
            sessions.ensureCurrent(stamp)
            val db=local?.takeIf {it.roomId==room} ?: return@withLock null
            val key="app/form/${db.roomId}/planner:request"
            db.store.record<String>(key)?.let {return@withLock ChatJson.parseToJsonElement(it).jsonObject}
            val body=buildJsonObject {put("request_id",Uuid.random().toString());put("title",title.trim());put("starts_at",date);put("share_to_legacy_chat",false)}
            db.store.put(key,body.toString())
            _state.update {it.copy(privateDrafts=it.privateDrafts+("planner:request" to body.toString()))}
            body
        }}
    }

    private fun validCard(card: ChatCard): Boolean = when(card) {
        is ChatCard.Ask -> card.question.isNotBlank() && card.question.length<=500 && (card.options.isEmpty() || card.options.size in 2..4) &&
            card.options.all {it.isNotBlank() && it.length<=120} && card.options.distinct().size==card.options.size
        is ChatCard.Spark -> SparkDecks.questions[card.deck]?.contains(card.question)==true
        is ChatCard.Plan -> card.coupleId>0 && card.planId>0
        is ChatCard.Moment -> card.momentId>0 && runCatching {kotlin.time.Instant.parse(card.expiresAt)}.isSuccess
        is ChatCard.Challenge -> card.sourceId.isNotBlank() && card.sourceId.length<=100
    }
    fun sendCard(card: ChatCard,form: String? = null) = action {db ->
        if(!validCard(card) || _state.value.security in setOf(ConversationSecurity.ARCHIVED,ConversationSecurity.KEY_LOST,ConversationSecurity.STORAGE_ERROR))return@action
        val completed=if(form in setOf("ask","spark","planner","moment"))db.store.page("app/form/${db.roomId}/$form:",limit=100).map {it.key} else emptyList()
        val operation=db.enqueue("message",ChatContent.Card(card),registration=registration,partner=connected?.devices?.partnerDevice,clearComposer=false,completedMarkers=completed)
        if(card is ChatCard.Spark)db.store.put("app/spark-watch/${db.roomId}/${operation.id}",true)
        _state.update {it.copy(atLatest=true)};work.trySend(Unit)
    }
    fun answerCard(id: String,reply: CardReply) = action {db ->
        val message=db.message(id) ?: return@action
        if(message.deleted || message.hidden || message.unavailable || message.authorId==db.accountId)return@action
        val card=(message.content as? ChatContent.Card)?.card as? ChatCard.Ask ?: return@action
        if(!validAskReply(card,reply))return@action
        db.enqueue("card.response",ChatContent.CardResponse(reply),id,registration=registration,partner=connected?.devices?.partnerDevice,clearComposer=false,
            completedMarker="app/form/${db.roomId}/answer:$id")
        work.trySend(Unit)
    }
    fun answerSpark(id: String,answer: String) = action {db ->
        val message=db.message(id) ?: return@action
        if(message.deleted || message.hidden || message.unavailable || (message.content as? ChatContent.Card)?.card !is ChatCard.Spark || answer.isBlank() || answer.length>2000)return@action
        val key="app/spark-answer/${db.roomId}/$id"
        if(db.store.read(key)!=null || db.store.record<LocalSparkState>("app/spark/${db.roomId}/$id")?.submitted==true)return@action
        db.store.write(listOf(ProtectedEntry(key,ChatJson.encodeToString(PendingSparkAnswer(Uuid.random().toString(),answer))),
            ProtectedEntry("app/spark-watch/${db.roomId}/$id","true"),ProtectedEntry("app/form/${db.roomId}/answer:$id",null)))
        work.trySend(Unit)
    }

    private suspend fun reconcileSparks(stamp: SessionStamp,db: LocalConversation,native: ChatRegistration,partner: ChatDevice) {
        val ids=localMutex.withLock {
            (db.store.page("app/spark-watch/${db.roomId}/",limit=10).map {it.key.substringAfterLast('/')}+
                _state.value.messages.filter {(it.content as? ChatContent.Card)?.card is ChatCard.Spark}.map {it.id}).distinct().take(10)
        }
        for(id in ids) {
            val message=localMutex.withLock {db.message(id)} ?: continue
            val watch="app/spark-watch/${db.roomId}/$id";val stateKey="app/spark/${db.roomId}/$id";val answerKey="app/spark-answer/${db.roomId}/$id"
            if(message.hidden || message.deleted || message.unavailable) {localMutex.withLock {db.store.write(listOf(ProtectedEntry(watch,null),ProtectedEntry(stateKey,null),ProtectedEntry(answerKey,null)))};continue}
            if(message.sequence==null)continue
            if(localMutex.withLock {db.store.record<LocalSparkState>(stateKey)?.revealed==true})continue
            try {
                var status=request(stamp,db.roomId) {api.spark(db.roomId,id,native.deviceId,message.authorId==db.accountId)}
                val pending=localMutex.withLock {db.store.record<PendingSparkAnswer>(answerKey)}
                if(pending!=null && !status.submitted) {
                    val hasSession=localMutex.withLock {db.store.call("has_session",buildJsonObject {put("room_id",id);put("peer_user_id",partner.userId.toString());put("peer_device_id",partner.id)}).jsonPrimitive.boolean}
                    if(!hasSession) {
                        val claim=request(stamp,db.roomId) {api.claim(db.roomId,ChatBundleClaim(native.deviceId,partner.id,pending.operationId))}
                        if(claim.id!=partner.id || claim.identityKey!=partner.identityKey)throw ChatStorageFailure("identity_changed")
                        localMutex.withLock {db.store.call("establish",buildJsonObject {put("room_id",id);put("claim_id",pending.operationId);put("bundle",ChatJson.encodeToJsonElement(claim))})}
                    }
                    val wire=localMutex.withLock {ChatJson.decodeFromJsonElement<ChatEnvelope>(db.store.call("seal",buildJsonObject {
                        put("room_id",id);put("peer_user_id",partner.userId.toString());put("peer_identity",partner.identityKey)
                        put("operation",buildJsonObject {put("device_id",native.deviceId);put("recipient_device_id",partner.id);put("operation_id",pending.operationId)
                            put("target_id",id);put("kind","card.response");put("version",1);put("reply_to",JsonNull)})
                        put("content",buildJsonObject {put("version",1);put("spark_id",id);put("answer",pending.answer)})
                    }))}
                    status=request(stamp,db.roomId) {api.answerSpark(db.roomId,id,wire)}
                }
                if(status.id!=id || (status.revealed && status.answers.size!=2) || (!status.revealed && status.answers.isNotEmpty()))throw SerializationException("Invalid Spark gate")
                localMutex.withLock {
                    sessions.ensureCurrent(stamp);if(local!==db)throw ConversationChanged()
                    val old=db.store.record<LocalSparkState>(stateKey) ?: LocalSparkState()
                    val own=if(status.submitted)pending?.answer ?: old.answers[db.accountId] else null
                    var answers=if(own!=null)mapOf(db.accountId to own) else emptyMap()
                    val entries=mutableListOf<ProtectedEntry>()
                    if(status.revealed)for(operation in status.answers) {
                        if(operation.actorId==db.accountId)continue
                        if(operation.actorId!=partner.userId || operation.senderDeviceId!=partner.id || operation.recipientDeviceId!=native.deviceId ||
                            operation.kind!="card.response" || operation.targetId!=id || operation.version!=1L)throw SerializationException("Invalid Spark sender")
                        val opened=db.store.call("open",buildJsonObject {put("room_id",id);put("peer_user_id",partner.userId.toString());put("peer_identity",partner.identityKey)
                            put("operation",ChatJson.encodeToJsonElement(operation))}).jsonObject
                        val answer=opened["answer"]?.jsonPrimitive?.content ?: throw SerializationException("Missing answer")
                        if(opened["version"]?.jsonPrimitive?.intOrNull!=1 || opened["spark_id"]?.jsonPrimitive?.content!=id || answer.isBlank() || answer.length>2000)throw SerializationException("Invalid answer")
                        answers=answers+(partner.userId to answer)
                        entries+=ProtectedEntry("app/processed/$id/${operation.operationId}","true")
                    }
                    entries+=ProtectedEntry(stateKey,ChatJson.encodeToString(LocalSparkState(status.submitted,status.partnerSubmitted,status.revealed,answers)))
                    if(status.submitted)entries+=ProtectedEntry(answerKey,null)
                    if(status.revealed)entries+=ProtectedEntry(watch,null) else entries+=ProtectedEntry(watch,"true")
                    db.store.write(entries)
                }
            } catch(cancelled: CancellationException) {throw cancelled}
            catch(failure: ChatRequestFailure) {
                if(failure.failure.chatRetryable())throw failure
                localMutex.withLock {val old=db.store.record<LocalSparkState>(stateKey) ?: LocalSparkState()
                    db.store.put(stateKey,old.copy(error=if(failure.failure.status==404)"This Spark is not available yet. Try again." else "This Spark cannot continue. Try again or start a new one."))}
            }
            catch(failure: ChatStorageFailure) {
                if(failure.code !in setOf("invalid_ciphertext","replay_rejected","invalid_payload","invalid_envelope","operation_conflict"))throw failure
                sparkUnavailable(db,stateKey)
            }
            catch(_: SerializationException) {sparkUnavailable(db,stateKey)}
        }
    }

    private suspend fun sparkUnavailable(db: LocalConversation,key: String) = localMutex.withLock {
        val old=db.store.record<LocalSparkState>(key) ?: LocalSparkState()
        db.store.put(key,old.copy(error="This Spark answer could not be verified. Start a new Spark.",revealed=false))
    }

    fun openAttachment(messageId: String,id: String) {
        val stamp=sessions.stamp()
        val generation=++mediaGeneration
        mediaJob?.cancel()
        mediaJob=scope.launch {
            try {
                val captured=localMutex.withLock {
                    val db=local ?: return@launch
                    val message=db.message(messageId) ?: return@launch
                    if(message.deleted || message.hidden || message.unavailable)return@launch
                    val item=(message.content as? ChatContent.Media)?.items?.firstOrNull {it.descriptor.id==id} ?: return@launch
                    _state.update {it.copy(mediaLoading=true,error=null)}
                    db to item
                }
                val (db,item)=captured
                val descriptor=ChatJson.encodeToJsonElement(item.descriptor).jsonObject
                var bytes=localMutex.withLock {db.store.attachment(descriptor,true)}
                if(bytes==null) {
                    val device=localMutex.withLock { registration?.deviceId } ?: return@launch
                    val capability=request(stamp,db.roomId) {api.mediaCapability(db.roomId,id,device)}
                    val ciphertext=request(stamp,db.roomId) {api.download(db.roomId,item.descriptor,capability.capability)}
                    localMutex.withLock {
                        sessions.ensureCurrent(stamp)
                        if(local!==db || db.message(messageId)?.let {it.deleted || it.hidden}!=false)throw ConversationChanged()
                        db.store.binary("media_cache",descriptor,ciphertext)
                        bytes=db.store.attachment(descriptor,true)
                    }
                }
                localMutex.withLock {
                    sessions.ensureCurrent(stamp)
                    if(generation==mediaGeneration && local===db && db.message(messageId)?.let {it.deleted || it.hidden}==false) {
                        _state.update {it.copy(openedMedia=bytes?.let {data -> OpenedChatMedia(messageId,item,data)},
                            offlineMedia=if(bytes!=null)it.offlineMedia+item.descriptor.id else it.offlineMedia-item.descriptor.id,
                            error=if(bytes==null)"The attachment could not be verified." else null)}
                    }
                }
            } catch (cancelled: CancellationException) {throw cancelled}
            catch (_: Exception) {if(generation==mediaGeneration && sessions.stamp()==stamp)_state.update {it.copy(error="This attachment is unavailable. Reconnect and try again.")}}
            finally {if(generation==mediaGeneration && sessions.stamp()==stamp)_state.update {it.copy(mediaLoading=false)}}
        }
    }
    fun openDraftAttachment(id: String) = action(refresh=false) { db ->
        val draft=db.mediaDraft() ?: return@action
        val item=draft.items.firstOrNull {it.descriptor.id==id} ?: return@action
        val bytes=db.store.attachment(ChatJson.encodeToJsonElement(item.descriptor).jsonObject,true)
        _state.update {it.copy(openedMedia=bytes?.let {OpenedChatMedia(draft.messageId,item,it)},
            error=if(bytes==null)"This attachment is no longer saved. Select it again." else null)}
    }
    fun dismissMedia() { mediaGeneration++;mediaJob?.cancel();mediaJob=null; _state.update {it.copy(openedMedia=null,mediaLoading=false)} }
    fun voicePlayed(messageId: String) = action(refresh=false) { db ->
        if(!db.preferences().readReceipts || _state.value.security!=ConversationSecurity.READY)return@action
        val message=db.message(messageId) ?: return@action
        val device=registration ?: return@action
        val peer=connected?.devices?.partnerDevice ?: return@action
        db.receipt(message,"receipt.played",device,peer);work.trySend(Unit)
    }
    fun setReply(id: String?) = action { db ->
        val target = id?.let(db::message)
        _state.update { it.copy(reply = target?.takeUnless { it.deleted || it.hidden || it.unavailable }?.let {
            ChatQuote(it.id, it.authorId, when(val content=it.content) {
                is ChatContent.Text -> content.body.take(240)
                is ChatContent.Media -> content.caption.ifEmpty {content.items.firstOrNull()?.kind ?: "Attachment"}.take(240)
                else -> "Message"
            }) }) }
        val quote=_state.value.reply
        if(quote==null)db.store.write(listOf(ProtectedEntry(db.replyKey,null))) else db.store.put(db.replyKey,quote)
    }
    fun sendText() = action { db -> sendDraft(db) }
    private fun sendDraft(db: LocalConversation) {
        if (_state.value.security in setOf(ConversationSecurity.ARCHIVED, ConversationSecurity.IDENTITY_CHANGED, ConversationSecurity.REPLACE_DEVICE)) return
        val body = db.draft().trim()
        val media = db.mediaDraft()
        if ((body.isEmpty() && media==null) || body.length > 4000) return
        val quote = _state.value.reply
        db.enqueue("message", if(media==null) ChatContent.Text(body,quote) else ChatContent.Media(media.items,body,quote),
            replyTo = quote?.messageId, registration = registration, partner = connected?.devices?.partnerDevice,messageId=media?.messageId)
        _state.update { it.copy(reply = null, draft = "", atLatest = true) }
        registration?.let {metadata.activity(db.roomId,it.deviceId,db.preferences().presence,false)}
        work.trySend(Unit)
    }
    fun react(id: String, emoji: String?) = action { db ->
        if (_state.value.security != ConversationSecurity.READY || (emoji != null && (emoji.isBlank() || emoji.length > 64))) return@action
        val target = db.rawMessage(id) ?: return@action
        if (target.deleted || target.hidden || target.unavailable) return@action
        db.enqueue("reaction", ChatContent.Reaction(emoji), id, registration = registration, partner = connected?.devices?.partnerDevice)
        work.trySend(Unit)
    }
    fun deleteForEveryone(id: String) = action { db ->
        val target = db.rawMessage(id) ?: return@action
        if (target.authorId != db.accountId || target.deleted || target.sequence == null || _state.value.security != ConversationSecurity.READY) return@action
        db.enqueue("delete", ChatContent.Delete, id, registration = registration, partner = connected?.devices?.partnerDevice)
        work.trySend(Unit)
    }
    fun deleteForMe(id: String) = action { it.hide(id) }
    fun cancelSend(id: String) = action { db ->
        val queued=db.queued(id) ?: return@action
        if(queued.kind!="message")return@action
        val sealed=db.store.call("is_sealed",buildJsonObject {put("room_id",db.roomId);put("operation_id",id)}).jsonPrimitive.boolean
        if(sealed) {
            db.store.put(db.cancelPrefix+id,true);db.hide(id)
            _state.update {it.copy(error="Cancellation saved. If this message reached your partner, it will be deleted when chat reconnects.")}
        } else {
            db.cancelUnsealed(queued)
            cleanInterruptedMedia(db)
        }
        _state.update {it.copy(uploadProgress=it.uploadProgress-id)}
        work.trySend(Unit)
    }
    fun markVisible(ids: List<String>) = action(refresh = false) { db ->
        if(!screenVisible)return@action
        db.store.write(ids.take(50).map {ProtectedEntry(db.unreadPrefix+it,null)})
        _state.update {it.copy(unreadCount=db.store.count(db.unreadPrefix))}
        if (!db.preferences().readReceipts || _state.value.security != ConversationSecurity.READY) return@action
        val native = registration ?: return@action
        val partner = connected?.devices?.partnerDevice ?: return@action
        ids.take(50).mapNotNull(db::message).filter { !it.hidden && !it.unavailable }.forEach { db.receipt(it, "receipt.read", native, partner) }
        work.trySend(Unit)
    }
    fun preferences(value: ConversationPreferences) = action { db ->
        db.store.put(db.preferencesKey, value.copy(nickname = value.nickname.trim().take(60)))
        if(!value.readReceipts) {
            for(operation in db.queue().filter {it.kind in setOf("receipt.read","receipt.played")}) {
                val sealed=db.store.call("is_sealed",buildJsonObject {put("room_id",db.roomId);put("operation_id",operation.id)}).jsonPrimitive.boolean
                if(!sealed)db.dropUnsealedReceipt(operation)
            }
            for(kind in listOf("receipt.read","receipt.played"))
                db.store.write(db.store.page("app/pending-receipt/${db.roomId}/$kind/",limit=100).map {ProtectedEntry(it.key,null)})
        }
        registration?.let {metadata.activity(db.roomId,it.deviceId,value.presence,false)}
    }
    fun older() = action { db ->
        val before = _state.value.messages.firstOrNull()?.timelineKey.orEmpty()
        val (older, more) = db.page(before)
        _state.update { it.copy(messages = (older + it.messages).distinctBy(LocalChatMessage::id).take(300), atLatest = false, hasOlder = more) }
    }
    fun latest() = action {db ->
        db.store.write(listOf(ProtectedEntry("app/anchor/${db.roomId}",null)))
        _state.update { it.copy(atLatest = true, newMessages = 0,readingAnchor=null) }
    }
    fun readingPosition(id: String,offset: Int,older: Boolean) = action(refresh=false) {db ->
        if(!screenVisible || _state.value.messages.none {it.id==id})return@action
        val isOlder=older || db.page(limit=1).first.lastOrNull()?.id!=_state.value.messages.lastOrNull()?.id
        if(isOlder)db.store.put("app/anchor/${db.roomId}",ChatReadingAnchor(id,offset.coerceAtLeast(0),_state.value.messages.last().id))
        else db.store.write(listOf(ProtectedEntry("app/anchor/${db.roomId}",null)))
        _state.update {it.copy(atLatest=!isOlder,newMessages=if(isOlder)it.newMessages else 0)}
    }
    fun showSafetyCode() = action { db ->
        val partner = connected?.devices?.partnerDevice ?: return@action
        val result = db.store.call("fingerprint", peerInput(partner)).jsonObject
        safetyIdentity=partner.identityKey
        _state.update { it.copy(safetyCode = result["display"]?.jsonPrimitive?.content,
            safetyQr=result["qr"]?.jsonPrimitive?.content,safetyVerified=null) }
    }
    fun verifySafetyQr(value: String) = action {db ->
        val partner=connected?.devices?.partnerDevice ?: return@action
        if(_state.value.safetyCode==null || safetyIdentity!=partner.identityKey)return@action
        val prefix="srisu-safety:1:"
        val matches=if(value.startsWith(prefix) && value.length<=1024) db.store.call("fingerprint",buildJsonObject {
            peerInput(partner).forEach {(key,item)->put(key,item)};put("scan",value.removePrefix(prefix))
        }).jsonObject["matches"]?.jsonPrimitive?.boolean==true else false
        _state.update {it.copy(safetyVerified=matches)}
    }
    fun dismissSafetyCode() { safetyIdentity=null; _state.update { it.copy(safetyCode = null,safetyQr=null,safetyVerified=null) } }
    fun confirmIdentityChange() = action { db ->
        val partner = connected?.devices?.partnerDevice ?: return@action
        if(safetyIdentity!=partner.identityKey || _state.value.safetyVerified==false) {
            _state.update {it.copy(error="The safety code changed or did not match. Open verification again.")};return@action
        }
        db.store.call("trust", buildJsonObject { peerInput(partner).forEach { (key, value) -> put(key, value) }; put("confirm_change", true) })
        dismissSafetyCode()
        _state.update { it.copy(security = ConversationSecurity.SETTING_UP) }; work.trySend(Unit)
    }
    fun confirmDeviceReplacement() = action { db ->
        val current = connected?.devices?.currentDevice ?: return@action
        db.store.put("app/replacement-authorized", current.id)
        _state.update { it.copy(security = ConversationSecurity.SETTING_UP) }; work.trySend(Unit)
    }

    fun retryMessage(id: String) = action { db ->
        val pending = db.queued(id)
        if (pending != null) db.writeQueue(pending.copy(retryAt = 0, failure = null))
        else {
            val failed = db.store.record<QueuedChatOperation>("app/failed-operation/${db.roomId}/$id") ?: return@action
            if (_state.value.security != ConversationSecurity.READY || failed.kind != "message") return@action
            val messageId=Uuid.random().toString()
            val content=failed.content.content
            val markers=mutableListOf<String>()
            val retryContent=if(content is ChatContent.Media) {
                cleanInterruptedMedia(db)
                val prepared=mutableListOf<ChatMedia>()
                for(item in content.items) {
                    val bytes=db.store.attachment(ChatJson.encodeToJsonElement(item.descriptor).jsonObject,true)
                    if(bytes==null) { _state.update {it.copy(error="The original attachment is unavailable. Select it again.")};return@action }
                    val fileId=Uuid.random().toString();val marker=db.preparingMediaPrefix+fileId
                    db.store.put(marker,true);markers+=marker
                    val descriptor=ChatJson.decodeFromJsonElement<AttachmentDescriptor>(db.store.binary("media_seal",buildJsonObject {
                        put("room_id",db.roomId);put("message_id",messageId);put("id",fileId)
                    },bytes))
                    prepared+=item.copy(descriptor=descriptor)
                }
                content.copy(items=prepared)
            } else content
            db.enqueue("message", retryContent, replyTo = failed.replyTo, registration = registration, partner = connected?.devices?.partnerDevice,
                messageId=messageId,clearComposer=false,completedMarkers=markers)
            db.hide(id)
            db.store.write(listOf(ProtectedEntry("app/failed-operation/${db.roomId}/$id",null)))
        }
        work.trySend(Unit)
    }

    fun reconcileHistory() = action { db -> db.store.put(db.cursorKey, 0L); work.trySend(Unit) }

    /** Called only by the explicit local-history-loss confirmation in the UI. */
    fun resetSecureStorage() {
        val stamp = sessions.stamp()
        val account = stamp.accountId ?: return
        scope.launch {
            try {
                localMutex.withLock {
                    sessions.ensureCurrent(stamp)
                    ProtectedChatStore(driver, account).call("reset", buildJsonObject { put("confirm_history_loss", true) })
                    resetEpoch.value += 1
                }
                metadata.refresh()
            } catch (failure: ChatStorageFailure) { storageFailure(failure) }
        }
    }

    fun jumpToMessage(id: String,reportUnavailable:Boolean=true) = action { db ->
        val target = db.message(id)
        if (target == null || target.hidden) {
            if(reportUnavailable)_state.update { it.copy(error = "The original message is unavailable.") }
            return@action
        }
        // Seek by the indexed ordering key, without reading/decrypting intervening history.
        val (page, older) = db.page(target.timelineKey + "z", 50)
        _state.update { it.copy(messages = page, atLatest = false, hasOlder = older) }
    }

    private fun errorMessage(code: String): String = when (code) {
        "identity_changed" -> "Your partner’s security identity changed. Compare safety codes before continuing."
        "storage_key_lost" -> "This device’s protected storage key is missing. Existing local history cannot be recovered."
        "prekeys_exhausted" -> "Your partner needs to open SriSu to refresh secure setup. Your message stays queued."
        "recipient_backlog_full" -> "Your partner needs to receive queued messages before more can be sent."
        "partner_device_changed", "device_changed", "device_revoked" -> "The chat device changed. Review secure setup before sending again."
        "cursor_ahead" -> "The saved synchronization cursor is ahead of the server. History reconciliation is required."
        "network", "timeout", "temporarily_unavailable" -> "Waiting for a connection. Your messages are saved on this device."
        "rate_limited" -> "Sending is temporarily limited. Queued messages will retry."
        else -> "Chat could not complete this operation. Your saved conversation is still protected."
    }
}
