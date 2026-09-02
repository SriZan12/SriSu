package com.srisu.srisu.core.session

import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.utils.Constants.Auth.SESSION_KEY
import kotlinx.serialization.json.Json

class SessionUtils(
    private val sessionStorage: SessionStorage,
) {

    fun getSession(): Session? {
        val sessionJson = sessionStorage.getSession(sessionKey = SESSION_KEY)
        var sessionData: Session? = null

        try {
            sessionData = sessionJson?.let { Json.decodeFromString<Session>(it) }
        } catch (exception: Exception) {
            AppLogger.log("SESSION SERIALIZATION EXCEPTION = ${exception.message}")
        }

        return sessionData
    }

    fun getPhoneNumber(): String? {
        val session = getSession()
        return session?.phoneNumber
    }

    fun getFullName(): String? {
        val session = getSession()
        return session?.fullName

    }

    fun getCurrentUserId(): Long? {
        val session = getSession()
        return session?.id
    }

}