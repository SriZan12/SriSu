package com.srisu.srisu.core.data.repository

import com.srisu.srisu.core.data.apiservice.AuthApiService
import com.srisu.srisu.core.data.dto.authdto.AuthDTO
import com.srisu.srisu.core.data.dto.authdto.ProfileSetupDTO
import com.srisu.srisu.core.data.network.ResultHandler
import com.srisu.srisu.core.data.response.auth.OtpVerificationResponse
import com.srisu.srisu.core.data.response.auth.ProfileSetupResponse
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
    ): ResultHandler<ProfileSetupResponse?> {
        return authApiService.sendProfileSetupRequest(
            profileSetupDTO = profileSetupDTO,
            mediaFile = mediaFile
        )
    }
}