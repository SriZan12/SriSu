package com.srisu.srisu.core.data.repository.profile

import com.srisu.srisu.core.data.remote.apiservice.base.BaseApiService
import com.srisu.srisu.core.data.remote.apiservice.profile.ProfileApiService
import com.srisu.srisu.core.data.dto.profile.ProfileUpdateDTO
import com.srisu.srisu.core.data.network.ResultHandler
import com.srisu.srisu.features.auth.data.remote.response.InterestResponse
import com.srisu.srisu.features.auth.data.remote.response.ProfileResponse
import com.srisu.srisu.core.data.response.suggestion.CityResponse
import com.srisu.srisu.core.data.response.suggestion.SingleConnectionResponse
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