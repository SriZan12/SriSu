package com.srisu.srisu.features.chat.data.remote.api

import com.srisu.srisu.core.data.remote.NetworkConfig
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

class ChatApiService(
    private val httpClient: HttpClient,
    private val networkConfig: NetworkConfig,
) {

    suspend fun uploadMedias(
        medias: List<MediaFile?>?
    ): ResultHandler<ChatMediaResponse?> {

        return httpClient.safeRequest {

            url(urlString = "${networkConfig.apiBaseUrl}api/chat/media-upload/")
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