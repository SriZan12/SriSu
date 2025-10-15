package com.srisu.srisu.session

import com.liftric.kvault.KVault
import com.srisu.srisu.core.logger.AppLogger
import platform.Foundation.NSUserDefaults


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

    override fun clearOnReinstall(key: String) {
        val defaults = NSUserDefaults.standardUserDefaults
        if (defaults.objectForKey(key) == null) {
            kvault.clear()
            AppLogger.log("INSIDE CLEAR ON REINSTALL")
            defaults.setBool(true, forKey = key)
        }
    }


}
