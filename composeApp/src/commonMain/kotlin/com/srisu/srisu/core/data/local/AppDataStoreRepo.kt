package com.srisu.srisu.core.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.features.auth.common.OTPScreenMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AppDataStoreRepo(
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
        } catch (e: Exception) {
            AppLogger.log("OTP TIME STAMP Error: $e")
            false
        }

    fun getOTPTimestamp(): Flow<OTPScreenMetadata?> =
        dataStore.data
            .map { preferences ->
                preferences[OTP_META_DATA]?.let { jsonString ->
                    try {
                        Json.decodeFromString<OTPScreenMetadata>(jsonString)
                    } catch (e: Exception) {
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