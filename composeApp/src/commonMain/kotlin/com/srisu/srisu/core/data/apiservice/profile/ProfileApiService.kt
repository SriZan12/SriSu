package com.srisu.srisu.core.data.apiservice.profile

import com.srisu.srisu.App
import com.srisu.srisu.core.data.apiservice.base.BaseApiService.Companion.BASE_URL
import com.srisu.srisu.core.data.dto.authdto.ProfileSetupDTO
import com.srisu.srisu.core.data.dto.profile.ProfileUpdateDTO
import com.srisu.srisu.core.data.network.ResultHandler
import com.srisu.srisu.core.data.network.safeRequest
import com.srisu.srisu.core.data.response.auth.InterestResponse
import com.srisu.srisu.core.data.response.auth.ProfileResponse
import com.srisu.srisu.core.data.response.suggestion.SingleConnectionResponse
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.utils.MediaFile
import com.srisu.srisu.utils.MediaType
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.readBytes
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import kotlinx.atomicfu.TraceBase.None.append
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
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

    suspend fun getInterestList(): ResultHandler<InterestResponse?> {
        return httpClient.safeRequest<InterestResponse?> {
            url("${BASE_URL}api/auth/interests/")
            method = HttpMethod.Get
        }
    }

    suspend fun sendUpdateProfileRequest(
        userId: Int?,
        profileUpdateDTO: ProfileUpdateDTO,
        mediaFile: MediaFile?,
        gallery: List<MediaFile?>?
    ): ResultHandler<ProfileResponse?> {

        return httpClient.safeRequest<ProfileResponse?> {
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

                        mediaFile?.let {
                            if (mediaFile.fileBytes != null) {
                                append(
                                    "profile_photo",
                                    mediaFile.fileBytes,
                                    Headers.build {
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

                     /*   gallery?.forEachIndexed { index, galleryFile ->
                            if (userId != null) {

                                AppLogger.log("GALLER = ${Json.encodeToString(galleryFile)}")

                                when {
                                    //Case 1: Updating the photo
                                    galleryFile?.removed == true && galleryFile.fileBytes != null && galleryFile.id != null -> {
                                        // flag that photo as null
                                        append("user_photos[$index][user]", userId)
                                        append("user_photos[$index][id]", galleryFile.id)

//                                        append("user_photos[$index][removed]", true)

//                                        append("user_photos[$index][user]", userId)
                                        append(
                                            key = "user_photos[$index][photo]",
                                            value = galleryFile.fileBytes,
                                            Headers.build {
                                                append(
                                                    HttpHeaders.ContentDisposition,
                                                    "form-data; name=user_photos[$index][photo]; filename=${galleryFile.fileName}"
                                                )
                                                append(
                                                    HttpHeaders.ContentType,
                                                    galleryFile.mimeType ?: ""
                                                )
                                            }
                                        )


                                    }

                                    galleryFile?.removed == true && galleryFile.fileBytes == null && galleryFile.id != null -> {
                                        AppLogger.log("REMOVING PHOTO.. = $index")
                                        append("user_photos[${index-1}][user]", userId)
                                        append("user_photos[${index-1}][id]", galleryFile.id)
                                        append("user_photos[${index-1}][removed]", true)
                                    }

                                    // Case 3: New photo (upload file)
                                    galleryFile?.fileBytes != null -> {
                                        AppLogger.log("NEW PHOTO UPLOADING.. = $index")
                                        galleryFile.id?.let {
                                            append(
                                                "user_photos[$index][id]",
                                                it
                                            )
                                        }
                                        append("user_photos[$index][user]", userId)
                                        append(
                                            key = "user_photos[$index][photo]",
                                            value = galleryFile.fileBytes,
                                            Headers.build {
                                                append(
                                                    HttpHeaders.ContentDisposition,
                                                    "form-data; name=user_photos[$index][photo]; filename=${galleryFile.fileName}"
                                                )
                                                append(
                                                    HttpHeaders.ContentType,
                                                    galleryFile.mimeType ?: ""
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
*/

                        var appendIndex = 0

                        gallery?.forEach { galleryFile ->
                            if (userId != null && galleryFile != null) {
                                when {
                                    // Case 1: Updating the photo
                                    galleryFile.removed == true && galleryFile.fileBytes != null && galleryFile.id != null -> {
                                        append("user_photos[$appendIndex][user]", userId)
                                        append("user_photos[$appendIndex][id]", galleryFile.id)
                                        append(
                                            key = "user_photos[$appendIndex][photo]",
                                            value = galleryFile.fileBytes,
                                            Headers.build {
                                                append(
                                                    HttpHeaders.ContentDisposition,
                                                    "form-data; name=user_photos[$appendIndex][photo]; filename=${galleryFile.fileName}"
                                                )
                                                append(HttpHeaders.ContentType, galleryFile.mimeType ?: "")
                                            }
                                        )
                                        appendIndex++
                                    }

                                    // Case 2: Removing photo
                                    galleryFile.removed == true && galleryFile.fileBytes == null && galleryFile.id != null -> {
                                        append("user_photos[$appendIndex][user]", userId)
                                        append("user_photos[$appendIndex][id]", galleryFile.id)
                                        append("user_photos[$appendIndex][removed]", true)
                                        appendIndex++
                                    }

                                    // Case 3: New photo (upload)
                                    galleryFile.fileBytes != null -> {
                                        galleryFile.id?.let {
                                            append("user_photos[$appendIndex][id]", it)
                                        }
                                        append("user_photos[$appendIndex][user]", userId)
                                        append(
                                            key = "user_photos[$appendIndex][photo]",
                                            value = galleryFile.fileBytes,
                                            Headers.build {
                                                append(
                                                    HttpHeaders.ContentDisposition,
                                                    "form-data; name=user_photos[$appendIndex][photo]; filename=${galleryFile.fileName}"
                                                )
                                                append(HttpHeaders.ContentType, galleryFile.mimeType ?: "")
                                            }
                                        )
                                        appendIndex++
                                    }
                                }
                            }
                        }


                    }
                )
            )
        }
    }
}