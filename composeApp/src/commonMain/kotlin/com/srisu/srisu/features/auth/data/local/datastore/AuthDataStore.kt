package com.srisu.srisu.features.auth.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.features.auth.presentation.components.OTPScreenMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Device-only entry preference. It is never evidence of authentication. */
enum class IntroductionStep { WELCOME, SPACE, PHONE, GUEST }

class AuthDataStore(
    private val dataStore: DataStore<Preferences>
) {

    companion object {
        val OTP_META_DATA = stringPreferencesKey(name = "saved_otp_timestamp")
        private val INTRODUCTION_STEP = stringPreferencesKey("introduction_v1_step")
        private val PHONE_RETURN_STEP = stringPreferencesKey("introduction_v1_phone_return")
    }

    suspend fun introductionStep(): IntroductionStep = dataStore.data.first()[INTRODUCTION_STEP]
        ?.let { saved -> IntroductionStep.entries.firstOrNull { it.name == saved } }
        ?: IntroductionStep.WELCOME

    suspend fun phoneReturnStep(): IntroductionStep = dataStore.data.first()[PHONE_RETURN_STEP]
        ?.let { saved -> IntroductionStep.entries.firstOrNull { it.name == saved && it != IntroductionStep.PHONE } }
        ?: IntroductionStep.WELCOME

    suspend fun saveIntroductionStep(step: IntroductionStep, returnTo: IntroductionStep? = null) {
        dataStore.edit {
            it[INTRODUCTION_STEP] = step.name
            if (returnTo != null) it[PHONE_RETURN_STEP] = returnTo.name
        }
    }

    suspend fun saveOTPTimestamp(otpScreenMetadata: OTPScreenMetadata): Boolean =
        try {
            val jsonString = Json.encodeToString(otpScreenMetadata)

            dataStore.edit { preferences ->
                preferences[OTP_META_DATA] = jsonString
            }
            true
        } catch (illegalArgumentException: IllegalArgumentException) {
            AppLogger.log("Illegal Argument Exception: $illegalArgumentException")
            false
        } catch (serializationException: SerializationException) {
            AppLogger.log("Serialization Exception: $serializationException")
            false
        }

    fun getOTPTimestamp(): Flow<OTPScreenMetadata?> =
        dataStore.data
            .map { preferences ->
                preferences[OTP_META_DATA]?.let { jsonString ->
                    try {
                        Json.decodeFromString<OTPScreenMetadata>(jsonString)
                    } catch (illegalArgumentException: IllegalArgumentException) {
                        AppLogger.log("Illegal Argument Exception: $illegalArgumentException")
                        null
                    } catch (serializationException: SerializationException) {
                        AppLogger.log("Serialization Exception: $serializationException")
                        null
                    }
                }
            }

    suspend fun deleteOTPTimeStamp() {
        try {
            dataStore.edit { preferences ->
                preferences.remove(OTP_META_DATA)
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
        } catch (e: Exception) {
            AppLogger.log("Error deleting OTP timestamp: $e")
        }
    }


}
