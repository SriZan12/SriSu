package com.srisu.srisu.core.session

interface SessionStorage {
    fun saveSession(credentials: String, sessionKey: String)
    fun getSession(sessionKey: String): String?
    fun clearSession(): Boolean

    fun clearOnReinstall(key: String)
}
