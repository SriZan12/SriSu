package com.srisu.srisu.session

import com.liftric.kvault.KVault
import com.srisu.srisu.core.session.SessionStorage

class AndroidSessionStorage(private val kVault: KVault) : SessionStorage {
    override fun saveSession(credentials: String, sessionKey: String) {
        check(kVault.set(key = sessionKey, stringValue = credentials)) { "Secure session storage failed" }
    }

    override fun getSession(sessionKey: String): String? {
        return kVault.string(forKey = sessionKey)
    }

    override fun clearSession(): Boolean {
        return kVault.clear()
    }

    override fun clearOnReinstall(key: String) {
    }

}