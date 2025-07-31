package com.srisu.srisu.session

import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.utils.Constants.Auth.SESSION_KEY
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SessionUtils : KoinComponent {

    val session: SessionStorage by inject()

    fun getSession(): Session? {
        val sessionJson = session.getSession(sessionKey = SESSION_KEY)
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

}