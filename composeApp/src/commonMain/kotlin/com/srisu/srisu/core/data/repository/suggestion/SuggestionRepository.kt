package com.srisu.srisu.core.data.repository.suggestion

import androidx.paging.Pager
import androidx.paging.PagingConfig
import com.srisu.srisu.core.data.apiservice.base.BaseApiService
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
    private val suggestionApiService: SuggestionApiService,
    private val baseApiService: BaseApiService,
) {

    suspend fun getUserSuggestions(
        pageSize: Int,
        page: Int
    ): ResultHandler<UserSuggestionResponse?> {
        return suggestionApiService.getUserSuggestions(pageSize = pageSize, page = page)
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

    @Throws(Exception::class)
    suspend fun getCityList(country: String?): CityResponse? {
        return baseApiService.getCitiesList(country)
    }

}