package com.srisu.srisu.features.auth.data.remote.api

import com.srisu.srisu.features.auth.data.remote.response.OtpChallenge
import com.srisu.srisu.core.data.remote.PublicAuthRequestKey
import com.srisu.srisu.core.data.remote.ApiEnvironmentKey
import com.srisu.srisu.core.data.remote.ResultHandler
import com.srisu.srisu.core.data.remote.safeRequest
import com.srisu.srisu.features.auth.data.remote.dto.AuthDTO
import com.srisu.srisu.features.auth.data.remote.dto.ProfileSetupDTO
import com.srisu.srisu.features.auth.data.remote.response.OtpVerificationResponse
import com.srisu.srisu.features.auth.data.remote.response.ProfileResponse
import com.srisu.srisu.utils.MediaFile
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod

class AuthApiService(private val httpClient: HttpClient, private val environment: com.srisu.srisu.core.config.ApiEnvironment = httpClient.attributes[ApiEnvironmentKey]) {
    suspend fun sendOTPRequest(authDTO: AuthDTO): ResultHandler<OtpChallenge?> {
        return httpClient.safeRequest<OtpChallenge> {
            url("${environment.baseUrl}api/auth/send-otp/")
            method = HttpMethod.Post
            attributes.put(PublicAuthRequestKey, true)
            setBody(authDTO)
        }
    }

    suspend fun sendVerifyOtpRequest(
        phoneNumber: String,
        otp: String,
        challengeId: String? = null
    ): ResultHandler<OtpVerificationResponse?> {

        val otpVerificationBody: HashMap<String, String> = HashMap()
        otpVerificationBody["phone_number"] = phoneNumber
        otpVerificationBody["otp_code"] = otp
        challengeId?.let { otpVerificationBody["challenge_id"] = it }

        return httpClient.safeRequest<OtpVerificationResponse> {
            url("${environment.baseUrl}api/auth/verify-otp/")
            method = HttpMethod.Post
            attributes.put(PublicAuthRequestKey, true)
            setBody(otpVerificationBody)
        }
    }

    suspend fun getProfile(): ResultHandler<ProfileResponse?> = httpClient.safeRequest {
        url("${environment.baseUrl}api/auth/setup-profile/")
        method = HttpMethod.Get
    }

    suspend fun updateName(name: String, username: String): ResultHandler<ProfileResponse?> = httpClient.safeRequest {
        url("${environment.baseUrl}api/auth/setup-profile/")
        method = HttpMethod.Patch
        setBody(mapOf("full_name" to name, "username" to username))
    }

    suspend fun revoke(refresh: String): ResultHandler<Unit?> = httpClient.safeRequest {
        url("${environment.baseUrl}api/auth/logout/")
        method = HttpMethod.Post
        attributes.put(PublicAuthRequestKey, true)
        setBody(mapOf("refresh" to refresh))
    }

    suspend fun sendProfileSetupRequest(profileSetupDTO: ProfileSetupDTO, mediaFile: MediaFile?): ResultHandler<ProfileResponse?> = httpClient.safeRequest {
        url("${environment.baseUrl}api/auth/setup-profile/")
        method = HttpMethod.Patch
        if (mediaFile == null) setBody(mapOf("skip_photo" to true))
        else setBody(MultiPartFormDataContent(formData {
            append("profile_photo", requireNotNull(mediaFile.fileBytes), Headers.build {
                append(HttpHeaders.ContentDisposition, "filename=profile.jpg")
                append(HttpHeaders.ContentType, "application/octet-stream")
            })
        }))
    }
}
