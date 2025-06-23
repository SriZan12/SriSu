package com.srisu.srisu.core.data.apiservice.suggestion

import com.srisu.srisu.core.data.apiservice.auth.AuthApiService.Companion.BASE_URL
import com.srisu.srisu.core.data.dto.couple.CoupleConnectionDTO
import com.srisu.srisu.core.data.dto.couple.SingleConnectionDTO
import com.srisu.srisu.core.data.network.ResultHandler
import com.srisu.srisu.core.data.network.safeRequest
import com.srisu.srisu.core.data.response.suggestion.CityResponse
import com.srisu.srisu.core.data.response.suggestion.CoupleConnectionResponse
import com.srisu.srisu.core.data.response.suggestion.LoveRequestListResponse
import com.srisu.srisu.core.data.response.suggestion.SingleConnectionResponse
import com.srisu.srisu.core.data.response.suggestion.UserSuggestionResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType

class SuggestionApiService(private val httpClient: HttpClient) {

    companion object {
        const val CITY_ENDPOINT = "https://countriesnow.space/api/v0.1/countries/cities/q"
    }

    suspend fun getUserSuggestions(
        page: Int,
        pageSize: Int
    ): ResultHandler<UserSuggestionResponse?> {
        return httpClient.safeRequest<UserSuggestionResponse?> {
            url("${BASE_URL}api/auth/user-suggestions/")
            parameter("page", page)
            parameter("page_size", pageSize)

            method = HttpMethod.Get
        }
    }

    suspend fun sendCoupleConnectionRequest(
        senderNumber: String,
        receiverNumber: String
    ): ResultHandler<CoupleConnectionResponse?> {

        val connectionRequest: HashMap<String, String> = HashMap()
        connectionRequest["sender_number"] = senderNumber
        connectionRequest["receiver_number"] = receiverNumber

        return httpClient.safeRequest<CoupleConnectionResponse?> {
            url("${BASE_URL}api/chat/connect-couple/")
            method = HttpMethod.Post
            setBody(connectionRequest)
        }
    }

    suspend fun updateCoupleConnectionRequestStatus(
        connectionId: Int,
        coupleConnectionDTO: CoupleConnectionDTO
    ): ResultHandler<CoupleConnectionResponse?> {
        return httpClient.safeRequest<CoupleConnectionResponse?> {
            url("${BASE_URL}api/chat/connect-couple/${connectionId}/")
            method = HttpMethod.Put
            setBody(coupleConnectionDTO)
        }
    }

    suspend fun updateSingleConnectionRequestStatus(
        connectionId: Int,
        singleConnectionDTO: SingleConnectionDTO
    ): ResultHandler<SingleConnectionResponse?> {

        return httpClient.safeRequest<SingleConnectionResponse?> {
            url("${BASE_URL}api/chat/connect-single/${connectionId}/")
            method = HttpMethod.Put
            setBody(singleConnectionDTO)
        }
    }

    suspend fun getSentLoveRequests(
        pageSize: Int,
    ): ResultHandler<LoveRequestListResponse?> {

        return httpClient.safeRequest<LoveRequestListResponse?> {
            url("${BASE_URL}api/chat/couple-connection/sent-requests/")
            parameter("page_size", pageSize)
            method = HttpMethod.Get
        }
    }

    suspend fun getLoveRequests(
        pageSize: Int
    ): ResultHandler<LoveRequestListResponse?> {

        return httpClient.safeRequest<LoveRequestListResponse?> {
            url("${BASE_URL}api/chat/couple-connection/received-requests/")
            parameter("page_size", pageSize)
            method = HttpMethod.Get
        }
    }

    suspend fun getCitiesList(country: String?): CityResponse? {
        return httpClient.get(CITY_ENDPOINT) {
            parameter("country", country)
            contentType(ContentType.Application.Json)
        }.body()
    }
}