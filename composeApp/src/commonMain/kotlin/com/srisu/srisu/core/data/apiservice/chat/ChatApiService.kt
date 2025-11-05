package com.srisu.srisu.core.data.apiservice.chat

import com.srisu.srisu.core.data.apiservice.base.BaseApiService.Companion.BASE_URL
import com.srisu.srisu.core.data.network.ResultHandler
import com.srisu.srisu.core.data.network.safeRequest
import com.srisu.srisu.core.data.response.chat.FindYourPartnerResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import io.ktor.http.HttpMethod

class ChatApiService(private val httpClient: HttpClient) {

    suspend fun sendFindYourPartnerRequest(partnerNumber: String): ResultHandler<FindYourPartnerResponse?> {
        return httpClient.safeRequest<FindYourPartnerResponse?> {
            url("${BASE_URL}api/social/find-partner/")
            parameter("phone_number", partnerNumber)
            method = HttpMethod.Get
        }
    }
}