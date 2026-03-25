package com.srisu.srisu.features.home.profile.data.remote.api

import com.srisu.srisu.features.home.profile.data.dto.ProfileUpdateDTO
import com.srisu.srisu.core.data.remote.ResultHandler
import com.srisu.srisu.core.data.remote.safeRequest
import com.srisu.srisu.core.data.remote.BaseApiService
import com.srisu.srisu.features.home.suggestions.data.response.SingleConnectionResponse
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.features.auth.data.remote.response.InterestResponse
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

class ProfileApiService(private val httpClient: HttpClient) {

    suspend fun sendSingleConnectionRequest(
        senderNumber: String?,
        receiverNumber: String?
    ): ResultHandler<SingleConnectionResponse?> {

        val connectionRequest: HashMap<String, String> = HashMap()
        connectionRequest["sender_number"] = senderNumber ?: ""
        connectionRequest["receiver_number"] = receiverNumber ?: ""

        return httpClient.safeRequest<SingleConnectionResponse?> {
            url("${BaseApiService.Companion.BASE_URL}api/social/connect-single/")
            method = HttpMethod.Companion.Post
            setBody(connectionRequest)
        }
    }

    suspend fun getInterestList(): ResultHandler<InterestResponse?> {
        return httpClient.safeRequest<InterestResponse?> {
            url("${BaseApiService.Companion.BASE_URL}api/auth/interests/")
            method = HttpMethod.Companion.Get
        }
    }

    suspend fun sendUpdateProfileRequest(
        userId: Long?,
        profileUpdateDTO: ProfileUpdateDTO,
        mediaFile: MediaFile?,
        gallery: List<MediaFile?>?
    ): ResultHandler<ProfileResponse?> {

        return httpClient.safeRequest<ProfileResponse?> {
            url("${BaseApiService.Companion.BASE_URL}api/auth/setup-profile/")
            method = HttpMethod.Companion.Put

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

                        mediaFile?.let {
                            if (mediaFile.fileBytes != null) {
                                append(
                                    "profile_photo",
                                    mediaFile.fileBytes,
                                    Headers.Companion.build {
                                        append(
                                            HttpHeaders.ContentDisposition,
                                            "form-data; name=profile_photo; filename=${mediaFile.fileName}"
                                        )
                                        append(HttpHeaders.ContentType, mediaFile.mimeType ?: "")
                                    }
                                )
                            }
                        }

                        profileUpdateDTO.userInterests?.let { userInterests ->
                            AppLogger.log("USER INTERESTS API SERVICE= $userInterests")
                            userInterests.forEachIndexed { index, interest ->
                                AppLogger.log("USER ID = $userId")
                                if (userId != null) {
                                    AppLogger.log("USER ID = $userId")
                                    append("user_interests[$index][user]", userId)
                                    interest?.name?.let {
                                        append(
                                            "user_interests[$index][name]",
                                            it
                                        )
                                    }
                                    interest?.interest?.let {
                                        append(
                                            "user_interests[$index][interest]",
                                            it
                                        )
                                    }
                                    interest?.removed?.let {
                                        append(
                                            "user_interests[$index][removed]",
                                            it
                                        )
                                    }
//                                    append("user_interests[$index][removed]", false)
                                }
                            }
                        }


                        var appendIndex = 0

                        gallery?.forEach { galleryFile ->
                            if (userId != null && galleryFile != null) {
                                when {
                                    // Case 1: Updating the photo
                                    galleryFile.removed == true && galleryFile.fileBytes != null && galleryFile.id != null -> {
                                        AppLogger.log("INSIDE UPDATING THE PHOTO")
                                        append("user_photos[$appendIndex][user]", userId)
                                        append("user_photos[$appendIndex][id]", galleryFile.id)
                                        append(
                                            key = "user_photos[$appendIndex][photo]",
                                            value = galleryFile.fileBytes,
                                            Headers.Companion.build {
                                                append(
                                                    HttpHeaders.ContentDisposition,
                                                    "form-data; name=user_photos[$appendIndex][photo]; filename=${galleryFile.fileName}"
                                                )
                                                append(
                                                    HttpHeaders.ContentType,
                                                    galleryFile.mimeType ?: ""
                                                )
                                            }
                                        )
                                        appendIndex++
                                    }

                                    // Case 2: Removing photo
                                    galleryFile.removed == true && galleryFile.fileBytes == null && galleryFile.id != null -> {
                                        AppLogger.log("INSIDE REMOVING THE PHOTO")
                                        append("user_photos[$appendIndex][user]", userId)
                                        append("user_photos[$appendIndex][id]", galleryFile.id)
                                        append("user_photos[$appendIndex][removed]", true)
                                        appendIndex++
                                    }

                                    // Case 3: New photo or update photo(upload)
                                    galleryFile.fileBytes != null -> {
                                        AppLogger.log("INSIDE 3 CASE NEW PHOTO UPLOAD")
                                        AppLogger.log("gallery file id = ${galleryFile.id}")
                                        append("user_photos[$appendIndex][user]", userId)
                                        galleryFile.id?.let {
                                            append("user_photos[$appendIndex][id]", it)
                                        }
                                        append(
                                            key = "user_photos[$appendIndex][photo]",
                                            value = galleryFile.fileBytes,
                                            Headers.Companion.build {
                                                append(
                                                    HttpHeaders.ContentDisposition,
                                                    "form-data; name=user_photos[$appendIndex][photo]; filename=${galleryFile.fileName}"
                                                )
                                                append(
                                                    HttpHeaders.ContentType,
                                                    galleryFile.mimeType ?: ""
                                                )
                                            }
                                        )
                                        appendIndex++
                                    }

                                    else -> {
//                                        if (galleryFile.id != null){
//                                            append("user_photos[$appendIndex][id]",galleryFile.id)
//                                            append("user_photos[$appendIndex][user]", userId)
//                                        }
                                    }
                                }
                            }
                        }


                    }
                )
            )
        }
    }

    suspend fun getProfile(): ResultHandler<ProfileResponse?> {
        return httpClient.safeRequest<ProfileResponse?> {
            url("${BaseApiService.Companion.BASE_URL}api/auth/setup-profile/")
            method = HttpMethod.Companion.Get
        }
    }
}