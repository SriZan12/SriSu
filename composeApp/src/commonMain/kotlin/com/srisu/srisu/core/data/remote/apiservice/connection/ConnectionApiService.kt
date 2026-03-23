package com.srisu.srisu.core.data.remote.apiservice.connection

import com.srisu.srisu.core.data.remote.apiservice.base.BaseApiService.Companion.BASE_URL
import com.srisu.srisu.core.data.dto.couple.CoupleConnectionDTO
import com.srisu.srisu.core.data.dto.couple.SingleConnectionDTO
import com.srisu.srisu.core.data.network.ResultHandler
import com.srisu.srisu.core.data.network.safeRequest
import com.srisu.srisu.features.chat.data.remote.response.FindYourPartnerResponse
import com.srisu.srisu.core.data.response.connection.CoupleConnectionRequestResponse
import com.srisu.srisu.core.data.response.connection.SingleConnectionResponse
import com.srisu.srisu.core.data.response.suggestion.CoupleConnectionResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.HttpMethod

class ConnectionApiService(private val httpClient: HttpClient) {

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

    suspend fun sendFindYourPartnerRequest(partnerNumber: String): ResultHandler<FindYourPartnerResponse?> {
        return httpClient.safeRequest<FindYourPartnerResponse?> {
            url("${BASE_URL}api/social/find-partner/")
            parameter("phone_number", partnerNumber)
            method = HttpMethod.Get
        }
    }

    suspend fun sendCoupleConnectionRequest(
        senderNumber: String?,
        receiverNumber: String?
    ): ResultHandler<CoupleConnectionResponse?> {

        val connectionRequest: HashMap<String, String> = HashMap()
        connectionRequest["sender_number"] = senderNumber ?: ""
        connectionRequest["receiver_number"] = receiverNumber ?: ""

        return httpClient.safeRequest<CoupleConnectionResponse?> {
            url("${BASE_URL}api/social/connect-couple/")
            method = HttpMethod.Post
            setBody(connectionRequest)
        }
    }

    suspend fun updateCoupleConnectionRequestStatus(
        connectionId: Int?,
        coupleConnectionDTO: CoupleConnectionDTO
    ): ResultHandler<CoupleConnectionResponse?> {
        return httpClient.safeRequest<CoupleConnectionResponse?> {
            url("${BASE_URL}api/social/connect-couple/${connectionId}/")
            method = HttpMethod.Put
            setBody(coupleConnectionDTO)
        }
    }

    suspend fun updateSingleConnectionRequestStatus(
        connectionId: Int?,
        singleConnectionDTO: SingleConnectionDTO
    ): ResultHandler<SingleConnectionResponse?> {

        return httpClient.safeRequest<SingleConnectionResponse?> {
            url("${BASE_URL}api/social/connect-single/${connectionId}/")
            method = HttpMethod.Put
            setBody(singleConnectionDTO)
        }
    }

    suspend fun getSentLoveRequests(
        pageSize: Int,
        page: Int,
    ): ResultHandler<CoupleConnectionRequestResponse?> {

        return httpClient.safeRequest<CoupleConnectionRequestResponse?> {
            url("${BASE_URL}api/social/couple-connection/sent-requests/")
            parameter("page", page)
            parameter("page_size", pageSize)
            method = HttpMethod.Get
        }
    }

    suspend fun getLoveRequests(
        page: Int,
        pageSize: Int
    ): ResultHandler<CoupleConnectionRequestResponse?> {

        return httpClient.safeRequest<CoupleConnectionRequestResponse?> {
            url("${BASE_URL}api/social/couple-connection/received-requests/")
            parameter("page", page)
            parameter("page_size", pageSize)
            method = HttpMethod.Get
        }
    }

    suspend fun getMyCrushList(
        page: Int,
        pageSize: Int
    ): ResultHandler<SingleConnectionResponse?> {
        return httpClient.safeRequest<SingleConnectionResponse?> {
            url("${BASE_URL}api/social/single-connection/sent-requests/")
            parameter("page", page)
            parameter("page_size", pageSize)

            method = HttpMethod.Get
        }
    }

    suspend fun getCrushOnMeList(
        page: Int,
        pageSize: Int
    ): ResultHandler<SingleConnectionResponse?> {
        return httpClient.safeRequest<SingleConnectionResponse?> {
            url("${BASE_URL}api/social/single-connection/received-requests/")
            parameter("page", page)
            parameter("page_size", pageSize)

            method = HttpMethod.Get
        }
    }

    suspend fun updateCrushRequest(
        crushRequestId: Int?,
        singleConnectionDTO: SingleConnectionDTO
    ): ResultHandler<SingleConnectionResponse?> {
        return httpClient.safeRequest<SingleConnectionResponse?> {
            url("${BASE_URL}api/social/connect-single/${crushRequestId}/")
            setBody(singleConnectionDTO)
            method = HttpMethod.Put
        }
    }

}