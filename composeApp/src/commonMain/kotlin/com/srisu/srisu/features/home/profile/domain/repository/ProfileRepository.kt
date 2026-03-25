package com.srisu.srisu.features.home.profile.domain.repository

import com.srisu.srisu.features.home.profile.data.dto.ProfileUpdateDTO
import com.srisu.srisu.core.data.remote.ResultHandler
import com.srisu.srisu.core.data.remote.BaseApiService
import com.srisu.srisu.features.home.profile.data.remote.api.ProfileApiService
import com.srisu.srisu.features.home.suggestions.data.response.CityResponse
import com.srisu.srisu.features.home.suggestions.data.response.SingleConnectionResponse
import com.srisu.srisu.features.auth.data.remote.response.InterestResponse
import com.srisu.srisu.features.auth.data.remote.response.ProfileResponse
import com.srisu.srisu.utils.MediaFile

class ProfileRepository(
    private val profileApiService: ProfileApiService,
    private val baseApiService: BaseApiService
) {

    @Throws(Exception::class)
    suspend fun sendSingleConnectionRequest(
        senderNumber: String?,
        receiverNumber: String?
    ): ResultHandler<SingleConnectionResponse?> {
        return profileApiService.sendSingleConnectionRequest(
            senderNumber = senderNumber,
            receiverNumber = receiverNumber
        )
    }

    @Throws(Exception::class)
    suspend fun getCityList(country: String?): CityResponse? {
        return baseApiService.getCitiesList(country)
    }

    @Throws(Exception::class)
    suspend fun getInterestList(): ResultHandler<InterestResponse?> {
        return profileApiService.getInterestList()
    }

    @Throws(Exception::class)
    suspend fun sendUpdateProfileRequest(
        userId: Long?,
        profileUpdateDTO: ProfileUpdateDTO,
        profilePhoto: MediaFile?,
        gallery: List<MediaFile?>?
    ): ResultHandler<ProfileResponse?> {
        return profileApiService.sendUpdateProfileRequest(
            userId = userId,
            profileUpdateDTO = profileUpdateDTO,
            mediaFile = profilePhoto,
            gallery = gallery
        )
    }

    @Throws(Exception::class)
    suspend fun getProfile(): ResultHandler<ProfileResponse?> {
        return profileApiService.getProfile()
    }
}