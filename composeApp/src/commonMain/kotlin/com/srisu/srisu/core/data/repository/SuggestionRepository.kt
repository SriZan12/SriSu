package com.srisu.srisu.core.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import com.srisu.srisu.core.data.apiservice.SuggestionApiService
import com.srisu.srisu.core.data.dto.couple.CoupleConnectionDTO
import com.srisu.srisu.core.data.dto.couple.SingleConnectionDTO
import com.srisu.srisu.core.data.network.BasePagingSource
import com.srisu.srisu.core.data.network.ResultHandler
import com.srisu.srisu.core.data.response.suggestion.City
import com.srisu.srisu.core.data.response.suggestion.CoupleConnectionResponse
import com.srisu.srisu.core.data.response.suggestion.LoveRequestListResponse
import com.srisu.srisu.core.data.response.suggestion.SingleConnectionResponse
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
    suspend fun sendSingleConnectionRequest(
        senderNumber: String,
        receiverNumber: String
    ): ResultHandler<SingleConnectionResponse?> {
        return suggestionApiService.sendSingleConnectionRequest(
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
    suspend fun getCityList(country: String): ResultHandler<City?> {
        return suggestionApiService.getCitiesList(country)
    }

}