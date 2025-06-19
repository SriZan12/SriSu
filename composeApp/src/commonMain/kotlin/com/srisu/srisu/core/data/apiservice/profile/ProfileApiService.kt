package com.srisu.srisu.core.data.apiservice.profile

import com.srisu.srisu.core.data.apiservice.auth.AuthApiService.Companion.BASE_URL
import com.srisu.srisu.core.data.network.ResultHandler
import com.srisu.srisu.core.data.network.safeRequest
import com.srisu.srisu.core.data.response.suggestion.SingleConnectionResponse
import com.srisu.srisu.core.logger.AppLogger
import io.ktor.client.HttpClient
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.HttpMethod

class ProfileApiService(private val httpClient: HttpClient) {

    suspend fun sendSingleConnectionRequest(
        senderNumber: String?,
        receiverNumber: String?
    ): ResultHandler<SingleConnectionResponse?> {

        AppLogger.log("INSIDE SEND SINGLE CONNECTION API ")

        val connectionRequest: HashMap<String, String> = HashMap()
        connectionRequest["sender_number"] = senderNumber ?: ""
        connectionRequest["receiver_number"] = receiverNumber ?: ""

        return httpClient.safeRequest<SingleConnectionResponse?> {
            url("${BASE_URL}api/chat/connect-single/")
            method = HttpMethod.Post
            setBody(connectionRequest)
        }
    }
}