package com.srisu.srisu.core.data.apiservice.profile

import com.srisu.srisu.core.data.apiservice.base.BaseApiService.Companion.BASE_URL
import com.srisu.srisu.core.data.dto.authdto.ProfileSetupDTO
import com.srisu.srisu.core.data.dto.profile.ProfileUpdateDTO
import com.srisu.srisu.core.data.network.ResultHandler
import com.srisu.srisu.core.data.network.safeRequest
import com.srisu.srisu.core.data.response.auth.ProfileSetupResponse
import com.srisu.srisu.core.data.response.suggestion.SingleConnectionResponse
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.utils.MediaFile
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.appendPathSegments
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ProfileApiService(private val httpClient: HttpClient) {

    suspend fun sendSingleConnectionRequest(
        senderNumber: String?,
        receiverNumber: String?
    ): ResultHandler<SingleConnectionResponse?> {

        val connectionRequest: HashMap<String, String> = HashMap()
        connectionRequest["sender_number"] = senderNumber ?: ""
        connectionRequest["receiver_number"] = receiverNumber ?: ""

        return httpClient.safeRequest<SingleConnectionResponse?> {
            url("${BASE_URL}api/social/connect-single/")
            method = HttpMethod.Post
            setBody(connectionRequest)
        }
    }

    suspend fun sendUpdateProfileRequest(
        userId: Int,
        profileUpdateDTO: ProfileUpdateDTO,
        mediaFile: MediaFile?,
        gallery: List<MediaFile?>?
    ): ResultHandler<ProfileSetupResponse?> {

        return httpClient.safeRequest<ProfileSetupResponse?> {
            url("${BASE_URL}api/auth/setup-profile/")
            method = HttpMethod.Put

            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("phone_number", profileUpdateDTO.phoneNumber ?: "")
                        append("full_name", profileUpdateDTO.fullName ?: "")
                        append("username", profileUpdateDTO.username ?: "")
                        append("bio", profileUpdateDTO.bio ?: "")
                        append("country", profileUpdateDTO.country ?: "")
                        append("city", profileUpdateDTO.city ?: "")
                        append("dob", profileUpdateDTO.dob ?: "")
                        append("gender", profileUpdateDTO.gender ?: "")
                        append("zodiac_sign", profileUpdateDTO.zodiacSign ?: "")
                        append("mood", profileUpdateDTO.mood ?: "")

                        // Actual file data (profile_picture)
                        AppLogger.log("MEDIA FILE = ${Json.encodeToString(mediaFile) }}")
                        mediaFile?.fileBytes?.let { fileBytes ->
                            append(
                                "profile_photo",
                                fileBytes,
                                Headers.build {
                                    append(
                                        HttpHeaders.ContentDisposition,
                                        "form-data; name=profile_photo; filename=${mediaFile.fileName}"
                                    )
                                    append(HttpHeaders.ContentType, mediaFile.mimeType)
                                }
                            )
                        }

                        profileUpdateDTO.userInterests?.let { userInterests ->
                            userInterests.forEachIndexed { index, interestName ->
                                interestName?.let {
                                    append("user_interests[$index][user]", userId)
                                    append("user_interests[$index][name]", interestName)
                                }
                            }
                        }

                        gallery?.let { userPhotos ->
                            userPhotos.forEachIndexed { index, galleryFile ->
                                galleryFile?.fileBytes?.let { fileBytes ->
                                    append("user_photos[$index][user]", userId)
                                    append(
                                        "user_photos[$index][photo]",
                                        fileBytes,
                                        Headers.build {
                                            append(
                                                HttpHeaders.ContentDisposition,
                                                "form-data; name=user_photos[$index][photo]; filename=${galleryFile.fileName}"
                                            )
                                            append(
                                                HttpHeaders.ContentType,
                                                galleryFile.mimeType
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
            )
        }
    }
}