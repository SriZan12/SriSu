package com.srisu.srisu.features.home.connection.data.remote.api

import com.srisu.srisu.features.home.connection.coupleconnection.data.remote.dto.CoupleConnectionDTO
import com.srisu.srisu.features.home.connection.coupleconnection.data.remote.dto.SingleConnectionDTO
import com.srisu.srisu.core.data.remote.ResultHandler
import com.srisu.srisu.core.data.remote.safeRequest
import com.srisu.srisu.core.data.remote.BaseApiService
import com.srisu.srisu.features.home.connection.data.remote.response.CoupleConnectionRequestResponse
import com.srisu.srisu.features.home.connection.data.remote.response.SingleConnectionResponse
import com.srisu.srisu.features.home.suggestions.data.response.CoupleConnectionResponse
import com.srisu.srisu.features.chat.data.remote.response.FindYourPartnerResponse
import com.srisu.srisu.features.home.connection.data.remote.response.HaveCoupleConnectionResponse
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
            url("${BaseApiService.BASE_URL}api/social/connect-single/")
            method = HttpMethod.Post
            setBody(connectionRequest)
        }
    }

    suspend fun sendFindYourPartnerRequest(partnerNumber: String): ResultHandler<FindYourPartnerResponse?> {
        return httpClient.safeRequest<FindYourPartnerResponse?> {
            url("${BaseApiService.BASE_URL}api/social/find-partner/")
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
            url("${BaseApiService.BASE_URL}api/social/connect-couple/")
            method = HttpMethod.Post
            setBody(connectionRequest)
        }
    }

    suspend fun updateCoupleConnectionRequestStatus(
        connectionId: Long?,
        coupleConnectionDTO: CoupleConnectionDTO
    ): ResultHandler<CoupleConnectionResponse?> {
        return httpClient.safeRequest<CoupleConnectionResponse?> {
            url("${BaseApiService.BASE_URL}api/social/connect-couple/${connectionId}/")
            method = HttpMethod.Put
            setBody(coupleConnectionDTO)
        }
    }

    suspend fun updateSingleConnectionRequestStatus(
        connectionId: Long?,
        singleConnectionDTO: SingleConnectionDTO
    ): ResultHandler<SingleConnectionResponse?> {

        return httpClient.safeRequest<SingleConnectionResponse?> {
            url("${BaseApiService.BASE_URL}api/social/connect-single/${connectionId}/")
            method = HttpMethod.Put
            setBody(singleConnectionDTO)
        }
    }

    suspend fun getSentLoveRequests(
        pageSize: Int,
        page: Int,
    ): ResultHandler<CoupleConnectionRequestResponse?> {

        return httpClient.safeRequest<CoupleConnectionRequestResponse?> {
            url("${BaseApiService.BASE_URL}api/social/couple-connection/sent-requests/")
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
            url("${BaseApiService.BASE_URL}api/social/couple-connection/received-requests/")
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
            url("${BaseApiService.BASE_URL}api/social/single-connection/sent-requests/")
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
            url("${BaseApiService.BASE_URL}api/social/single-connection/received-requests/")
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
            url("${BaseApiService.BASE_URL}api/social/connect-single/${crushRequestId}/")
            setBody(singleConnectionDTO)
            method = HttpMethod.Put
        }
    }

    suspend fun haveCoupleConnectionRequested(): ResultHandler<HaveCoupleConnectionResponse?> {
        return httpClient.safeRequest<HaveCoupleConnectionResponse?> {
            url("${BaseApiService.BASE_URL}api/social/have-couple-connection-requested/")
            method = HttpMethod.Get
        }
    }

}