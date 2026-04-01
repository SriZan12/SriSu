package com.srisu.srisu.features.home.suggestions.domain.repository

import com.srisu.srisu.core.data.remote.BaseApiService
import com.srisu.srisu.core.data.remote.ResultHandler
import com.srisu.srisu.features.home.suggestions.data.api.SuggestionApiService
import com.srisu.srisu.features.home.suggestions.data.dto.UserPreferenceDTO
import com.srisu.srisu.features.home.suggestions.data.response.CityResponse
import com.srisu.srisu.features.home.suggestions.data.response.UserPreferenceResponse
import com.srisu.srisu.features.home.suggestions.data.response.UserSuggestionResponse


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

    suspend fun getSuggestionProfile(userId: Int?): ResultHandler<UserSuggestionResponse.Result?> {
        return suggestionApiService.getSuggestionProfile(userId = userId)
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