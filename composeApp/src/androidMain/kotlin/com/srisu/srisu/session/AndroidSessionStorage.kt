package com.srisu.srisu.session

import android.content.Context
import android.preference.PreferenceManager
import com.liftric.kvault.KVault
import com.srisu.srisu.session.SessionStorage
import androidx.core.content.edit
import com.srisu.srisu.utils.AppContext

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

    override fun clearOnReinstall(key: String) {

    }

}