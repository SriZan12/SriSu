package com.srisu.srisu.core.session

import com.srisu.srisu.utils.Constants.Auth.SESSION_KEY
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

data class SessionStamp(val generation: Long, val accountId: Long?)
class SessionChangedException : CancellationException("Session scope changed")

/** Existing secure storage format is retained. The observable state has no secrets. */
class SessionCoordinator(private val storage: SessionStorage) : SessionStorage {
    private val lock = SynchronizedObject()
    private val json = Json { ignoreUnknownKeys = true }
    private var credentials = storage.run {
        clearOnReinstall(com.srisu.srisu.utils.Constants.Auth.FIRST_INSTALL_FLAG)
        getSession(SESSION_KEY)
    }
    private var session = decode(credentials)
    private val _state = MutableStateFlow(SessionStamp(0, session?.id))
    val state = _state.asStateFlow()

    fun stamp(): SessionStamp = _state.value
    fun ensureCurrent(stamp: SessionStamp) {
        if (this.stamp() != stamp) throw SessionChangedException()
    }
    fun authorization(stamp: SessionStamp): String? = synchronized(lock) {
        ensureCurrent(stamp)
        session?.access
    }
    fun accessToken(): String? = synchronized(lock) { session?.access }

    override fun getSession(sessionKey: String): String? = synchronized(lock) {
        if (sessionKey == SESSION_KEY) credentials else storage.getSession(sessionKey)
    }

    override fun saveSession(credentials: String, sessionKey: String) = synchronized(lock) {
        if (sessionKey != SESSION_KEY) {
            storage.saveSession(credentials, sessionKey)
            return@synchronized
        }
        val next = requireNotNull(decode(credentials)) { "Invalid session format" }
        storage.saveSession(credentials, sessionKey)
        val changed = session?.id != next.id || session?.access != next.access
        this.credentials = credentials
        session = next
        if (changed) _state.value = SessionStamp(_state.value.generation + 1, next.id)
    }

    fun saveIfCurrent(credentials: String, stamp: SessionStamp): Boolean = synchronized(lock) {
        if (_state.value != stamp) return@synchronized false
        saveSession(credentials, SESSION_KEY)
        true
    }

    override fun clearSession(): Boolean = synchronized(lock) {
        credentials = null
        session = null
        _state.value = SessionStamp(_state.value.generation + 1, null)
        storage.clearSession()
    }

    override fun clearOnReinstall(key: String) = synchronized(lock) {
        storage.clearOnReinstall(key)
        val next = storage.getSession(SESSION_KEY)
        if (next != credentials) {
            credentials = next
            session = decode(next)
            _state.value = SessionStamp(_state.value.generation + 1, session?.id)
        }
    }

    private fun decode(value: String?): Session? = try {
        value?.let { json.decodeFromString<Session>(it) }
    } catch (_: kotlinx.serialization.SerializationException) {
        null
    }
}
