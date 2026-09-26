package com.srisu.srisu.features.chat.data.remote.api

import com.srisu.srisu.core.data.remote.BaseApiService
import com.srisu.srisu.core.data.remote.ResultHandler
import com.srisu.srisu.core.data.remote.safeRequest
import com.srisu.srisu.features.chat.data.remote.response.ChatMediaResponse
import com.srisu.srisu.utils.MediaFile
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod

class ChatApiService(private val httpClient: HttpClient, private val environment: com.srisu.srisu.core.config.ApiEnvironment = com.srisu.srisu.core.config.ApiEnvironment.configured()) {

    suspend fun rooms(cursor: String? = null): ResultHandler<com.srisu.srisu.features.chat.data.remote.response.ChatRoomPage?> = httpClient.safeRequest(readRetries = 1) {
        url("${environment.baseUrl}api/chat/rooms/")
        method = HttpMethod.Get
        url.parameters.append("limit", "20")
        cursor?.let { url.parameters.append("cursor", it) }
    }

    suspend fun history(roomId: String, cursor: Long? = null): ResultHandler<com.srisu.srisu.features.chat.data.remote.response.ChatHistoryPage?> = httpClient.safeRequest(readRetries = 1) {
        require(roomId.matches(Regex("[0-9a-fA-F-]{36}")))
        url("${environment.baseUrl}api/chat/rooms/$roomId/messages/")
        method = HttpMethod.Get
        url.parameters.append("limit", "20")
        cursor?.let { url.parameters.append("cursor", it.toString()) }
    }

    suspend fun uploadMedias(
        medias: List<MediaFile?>?
    ): ResultHandler<ChatMediaResponse?> {

        return httpClient.safeRequest {

            url(urlString = "${environment.baseUrl}api/chat/media-upload/")
            method = HttpMethod.Companion.Post

            setBody(
                MultiPartFormDataContent(
                    parts = formData {
                        medias
                            ?.filterNotNull()
                            ?.forEach { mediaFile ->
                                mediaFile.fileBytes?.let { fileBytes ->
                                    append(
                                        key = "file",
                                        value = fileBytes,
                                        Headers.Companion.build {
                                            append(
                                                HttpHeaders.ContentDisposition,
                                                "form-data; name=file; filename=${mediaFile.fileName}"
                                            )
                                            append(
                                                HttpHeaders.ContentType,
                                                mediaFile.mimeType ?: "application/octet-stream"
                                            )
                                        }
                                    )
                                }

                            }
                    }
                )
            )
        }
    }

}