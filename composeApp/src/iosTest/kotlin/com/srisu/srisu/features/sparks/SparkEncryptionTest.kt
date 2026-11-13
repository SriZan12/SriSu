package com.srisu.srisu.features.sparks

import com.srisu.srisu.features.chat.matrix.encryptSpark
import com.srisu.srisu.features.chat.matrix.decryptSpark
import com.srisu.srisu.features.sparks.data.SparkAnswer
import de.connect2x.trixnity.client.room.RoomEventEncryptionService
import de.connect2x.trixnity.core.UserInfo
import de.connect2x.trixnity.core.model.*
import de.connect2x.trixnity.core.model.keys.*
import de.connect2x.trixnity.core.model.events.*
import de.connect2x.trixnity.core.model.events.m.room.*
import de.connect2x.trixnity.core.serialization.createMatrixEventJson
import de.connect2x.trixnity.crypto.olm.*
import de.connect2x.trixnity.crypto.key.DeviceTrustLevel
import de.connect2x.trixnity.crypto.driver.CryptoDriver
import de.connect2x.trixnity.client.CryptoDriverModule
import de.connect2x.trixnity.client.cryptodriver.vodozemac.vodozemac
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Instant

/** Real pinned SDK Megolm/vodozemac encryption on iOS; in-memory stores and transport.
 * Key delivery is injected here. Live Olm/Synapse delivery is separate coverage.
 */
class SparkEncryptionTest {
    @Test fun sdkRoundTripHasNoPlaintextAndRejectsTamperingReplayAndWrongSender() = runBlocking {
        val cryptoModule = CryptoDriverModule.vodozemac().create()
        val cryptoDi = koinApplication { modules(cryptoModule) }.koin
        val driver = cryptoDi.get<CryptoDriver>()
        val user = UserId("@one:test.invalid")
        val account = driver.olm.account()
        val info = UserInfo(user, "SPARKTEST", Key.Ed25519Key("SPARKTEST", account.ed25519Key.base64), Key.Curve25519Key("SPARKTEST", account.curve25519Key.base64))
        val sender = TestStore(info)
        val recipient = TestStore(info)
        val senderClient = services(sender, info, driver)
        val recipientClient = services(recipient, info, driver)
        try {
            val session = SparkFixtures.session
            val text = "Synthetic answer: the quiet cup of tea."
            val envelope = encryptSpark(senderClient, session, user.full, text)
            assertEquals("m.megolm.v1.aes-sha2", envelope["algorithm"]!!.jsonPrimitive.content)
            assertFalse(envelope.toString().contains(text))
            assertEquals(setOf("algorithm", "ciphertext", "session_id", "sender_key", "device_id"), envelope.keys)
            // Simulate the SDK's authenticated key-delivery boundary, retaining the
            // exact native session. No fake encryption is used on this path.
            recipient.inbound.putAll(sender.inbound)
            val answer = SparkAnswer(1, user.full, envelope)
            assertEquals(text, decryptSpark(recipientClient, recipient, session, answer))
            assertEquals(text, decryptSpark(recipientClient, recipient, session, answer)) // exact retry
            assertTrue(runCatching { decryptSpark(recipientClient, recipient, session.copy(id="33333333-3333-4333-8333-333333333333"), answer) }.isFailure)
            assertTrue(runCatching { decryptSpark(recipientClient, recipient, session, answer.copy(sender="@attacker:test.invalid")) }.isFailure)
            assertTrue(runCatching { decryptSpark(recipientClient, recipient, session.copy(question="Another question"), answer) }.isFailure)
            val corrupt = JsonObject(envelope + ("ciphertext" to JsonPrimitive(envelope["ciphertext"]!!.jsonPrimitive.content.reversed())))
            assertTrue(runCatching { decryptSpark(recipientClient, recipient, session, answer.copy(envelope=corrupt)) }.isFailure)
            recipient.inbound.clear()
            assertTrue(runCatching { decryptSpark(recipientClient, recipient, session, answer) }.isFailure)
        } finally { account.close(); cryptoDi.close() }
    }

