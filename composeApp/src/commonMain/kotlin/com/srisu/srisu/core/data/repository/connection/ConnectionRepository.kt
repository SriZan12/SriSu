package com.srisu.srisu.core.data.repository.connection

import com.srisu.srisu.core.data.apiservice.connection.ConnectionApiService
import com.srisu.srisu.core.data.dto.couple.CoupleConnectionDTO
import com.srisu.srisu.core.data.dto.couple.SingleConnectionDTO
import com.srisu.srisu.core.data.network.ResultHandler
import com.srisu.srisu.core.data.response.chat.FindYourPartnerResponse
import com.srisu.srisu.core.data.response.connection.CoupleConnectionRequestResponse
import com.srisu.srisu.core.data.response.connection.SingleConnectionResponse
import com.srisu.srisu.core.data.response.suggestion.CoupleConnectionResponse
import com.srisu.srisu.core.logger.AppLogger

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
        loveRequestId: Int?,
        coupleConnectionDTO: CoupleConnectionDTO
    ): ResultHandler<CoupleConnectionResponse?> {
        return connectionApiService.updateCoupleConnectionRequestStatus(
            connectionId = loveRequestId,
            coupleConnectionDTO = coupleConnectionDTO
        )
    }
}