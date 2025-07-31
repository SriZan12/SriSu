package com.srisu.srisu.core.data.repository.profile

import com.srisu.srisu.core.data.apiservice.profile.ProfileApiService
import com.srisu.srisu.core.data.network.ResultHandler
import com.srisu.srisu.core.data.response.suggestion.CityResponse
import com.srisu.srisu.core.data.response.suggestion.SingleConnectionResponse

class ProfileRepository(private val profileApiService: ProfileApiService) {

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
//    @Throws(Exception::class)
//    suspend fun getCityList(country: String?): CityResponse? {
//        return suggestionApiService.getCitiesList(country)
//    }
}