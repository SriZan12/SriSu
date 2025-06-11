package com.srisu.srisu.session

interface SessionStorage {
    fun saveSession(credentials: String, sessionKey: String)
    fun getSession(sessionKey: String): String?
    fun clearSession(): Boolean
}
