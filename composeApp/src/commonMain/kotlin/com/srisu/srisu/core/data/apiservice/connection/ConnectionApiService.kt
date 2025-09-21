package com.srisu.srisu.core.data.apiservice.connection

import com.srisu.srisu.core.data.apiservice.base.BaseApiService.Companion.BASE_URL
import com.srisu.srisu.core.data.network.ResultHandler
import com.srisu.srisu.core.data.network.safeRequest
import com.srisu.srisu.core.data.response.connection.MyCrushListResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import io.ktor.http.HttpMethod

class ConnectionApiService(private val httpClient: HttpClient) {

    suspend fun getMyCrushList(
        page: Int,
        pageSize: Int
    ): ResultHandler<MyCrushListResponse?> {
        return httpClient.safeRequest<MyCrushListResponse?> {
            url("${BASE_URL}api/social/single-connection/sent-requests/")
            parameter("page", page)
            parameter("page_size", pageSize)

            method = HttpMethod.Get
        }
    }
}