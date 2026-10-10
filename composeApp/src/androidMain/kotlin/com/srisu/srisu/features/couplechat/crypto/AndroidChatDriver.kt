package com.srisu.srisu.features.couplechat.crypto

import android.content.Context
import com.srisu.srisu.securechat.SecureChatEngine

class AndroidChatDriver(context: Context) : NativeChatDriver {
    private val engine = SecureChatEngine(context.applicationContext)
    override fun execute(accountId: String, command: String, payload: String): String = engine.execute(accountId, command, payload)
    override fun close(accountId: String) = engine.close(accountId)
    override fun executeBinary(accountId: String, command: String, payload: String, bytes: ByteArray): String = engine.executeBinary(accountId,command,payload,bytes)
    override fun readAttachment(accountId: String, descriptor: String, decrypt: Boolean): ByteArray? = engine.readAttachment(accountId,descriptor,decrypt)
}
