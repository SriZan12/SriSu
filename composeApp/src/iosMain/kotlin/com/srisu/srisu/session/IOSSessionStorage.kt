package com.srisu.srisu.session

import com.liftric.kvault.KVault
import com.srisu.srisu.core.logger.AppLogger


class IOSSessionStorage(private val kvault: KVault) : SessionStorage {
    override fun saveSession(credentials: String, sessionKey: String) {
        val session  = kvault.set(key = sessionKey, stringValue = credentials)
        AppLogger.log("session saved ios $session")
    }

    override fun getSession(sessionKey: String): String? {
        return kvault.string(forKey = sessionKey)
    }

    override fun clearSession(): Boolean {
        return kvault.clear()
    }
}
