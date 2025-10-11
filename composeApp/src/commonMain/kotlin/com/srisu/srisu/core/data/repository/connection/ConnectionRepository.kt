package com.srisu.srisu.core.data.repository.connection

import com.srisu.srisu.core.data.apiservice.base.BaseApiService
import com.srisu.srisu.core.data.apiservice.connection.ConnectionApiService
import com.srisu.srisu.core.data.dto.couple.SingleConnectionDTO
import com.srisu.srisu.core.data.network.ResultHandler
import com.srisu.srisu.core.data.response.connection.MyCrushListResponse
import com.srisu.srisu.core.data.response.suggestion.SingleConnectionResponse
import com.srisu.srisu.core.logger.AppLogger
import kotlin.math.sin

class ConnectionRepository(
    private val connectionApiService: ConnectionApiService,
) {

    suspend fun getMyCrushList(
        page: Int,
        pageSize: Int
    ): ResultHandler<MyCrushListResponse?> {
        AppLogger.log("INSIDE CONNECTION REPOSITORY getMyCrushList")
        return connectionApiService.getMyCrushList(
            page = page,
            pageSize = pageSize
        )
    }

    suspend fun cancelCrushRequest(
        crushRequestId: Int?,
        singleConnectionDTO: SingleConnectionDTO
    ): ResultHandler<SingleConnectionResponse?> {
        return connectionApiService.cancelCrushRequest(
            crushRequestId = crushRequestId,
            singleConnectionDTO = singleConnectionDTO
        )
    }
}