package com.srisu.srisu.session

import com.liftric.kvault.KVault
import com.srisu.srisu.session.SessionStorage

class AndroidSessionStorage(private val kVault: KVault) : SessionStorage {
    override fun saveSession(credentials: String, sessionKey: String) {
        kVault.set(key = sessionKey, stringValue = credentials)
    }

    override fun getSession(sessionKey: String): String? {
        return kVault.string(forKey = sessionKey)
    }

    override fun clearSession(): Boolean {
        return kVault.clear()
    }

}