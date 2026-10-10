package com.srisu.srisu.features.couplechat.crypto

/** Platform boundary for libsignal plus the protected SQLite store.
 *
 * Requests/results are bounded JSON values so Swift does not depend on Kotlin
 * serialization internals. No native exceptions or private values cross logs.
 * Call only from the chat repository's serialized background dispatcher.
 */
interface NativeChatDriver {
    fun execute(accountId: String, command: String, payload: String): String
    fun close(accountId: String)
    fun executeBinary(accountId: String, command: String, payload: String, bytes: ByteArray): String
    fun readAttachment(accountId: String, descriptor: String, decrypt: Boolean): ByteArray?
}

/** Swift installs its native implementation before constructing the Compose UI. */
object NativeChatRuntime {
    var driver: NativeChatDriver? = null
        private set
    fun install(value: NativeChatDriver) { driver = value }
}
