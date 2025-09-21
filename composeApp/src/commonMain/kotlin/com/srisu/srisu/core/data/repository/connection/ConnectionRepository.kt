package com.srisu.srisu.core.data.repository.connection

import com.srisu.srisu.core.data.apiservice.base.BaseApiService
import com.srisu.srisu.core.data.apiservice.connection.ConnectionApiService
import com.srisu.srisu.core.data.network.ResultHandler
import com.srisu.srisu.core.data.response.connection.MyCrushListResponse
import com.srisu.srisu.core.logger.AppLogger

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
}