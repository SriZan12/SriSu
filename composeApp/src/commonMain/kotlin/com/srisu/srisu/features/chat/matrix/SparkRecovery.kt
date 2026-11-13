package com.srisu.srisu.features.chat.matrix

import com.srisu.srisu.features.sparks.data.*
import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.core.ErrorResponse
import de.connect2x.trixnity.core.MatrixServerException
import de.connect2x.trixnity.core.model.events.UnknownEventContent
import de.connect2x.trixnity.core.model.events.block.EventContentBlocks
import de.connect2x.trixnity.core.model.events.m.secretstorage.SecretEventContent
import de.connect2x.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent
import de.connect2x.trixnity.crypto.core.SecureRandom
import de.connect2x.trixnity.crypto.key.decodeRecoveryKey
import de.connect2x.trixnity.crypto.key.encodeRecoveryKey
import de.connect2x.trixnity.crypto.key.encryptSecret
import de.connect2x.trixnity.crypto.key.decryptSecret
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okio.ByteString.Companion.toByteString
import okio.ByteString.Companion.encodeUtf8

/** Account-private Matrix account data, never room events or Django plaintext.
 * Uses the pinned SDK's Matrix secret-storage AES-256-CTR + HMAC-SHA256.
 * Independent immutable vault namespaces prevent replacing another device's backup.
 */
internal interface SparkRecoveryTransport {
    suspend fun get(name: String): JsonObject?
    suspend fun put(name: String, content: JsonObject)
}
internal class MatrixSparkRecoveryTransport(private val client: MatrixClient) : SparkRecoveryTransport {
    override suspend fun get(name: String): JsonObject? {
        val result = client.api.user.getAccountData(name, client.userId)
        val error = result.exceptionOrNull()
        if (error is MatrixServerException && error.errorResponse is ErrorResponse.NotFound) return null
        return (result.getOrThrow() as? UnknownEventContent)?.raw ?: error("Unsupported recovery format")
    }
    override suspend fun put(name: String, content: JsonObject) {
        client.api.user.setAccountData(UnknownEventContent(content, EventContentBlocks(), name), client.userId).getOrThrow()
    }
}

enum class SparkRecoveryStatus { NotSetUp, Pending, Locked, Ready }
internal const val SPARK_RECOVERY_ROOT = "com.srisu.sparks.recovery.v1"
internal fun newSparkRecoveryKey(): String = SecureRandom.nextBytes(32).let { bytes ->
    try { encodeRecoveryKey(bytes) } finally { bytes.fill(0) }
}
internal fun recoveryKeyId(encoded: String): String = decodeRecoveryKey(encoded).let {
    try { it.toByteString().sha256().hex() } finally { it.fill(0) }
}
internal fun canonicalRecoveryEnvelope(element: JsonElement): String = when (element) {
    is JsonObject -> element.entries.sortedBy { it.key }.joinToString(",", "{", "}") { JsonPrimitive(it.key).toString() + ":" + canonicalRecoveryEnvelope(it.value) }
    is JsonArray -> element.joinToString(",", "[", "]") { canonicalRecoveryEnvelope(it) }
    else -> element.toString()
}

@Serializable private data class RecoveryHeader(val version: Int = 1, val owner: String, val keyId: String)
@Serializable private data class RecoveryAnswer(val version: Int = 1, val owner: String, val room: String,
    val author: Long, val envelopeHash: String, val value: SparkPlaintext)
private data class RecoverySecret(override val encrypted: Map<String, JsonElement>) : SecretEventContent

/** Authenticated copies of answers that THIS account previously decrypted and authenticated.
 * We deliberately do not mark public-key-encrypted Megolm backups as trusted, import
 * device identities, alter cross-signing or relax decryptSpark's sender checks.
 * Every restore still requires a freshly authorized, reveal-gated Spark envelope.
 */
