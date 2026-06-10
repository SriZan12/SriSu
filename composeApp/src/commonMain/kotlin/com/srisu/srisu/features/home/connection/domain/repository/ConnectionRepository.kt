package com.srisu.srisu.features.home.connection.domain.repository

import com.srisu.srisu.features.home.connection.coupleconnection.data.remote.dto.CoupleConnectionDTO
import com.srisu.srisu.features.home.connection.coupleconnection.data.remote.dto.SingleConnectionDTO
import com.srisu.srisu.core.data.remote.ResultHandler
import com.srisu.srisu.features.home.connection.data.remote.response.CoupleConnectionRequestResponse
import com.srisu.srisu.features.home.connection.data.remote.response.SingleConnectionResponse
import com.srisu.srisu.features.home.suggestions.data.response.CoupleConnectionResponse
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.features.chat.data.remote.response.FindYourPartnerResponse
import com.srisu.srisu.features.home.connection.data.remote.api.ConnectionApiService
import com.srisu.srisu.features.home.connection.data.remote.response.HaveCoupleConnectionResponse

class ConnectionRepository(
    private val connectionApiService: ConnectionApiService,
) {

    suspend fun sendSingleConnectionRequest(
        senderNumber: String?,
        receiverNumber: String?
    ): ResultHandler<SingleConnectionResponse?> {
        return connectionApiService.sendSingleConnectionRequest(
            senderNumber = senderNumber,
            receiverNumber = receiverNumber
        )
    }

    suspend fun sendFindYourPartnerRequest(partnerNumber: String): ResultHandler<FindYourPartnerResponse?> {
        return connectionApiService.sendFindYourPartnerRequest(partnerNumber = partnerNumber)
    }

    suspend fun sendHaveCoupleConnectionRequested(): ResultHandler<HaveCoupleConnectionResponse?> {
        return connectionApiService.haveCoupleConnectionRequested()
    }

    suspend fun sendCoupleConnectionRequest(
        senderNumber: String?,
        receiverNumber: String?
    ): ResultHandler<CoupleConnectionResponse?> {
        return connectionApiService.sendCoupleConnectionRequest(
            receiverNumber = receiverNumber,
            senderNumber = senderNumber
        )
    }

    suspend fun getSentLoveRequests(
        pageSize: Int,
        page: Int
    ): ResultHandler<CoupleConnectionRequestResponse?> {
        return connectionApiService.getSentLoveRequests(
            pageSize = pageSize,
            page = page
        )
    }

    suspend fun getLoveRequests(
        pageSize: Int,
        page: Int
    ): ResultHandler<CoupleConnectionRequestResponse?> {
        return connectionApiService.getLoveRequests(
            pageSize = pageSize,
            page = page
        )
    }


    suspend fun getMyCrushList(
        page: Int,
        pageSize: Int
    ): ResultHandler<SingleConnectionResponse?> {
        AppLogger.log("INSIDE CONNECTION REPOSITORY getMyCrushList")
        return connectionApiService.getMyCrushList(
            page = page,
            pageSize = pageSize
        )
    }

    suspend fun getCrushOnMeRequest(
        page: Int,
        pageSize: Int
    ): ResultHandler<SingleConnectionResponse?> {
        return connectionApiService.getCrushOnMeList(
            page = page,
            pageSize = pageSize
        )
    }

    suspend fun updateCrushRequest(
        crushRequestId: Int?,
        singleConnectionDTO: SingleConnectionDTO
    ): ResultHandler<SingleConnectionResponse?> {
        return connectionApiService.updateCrushRequest(
            crushRequestId = crushRequestId,
            singleConnectionDTO = singleConnectionDTO
        )
    }

    suspend fun updateLoveRequest(
        loveRequestId: Long?,
        coupleConnectionDTO: CoupleConnectionDTO
    ): ResultHandler<CoupleConnectionResponse?> {
        return connectionApiService.updateCoupleConnectionRequestStatus(
            connectionId = loveRequestId,
            coupleConnectionDTO = coupleConnectionDTO
        )
    }
}