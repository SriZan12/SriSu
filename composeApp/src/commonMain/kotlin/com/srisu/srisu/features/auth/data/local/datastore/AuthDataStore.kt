package com.srisu.srisu.features.auth.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.features.auth.presentation.components.OTPScreenMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class AuthDataStore(
    private val dataStore: DataStore<Preferences>
) {

    companion object {
        val OTP_META_DATA = stringPreferencesKey(name = "saved_otp_timestamp")
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
        } catch (e: Exception) {
            AppLogger.log("Error deleting OTP timestamp: $e")
        }
    }


}