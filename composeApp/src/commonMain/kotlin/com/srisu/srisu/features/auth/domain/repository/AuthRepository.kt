package com.srisu.srisu.features.auth.domain.repository

import com.srisu.srisu.features.auth.data.remote.api.AuthApiService
import com.srisu.srisu.core.data.remote.ResultHandler
import com.srisu.srisu.features.auth.data.remote.response.OtpVerificationResponse
import com.srisu.srisu.features.auth.data.remote.response.ProfileResponse
import com.srisu.srisu.features.auth.data.remote.dto.AuthDTO
import com.srisu.srisu.features.auth.data.remote.dto.ProfileSetupDTO
import com.srisu.srisu.utils.MediaFile

class AuthRepository(
    private val authApiService: AuthApiService
) {

    @Throws(Exception::class)
    suspend fun sendOTPRequest(authDTO: AuthDTO): ResultHandler<String?> {
        return authApiService.sendOTPRequest(authDTO = authDTO)
    }

    @Throws(Exception::class)
    suspend fun sendVerifyOtpRequest(
        phoneNumber: String,
        otp: String
    ): ResultHandler<OtpVerificationResponse?> {
        return authApiService.sendVerifyOtpRequest(phoneNumber = phoneNumber, otp = otp)
    }

    @Throws(Exception::class)
    suspend fun sendProfileSetupRequest(
        profileSetupDTO: ProfileSetupDTO,
        mediaFile: MediaFile?
    ): ResultHandler<ProfileResponse?> {
        return authApiService.sendProfileSetupRequest(
            profileSetupDTO = profileSetupDTO,
            mediaFile = mediaFile
        )
    }
}