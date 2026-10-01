package com.srisu.srisu.features.home.connection.domain.repository

import com.srisu.srisu.features.home.connection.coupleconnection.data.remote.dto.CoupleConnectionDTO
import com.srisu.srisu.core.data.remote.ResultHandler
import com.srisu.srisu.features.home.connection.data.remote.response.CoupleConnectionRequestResponse
import com.srisu.srisu.features.home.connection.data.remote.response.CoupleConnectionResponse
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.features.chat.data.remote.response.FindYourPartnerResponse
import com.srisu.srisu.features.home.connection.data.remote.api.ConnectionApiService
import com.srisu.srisu.features.home.connection.data.remote.response.HaveCoupleConnectionResponse

class ConnectionRepository(
    private val connectionApiService: ConnectionApiService,
) {



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