    private fun services(store: TestStore, info: UserInfo, driver: CryptoDriver): List<RoomEventEncryptionService> {
        val unusedOlm = object : OlmEncryptionService {
            override suspend fun encryptOlm(content: EventContent, recipientUserId: UserId, recipientDeviceId: String): Result<de.connect2x.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent> = error("Unexpected transport")
            override suspend fun encryptOlm(content: EventContent, recipients: Set<Pair<UserId, String>>): Map<Pair<UserId, String>, Result<de.connect2x.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent>> = error("Unexpected transport")
            override suspend fun recoverOlm(olmRecovery: OlmEncryptionService.OlmRecovery): Result<de.connect2x.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent?> = error("Unexpected transport")
            override suspend fun decryptOlm(event: ClientEvent.ToDeviceEvent<de.connect2x.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent>): Result<PlaintextOlmEvent<*>> = error("Unexpected transport")
        }
        val service = MegolmEncryptionServiceImpl(info, createMatrixEventJson(), store, object : MegolmEncryptionServiceRequestHandler {
            override suspend fun sendToDevice(events: Map<UserId, Map<String, ToDeviceEventContent>>, transactionId: String) = Result.success(Unit)
        }, unusedOlm, Clock.System, driver)
        val adapter = object : RoomEventEncryptionService {
            override suspend fun encrypt(content: MessageEventContent, roomId: RoomId) = service.encryptMegolm(content, roomId, EncryptionEventContent(algorithm = EncryptionAlgorithm.Megolm))
            override suspend fun decrypt(event: ClientEvent.RoomEvent.MessageEvent<*>): Result<MessageEventContent>? {
                @Suppress("UNCHECKED_CAST")
                return service.decryptMegolm(event as ClientEvent.RoomEvent<EncryptedMessageEventContent.MegolmEncryptedMessageEventContent>).map { it.content as MessageEventContent }
            }
        }
        return listOf(adapter)
    }
}

private class TestStore(private val info: UserInfo) : OlmStore {
    val inbound = mutableMapOf<String, StoredInboundMegolmSession>()
    private var outbound: StoredOutboundMegolmSession? = null
    private val indexes = mutableMapOf<Long, StoredInboundMegolmMessageIndex>()
    override suspend fun getDeviceKeys(userId: UserId): Map<String, SignedDeviceKeys>? = if(userId != info.userId) null else mapOf(info.deviceId to Signed(DeviceKeys(info.userId,info.deviceId,setOf(EncryptionAlgorithm.Megolm),keysOf(info.identityPublicKey,info.signingPublicKey))))
    override suspend fun getMembers(roomId: RoomId,memberships:Set<Membership>):Set<UserId> = emptySet()
    override suspend fun getTrustLevel(userId:UserId,deviceId:String):DeviceTrustLevel? = null
    override suspend fun getOlmSessions(identityKeyValue:KeyValue.Curve25519KeyValue):Set<StoredOlmSession>? = null
    override suspend fun updateOlmSessions(identityKeyValue:KeyValue.Curve25519KeyValue,updater:(Set<StoredOlmSession>?)->Set<StoredOlmSession>?) = Unit
    override suspend fun getOutboundMegolmSession(roomId:RoomId) = outbound
    override suspend fun updateOutboundMegolmSession(roomId:RoomId,updater:(StoredOutboundMegolmSession?)->StoredOutboundMegolmSession?) { outbound=updater(outbound) }
    override suspend fun updateInboundMegolmSession(sessionId:String,roomId:RoomId,updater:(StoredInboundMegolmSession?)->StoredInboundMegolmSession?) { updater(inbound[sessionId])?.let {inbound[sessionId]=it} }
    override suspend fun getInboundMegolmSession(sessionId:String,roomId:RoomId) = inbound[sessionId]?.takeIf {it.roomId==roomId}
    override suspend fun updateInboundMegolmMessageIndex(sessionId:String,roomId:RoomId,messageIndex:Long,updater:(StoredInboundMegolmMessageIndex?)->StoredInboundMegolmMessageIndex?) { updater(indexes[messageIndex])?.let {indexes[messageIndex]=it} }
    override suspend fun getOlmAccount():String = error("unused")
    override suspend fun updateOlmAccount(updater:(String)->String) = Unit
    override suspend fun getOlmPickleKey():String? = null
    override suspend fun getForgetFallbackKeyAfter():Instant? = null
    override suspend fun updateForgetFallbackKeyAfter(updater:(Instant?)->Instant?) = Unit
    override suspend fun getHistoryVisibility(roomId:RoomId) = HistoryVisibilityEventContent.HistoryVisibility.JOINED
    override suspend fun getRoomEncryptionAlgorithm(roomId:RoomId) = EncryptionAlgorithm.Megolm
}
