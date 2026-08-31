package com.srisu.srisu.features.home.couple.domain.repository

import com.srisu.srisu.core.data.remote.ResultHandler
import com.srisu.srisu.features.home.couple.data.remote.api.CoupleProfileApiService
import com.srisu.srisu.features.home.couple.data.remote.dto.CoupleProfileWriteRequest
import com.srisu.srisu.features.home.couple.data.remote.response.CoupleProfileData
import com.srisu.srisu.utils.MediaFile

class CoupleProfileRepository(
    private val apiService: CoupleProfileApiService,
) {
    suspend fun getProfile(): ResultHandler<CoupleProfileData?> =
        apiService.getProfile()

    suspend fun createProfile(
        request: CoupleProfileWriteRequest,
    ): ResultHandler<CoupleProfileData?> = apiService.createProfile(request)

    suspend fun updateProfile(
        request: CoupleProfileWriteRequest,
    ): ResultHandler<CoupleProfileData?> = apiService.updateProfile(request)

    suspend fun uploadCoverPhoto(
        coverPhoto: MediaFile,
    ): ResultHandler<CoupleProfileData?> = apiService.uploadCoverPhoto(coverPhoto)
}
