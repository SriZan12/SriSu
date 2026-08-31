package com.srisu.srisu.features.home.couple.data.remote.api

import com.srisu.srisu.core.data.remote.BaseApiService
import com.srisu.srisu.core.data.remote.ResultHandler
import com.srisu.srisu.core.data.remote.safeRequest
import com.srisu.srisu.features.home.couple.data.remote.dto.CoupleProfileWriteRequest
import com.srisu.srisu.features.home.couple.data.remote.response.CoupleProfileData
import com.srisu.srisu.utils.MediaFile
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod

class CoupleProfileApiService(
    private val httpClient: HttpClient,
) {
    companion object {
        private const val COUPLE_PROFILE_ENDPOINT = "api/social/couple-profile/"
    }

    suspend fun getProfile(): ResultHandler<CoupleProfileData?> {
        return httpClient.safeRequest<CoupleProfileData?> {
            url("${BaseApiService.BASE_URL}$COUPLE_PROFILE_ENDPOINT")
            method = HttpMethod.Get
        }
    }

    suspend fun createProfile(
        request: CoupleProfileWriteRequest,
    ): ResultHandler<CoupleProfileData?> {
        return httpClient.safeRequest<CoupleProfileData?> {
            url("${BaseApiService.BASE_URL}$COUPLE_PROFILE_ENDPOINT")
            method = HttpMethod.Post
            setBody(request)
        }
    }

    suspend fun updateProfile(
        request: CoupleProfileWriteRequest,
    ): ResultHandler<CoupleProfileData?> {
        return httpClient.safeRequest<CoupleProfileData?> {
            url("${BaseApiService.BASE_URL}$COUPLE_PROFILE_ENDPOINT")
            method = HttpMethod.Patch
            setBody(request)
        }
    }

    suspend fun uploadCoverPhoto(
        coverPhoto: MediaFile,
    ): ResultHandler<CoupleProfileData?> {
        return httpClient.safeRequest<CoupleProfileData?> {
            url("${BaseApiService.BASE_URL}$COUPLE_PROFILE_ENDPOINT")
            method = HttpMethod.Patch
            setBody(
                MultiPartFormDataContent(
                    formData {
                        coverPhoto.fileBytes?.let { bytes ->
                            append(
                                key = "cover_photo",
                                value = bytes,
                                headers = Headers.build {
                                    append(
                                        HttpHeaders.ContentDisposition,
                                        "form-data; name=cover_photo; filename=${coverPhoto.fileName ?: "couple-cover"}",
                                    )
                                    append(
                                        HttpHeaders.ContentType,
                                        coverPhoto.mimeType ?: "image/jpeg",
                                    )
                                },
                            )
                        }
                    },
                ),
            )
        }
    }
}
