package com.srisu.srisu.core.data.repository.connection

import com.srisu.srisu.core.data.apiservice.connection.ConnectionApiService
import com.srisu.srisu.core.data.dto.couple.SingleConnectionDTO
import com.srisu.srisu.core.data.network.ResultHandler
import com.srisu.srisu.core.data.response.connection.SingleConnectionResponse
import com.srisu.srisu.core.logger.AppLogger

class ConnectionRepository(
    private val connectionApiService: ConnectionApiService,
) {

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
}