package com.srisu.srisu.core.data.apiservice.profile

import com.srisu.srisu.core.data.apiservice.base.BaseApiService.Companion.BASE_URL
import com.srisu.srisu.core.data.dto.authdto.ProfileSetupDTO
import com.srisu.srisu.core.data.dto.profile.ProfileUpdateDTO
import com.srisu.srisu.core.data.network.ResultHandler
import com.srisu.srisu.core.data.network.safeRequest
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

                        // Actual file data (profile_picture)
                        AppLogger.log("MEDIA FILE = ${Json.encodeToString(mediaFile)}}")
                        /* mediaFile?.fileBytes?.let { fileBytes ->
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
                         }*/

                        mediaFile?.let {
                            uploadImage(mediaFile)
                        }

                        profileUpdateDTO.userInterests?.let { userInterests ->
                            userInterests.forEachIndexed { index, interestName ->
                                if (!interestName.isNullOrEmpty() && userId != null) {
                                    append("user_interests[$index][user]", userId)
                                    append("user_interests[$index][name]", interestName)
                                }
                            }
                        }

/*
                        gallery?.let { userPhotos ->
                            userPhotos.forEachIndexed { index, galleryFile ->
                                galleryFile?.fileBytes?.let { fileBytes ->
                                    if (userId != null) {
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
*/
                    }
                )
            )
        }
    }

    suspend fun createMediaFileFromUrl(url: String, fileName: String = "profile_photo.jpg"): MediaFile {
        // Initialize Ktor client
        val client = HttpClient(CIO)

        return withContext(Dispatchers.IO) {
            try {
                // Download the image from the URL
                val response = client.get(url)
                val fileBytes = response.readRawBytes()
                val fileSize = fileBytes.size.toLong()

                // Determine MIME type from response headers or fallback to JPEG
                val mimeType = response.contentType()?.toString() ?: "image/jpeg"

                // Create MediaFile object
                MediaFile(
                    fileName = fileName,
                    mimeType = mimeType,
                    fileSize = fileSize,
                    fileBytes = fileBytes,
                    fileType = MediaType.IMAGE_ONLY
                )
            } catch (e: Exception) {
                throw IllegalStateException("Failed to download image from URL: ${e.message}")
            } finally {
                client.close()
            }
        }
    }

    fun uploadImage(mediaFile: MediaFile) {
        // Your existing multipart upload logic
        append(
            "profile_photo",
            mediaFile.fileBytes,
            Headers.build {
                append(
                    HttpHeaders.ContentDisposition,
                    "form-data; name=profile_photo; filename=${mediaFile.fileName}"
                )
                append(HttpHeaders.ContentType, mediaFile.mimeType)
            }
        )
    }
}