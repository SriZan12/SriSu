package com.srisu.srisu.core.data.repository.suggestion

import androidx.paging.Pager
import androidx.paging.PagingConfig
import com.srisu.srisu.core.data.apiservice.suggestion.SuggestionApiService
import com.srisu.srisu.core.data.dto.couple.CoupleConnectionDTO
import com.srisu.srisu.core.data.dto.couple.SingleConnectionDTO
import com.srisu.srisu.core.data.dto.suggestion.UserPreferenceDTO
import com.srisu.srisu.core.data.network.BasePagingSource
import com.srisu.srisu.core.data.network.ResultHandler
import com.srisu.srisu.core.data.response.suggestion.CityResponse
import com.srisu.srisu.core.data.response.suggestion.CoupleConnectionResponse
import com.srisu.srisu.core.data.response.suggestion.LoveRequestListResponse
import com.srisu.srisu.core.data.response.suggestion.SingleConnectionResponse
import com.srisu.srisu.core.data.response.suggestion.UserPreferenceResponse
import com.srisu.srisu.core.data.response.suggestion.UserSuggestionResponse

class SuggestionRepository(
    private val suggestionApiService: SuggestionApiService
) {

    @Throws(Exception::class)
    suspend fun sendCoupleConnectionRequest(
        senderNumber: String,
        receiverNumber: String
    ): ResultHandler<CoupleConnectionResponse?> {
        return suggestionApiService.sendCoupleConnectionRequest(
            senderNumber = senderNumber,
            receiverNumber = receiverNumber
        )
    }

    @Throws(Exception::class)
    suspend fun updateCoupleConnectionRequestStatus(
        connectionId: Int,
        coupleConnectionDTO: CoupleConnectionDTO
    ): ResultHandler<CoupleConnectionResponse?> {
        return suggestionApiService.updateCoupleConnectionRequestStatus(
            connectionId = connectionId,
            coupleConnectionDTO = coupleConnectionDTO
        )
    }

    @Throws(Exception::class)
    suspend fun updateSingleConnectionRequestStatus(
        connectionId: Int,
        singleConnectionDTO: SingleConnectionDTO,
    ): ResultHandler<SingleConnectionResponse?> {
        return suggestionApiService.updateSingleConnectionRequestStatus(
            connectionId = connectionId,
            singleConnectionDTO = singleConnectionDTO
        )
    }

    suspend fun getUserSuggestions(
        pageSize: Int,
        page: Int
    ): ResultHandler<UserSuggestionResponse?> {
        return suggestionApiService.getUserSuggestions(pageSize = pageSize, page = page)
    }

    @Throws(Exception::class)
    suspend fun sendSingleConnectionRequest(
        senderNumber: String?,
        receiverNumber: String?
    ): ResultHandler<SingleConnectionResponse?> {
        return suggestionApiService.sendSingleConnectionRequest(
            senderNumber = senderNumber,
            receiverNumber = receiverNumber
        )
    }

    suspend fun getUserPreferences(): ResultHandler<UserPreferenceResponse?> {
        return suggestionApiService.getUserPreferences()
    }

    suspend fun setUserPreferences(userPreferenceDTO: UserPreferenceDTO): ResultHandler<UserPreferenceResponse?> {
        return suggestionApiService.setUserPreferences(userPreferenceDTO)
    }

    suspend fun updateUserPreferences(
        userPreferenceDTO: UserPreferenceDTO,
        prefId: Int?
    ): ResultHandler<UserPreferenceResponse?> {
        return suggestionApiService.updateUserPreferences(
            userPreferenceDTO = userPreferenceDTO,
            prefId = prefId
        )
    }

    fun getSentLoveRequests(): Pager<Int, LoveRequestListResponse.Result> {
        return Pager(
            config = PagingConfig(
                pageSize = 20,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                BasePagingSource { pageSize ->
                    val resultHandler = suggestionApiService.getSentLoveRequests(pageSize)


                    var items: List<LoveRequestListResponse.Result> = emptyList()

                    resultHandler
                        .onSuccess { response, _ ->
                            items = response?.results?.filterNotNull() ?: emptyList()
                        }
                        .onError { error, errorType ->
                            throw Exception("API Error: $error, Type: $errorType")
                        }

                    items
                }
            }
        )
    }

    @Throws(Exception::class)
    fun getLoveRequests(): Pager<Int, LoveRequestListResponse.Result> {
        return Pager(
            config = PagingConfig(
                pageSize = 20,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                BasePagingSource { pageSize ->
                    val resultHandler = suggestionApiService.getLoveRequests(pageSize)

                    var items: List<LoveRequestListResponse.Result> = emptyList()

                    resultHandler
                        .onSuccess { response, _ ->
                            items = response?.results?.filterNotNull() ?: emptyList()
                        }
                        .onError { error, errorType ->
                            throw Exception("API Error: $error, Type: $errorType")
                        }

                    items
                }
            }
        )
    }

    @Throws(Exception::class)
    suspend fun getCityList(country: String?): CityResponse? {
        return suggestionApiService.getCitiesList(country)
    }

}