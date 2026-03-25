package com.srisu.srisu.features.auth.data.remote.api

import com.srisu.srisu.core.data.remote.BaseApiService
import com.srisu.srisu.core.data.remote.ResultHandler
import com.srisu.srisu.core.data.remote.safeRequest
import com.srisu.srisu.features.auth.data.remote.response.OtpVerificationResponse
import com.srisu.srisu.features.auth.data.remote.response.ProfileResponse
import com.srisu.srisu.features.auth.data.remote.dto.AuthDTO
import com.srisu.srisu.features.auth.data.remote.dto.ProfileSetupDTO
import com.srisu.srisu.utils.MediaFile
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod

class AuthApiService(private val httpClient: HttpClient) {
    suspend fun sendOTPRequest(authDTO: AuthDTO): ResultHandler<String?> {
        return httpClient.safeRequest<String?> {
            url("${BaseApiService.Companion.BASE_URL}api/auth/send-otp/")
            method = HttpMethod.Companion.Post
            setBody(authDTO)
        }
    }

    suspend fun sendVerifyOtpRequest(
        phoneNumber: String,
        otp: String
    ): ResultHandler<OtpVerificationResponse?> {

        val otpVerificationBody: HashMap<String, String> = HashMap()
        otpVerificationBody["phone_number"] = phoneNumber
        otpVerificationBody["otp_code"] = otp

        return httpClient.safeRequest<OtpVerificationResponse> {
            url("${BaseApiService.Companion.BASE_URL}api/auth/verify-otp/")
            method = HttpMethod.Companion.Post
            setBody(otpVerificationBody)
        }
    }

    suspend fun sendProfileSetupRequest(
        profileSetupDTO: ProfileSetupDTO,
        mediaFile: MediaFile?
    ): ResultHandler<ProfileResponse?> {

        return httpClient.safeRequest<ProfileResponse> {
            url("${BaseApiService.Companion.BASE_URL}api/auth/setup-profile/")
            method = HttpMethod.Companion.Put

            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("phone_number", profileSetupDTO.phoneNumber ?: "")
                        append("full_name", profileSetupDTO.fullName ?: "")
                        append("username", profileSetupDTO.username ?: "")
                        append("dob", profileSetupDTO.dob ?: "")
                        append("gender", profileSetupDTO.gender ?: "")
                        append("zodiac_sign", profileSetupDTO.zodiacSign ?: "")
                        append("mood", profileSetupDTO.mood ?: "")

                        // Actual file data (profile_picture)
                        mediaFile?.fileBytes?.let { fileBytes ->
                            append(
                                "profile_photo",
                                fileBytes,
                                Headers.Companion.build {
                                    append(
                                        HttpHeaders.ContentDisposition,
                                        "form-data; name=profile_photo; filename=${mediaFile.fileName}"
                                    )
                                    append(
                                        HttpHeaders.ContentType,
                                        mediaFile.mimeType ?: "application/octet-stream"
                                    )
                                }
                            )
                        }
                    }
                )
            )
        }
    }


}