internal class SparkRecoveryVault(private val transport: SparkRecoveryTransport, private val owner: String) {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }
    private fun headerName(id: String) = "$SPARK_RECOVERY_ROOT.$id.header"
    private fun hash(answer: SparkAnswer) = canonicalRecoveryEnvelope(answer.envelope).encodeUtf8().sha256().hex()
    private fun answerName(id: String, session: SparkSession, answer: SparkAnswer) =
        "$SPARK_RECOVERY_ROOT.$id.answer." + "${session.id}|${answer.author}|${hash(answer)}".encodeUtf8().sha256().hex()

    suspend fun status(key: String?): SparkRecoveryStatus {
        if (key != null) {
            val header = transport.get(headerName(recoveryKeyId(key)))
            if (header != null) { validateHeader(key, header); return SparkRecoveryStatus.Ready }
            if (transport.get(SPARK_RECOVERY_ROOT) == null) return SparkRecoveryStatus.Pending
        }
        return if (transport.get(SPARK_RECOVERY_ROOT) == null) SparkRecoveryStatus.NotSetUp else SparkRecoveryStatus.Locked
    }

    suspend fun create(key: String) {
        val id = recoveryKeyId(key)
        val existing = transport.get(SPARK_RECOVERY_ROOT)
        // No reset/replacement flow. A prior uncertain request may only resume its exact key.
        require(existing == null || existing["key_id"]?.jsonPrimitive?.content == id) { "Recovery already exists. Use your saved recovery key." }
        val header = transport.get(headerName(id))
        if (header == null) transport.put(headerName(id), seal(key, headerName(id), json.encodeToString(RecoveryHeader(owner = owner, keyId = id))))
        else validateHeader(key, header)
        // Key-specific headers/answers remain accessible even if two first-time setups race.
        transport.put(SPARK_RECOVERY_ROOT, buildJsonObject { put("key_id", id); put("version", 1) })
        unlock(key)
    }

    suspend fun unlock(key: String) {
        val header = requireNotNull(transport.get(headerName(recoveryKeyId(key)))) { "Recovery key does not match a backup for this account." }
        validateHeader(key, header)
    }
    private suspend fun validateHeader(key: String, header: JsonObject) {
        val id = recoveryKeyId(key)
        val value = json.decodeFromString<RecoveryHeader>(open(key, headerName(id), header))
        require(value.version == 1 && value.owner == owner && value.keyId == id)
    }

    suspend fun backup(key: String, session: SparkSession, answer: SparkAnswer, authenticatedText: String) {
        require(session.answers.any { it == answer })
        val id = recoveryKeyId(key)
        val name = answerName(id, session, answer)
        val value = RecoveryAnswer(owner = owner, room = session.matrixRoomId, author = answer.author,
            envelopeHash = hash(answer), value = SparkPlaintext(spark = session.id, couple = session.couple,
                epoch = session.epoch, questionId = session.questionId, question = session.question,
                sender = answer.sender, answer = authenticatedText))
        value.value.validated(session, answer.sender)
        val existing = transport.get(name)
        if (existing != null) {
            // Idempotent uploads, including a lost acknowledgement or another recovered phone.
            require(json.decodeFromString<RecoveryAnswer>(open(key, name, existing)) == value)
            return
        }
        transport.put(name, seal(key, name, json.encodeToString(value)))
    }

    suspend fun recover(key: String, session: SparkSession, answer: SparkAnswer): String? {
        require(session.answers.any { it == answer })
        val name = answerName(recoveryKeyId(key), session, answer)
        val encrypted = transport.get(name) ?: return null
        val value = json.decodeFromString<RecoveryAnswer>(open(key, name, encrypted))
        require(value.version == 1 && value.owner == owner && value.room == session.matrixRoomId &&
            value.author == answer.author && value.envelopeHash == hash(answer))
        return value.value.validated(session, answer.sender)
    }

    private suspend fun seal(encoded: String, name: String, plaintext: String): JsonObject {
        val key = decodeRecoveryKey(encoded)
        return try { buildJsonObject {
            put("algorithm", "m.secret_storage.v1.aes-hmac-sha2")
            put("encrypted", JsonObject(encryptSecret(key, recoveryKeyId(encoded), name, plaintext, json)))
        } } finally { key.fill(0) }
    }
    private suspend fun open(encoded: String, name: String, value: JsonObject): String {
        require(value["algorithm"]?.jsonPrimitive?.content == "m.secret_storage.v1.aes-hmac-sha2")
        val key = decodeRecoveryKey(encoded)
        return try { requireNotNull(decryptSecret(key, recoveryKeyId(encoded), SecretKeyEventContent.AesHmacSha2Key(),
            name, RecoverySecret(value.getValue("encrypted").jsonObject), json)) } finally { key.fill(0) }
    }
}
