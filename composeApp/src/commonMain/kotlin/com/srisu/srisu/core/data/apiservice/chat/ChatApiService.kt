package com.srisu.srisu.core.data.apiservice.chat

import com.srisu.srisu.core.data.apiservice.base.BaseApiService.Companion.BASE_URL
import com.srisu.srisu.core.data.network.ResultHandler
import com.srisu.srisu.core.data.network.safeRequest
import com.srisu.srisu.core.data.response.chat.ChatMediaResponse
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

class ChatApiService(private val httpClient: HttpClient) {

    suspend fun uploadMedias(
        medias: List<MediaFile?>?
    ): ResultHandler<ChatMediaResponse?> {

        return httpClient.safeRequest {

            url(urlString = "${BASE_URL}api/chat/media-upload/")
            method = HttpMethod.Post

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
                                        Headers.build {
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