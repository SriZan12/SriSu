package com.srisu.srisu.features.couplechat.crypto

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

internal val ChatJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = true }

internal class ChatStorageFailure(val code: String) : Exception("Protected chat operation failed")

@Serializable
internal data class ProtectedEntry(val key: String, val value: String?)

/** All methods are synchronous and belong on the repository's background scope.
 * Native calls serialize access and commit a single SQLite transaction each.
 * Only app/ records are available to common code; protocol/key records are private.
 */
internal class ProtectedChatStore(private val driver: NativeChatDriver, val accountId: Long) {
    fun binary(command: String, input: JsonObject, bytes: ByteArray): JsonElement {
        val response = try { ChatJson.parseToJsonElement(driver.executeBinary(accountId.toString(),command,input.toString(),bytes)).jsonObject }
            catch (_: Exception) { throw ChatStorageFailure("attachment_unavailable") }
        if (response["ok"]?.jsonPrimitive?.booleanOrNull != true)
            throw ChatStorageFailure(response["code"]?.jsonPrimitive?.contentOrNull ?: "attachment_unavailable")
        return response["value"] ?: JsonNull
    }
    fun attachment(descriptor: JsonObject, decrypt: Boolean): ByteArray? = driver.readAttachment(accountId.toString(),descriptor.toString(),decrypt)
    fun call(command: String, input: JsonObject = buildJsonObject {}): JsonElement {
        val response = try { ChatJson.parseToJsonElement(driver.execute(accountId.toString(), command, input.toString())).jsonObject }
            catch (_: Exception) { throw ChatStorageFailure("storage_or_protocol_failure") }
        if (response["ok"]?.jsonPrimitive?.booleanOrNull != true)
            throw ChatStorageFailure(response["code"]?.jsonPrimitive?.contentOrNull ?: "storage_or_protocol_failure")
        return response["value"] ?: JsonNull
    }

    fun read(key: String): String? = call("read", buildJsonObject { put("key", key) }).let {
        if (it == JsonNull) null else it.jsonPrimitive.content
    }

    fun readMany(keys: List<String>): List<String?> = call("read_many", buildJsonObject { put("keys", ChatJson.encodeToJsonElement(keys)) })
        .jsonArray.map { if (it == JsonNull) null else it.jsonPrimitive.content }

    fun count(prefix: String): Int = call("count", buildJsonObject { put("prefix", prefix) }).jsonPrimitive.int

    inline fun <reified T> record(key: String): T? = read(key)?.let {
        try { ChatJson.decodeFromString<T>(it) } catch (_: kotlinx.serialization.SerializationException) { throw ChatStorageFailure("storage_or_protocol_failure") }
    }

    fun write(entries: List<ProtectedEntry>) {
        call("write", buildJsonObject { put("entries", ChatJson.encodeToJsonElement(entries)) })
    }

    inline fun <reified T> put(key: String, value: T) = write(listOf(ProtectedEntry(key, ChatJson.encodeToString(value))))

    fun page(prefix: String, before: String = "", limit: Int = 50): List<ProtectedEntry> =
        ChatJson.decodeFromJsonElement(call("page", buildJsonObject {
            put("prefix", prefix); put("before", before); put("limit", limit)
        }))

    fun close() = driver.close(accountId.toString())
}
