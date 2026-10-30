package com.srisu.srisu.features.chat.matrix

import com.srisu.srisu.features.sparks.data.*
import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.client.room.RoomEventEncryptionService
import de.connect2x.trixnity.client.room.encrypt
import de.connect2x.trixnity.client.room.decrypt
import de.connect2x.trixnity.core.model.*
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import de.connect2x.trixnity.core.model.events.m.room.EncryptedMessageEventContent.MegolmEncryptedMessageEventContent
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import de.connect2x.trixnity.crypto.olm.OlmStore
import de.connect2x.trixnity.crypto.key.DeviceTrustLevel
import kotlinx.serialization.json.*

private val sparkJson = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = false }

/** Uses the chat SDK's room sessions, ratchets, key sharing and replay protection.
 * This ciphertext is NOT posted to the Matrix timeline before mutual reveal.
 */
internal suspend fun encryptSpark(services: List<RoomEventEncryptionService>, session: SparkSession, sender: String, answer: String): JsonObject {
    require(answer.isNotBlank() && answer.length <= SPARK_ANSWER_LIMIT)
    val body = sparkJson.encodeToString(SparkPlaintext(spark = session.id, couple = session.couple,
        epoch = session.epoch, questionId = session.questionId, question = session.question, sender = sender, answer = answer))
    val encrypted = services.encrypt(
        RoomMessageEventContent.TextBased.Text(body = body), RoomId(session.matrixRoomId))?.getOrThrow()
    require(encrypted is MegolmEncryptedMessageEventContent) { "Secure answer encryption is unavailable." }
    return sparkJson.encodeToJsonElement(encrypted).jsonObject
}

internal suspend fun decryptSpark(services: List<RoomEventEncryptionService>, store: OlmStore, session: SparkSession, answer: SparkAnswer): String {
    val encrypted = sparkJson.decodeFromJsonElement<MegolmEncryptedMessageEventContent>(answer.envelope)
    // A fixed, non-timeline identity makes retries use exactly the same SDK replay-cache entry.
    val event = MessageEvent(encrypted, EventId("\$srisu.spark.${session.id}.${answer.author}"),
        UserId(answer.sender), RoomId(session.matrixRoomId), 0)
    val content = services.decrypt(event)?.getOrThrow()
    require(content is RoomMessageEventContent.TextBased.Text)
    val inbound = requireNotNull(store.getInboundMegolmSession(encrypted.sessionId, event.roomId))
    val device = store.getDeviceKeys(event.sender)?.values?.firstOrNull { key ->
        key.signed.userId == event.sender && key.signed.keys.keys.any { it.value == inbound.senderKey } &&
            key.signed.keys.keys.any { it.value == inbound.senderSigningKey }
    }
    // A server-supplied sender label alone is never proof of authorship.
    require(inbound.isTrusted && device != null) { "The answer's device keys are not available yet." }
    val trust = store.getTrustLevel(event.sender, device.signed.deviceId)
    require(trust !is DeviceTrustLevel.Invalid && trust != DeviceTrustLevel.Blocked && trust != DeviceTrustLevel.NotTrusted)
    return sparkJson.decodeFromString<SparkPlaintext>(content.body).validated(session, answer.sender)
}

internal suspend fun sendSparkCard(client: MatrixClient, session: SparkSession): String {
    val reference = SparkReference(session.id, session.couple)
    val encrypted = client.di.getAll<RoomEventEncryptionService>().encrypt(
        RoomMessageEventContent.TextBased.Text(body = session.question, externalUrl = reference.url),
        RoomId(session.matrixRoomId))?.getOrThrow()
    require(encrypted is MegolmEncryptedMessageEventContent)
    return client.api.room.sendMessageEvent(RoomId(session.matrixRoomId), encrypted,
        txnId = "srisu-spark-${session.id}").getOrThrow().full
}
