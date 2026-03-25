package com.srisu.srisu.features.home.suggestions.data.api

import com.srisu.srisu.features.home.suggestions.data.dto.UserPreferenceDTO
import com.srisu.srisu.core.data.remote.ResultHandler
import com.srisu.srisu.core.data.remote.safeRequest
import com.srisu.srisu.core.data.remote.BaseApiService
import com.srisu.srisu.features.home.suggestions.data.response.UserPreferenceResponse
import com.srisu.srisu.features.home.suggestions.data.response.UserSuggestionResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType

class SuggestionApiService(private val httpClient: HttpClient) {

    companion object {
        const val CITY_ENDPOINT = "https://countriesnow.space/api/v0.1/countries/cities/q"
    }

    suspend fun getUserSuggestions(
        page: Int,
        pageSize: Int
    ): ResultHandler<UserSuggestionResponse?> {
        return httpClient.safeRequest<UserSuggestionResponse?> {
            url("${BaseApiService.Companion.BASE_URL}api/social/user-suggestions/")
            parameter("page", page)
            parameter("page_size", pageSize)

            method = HttpMethod.Companion.Get
        }
    }


    suspend fun getUserPreferences(): ResultHandler<UserPreferenceResponse?> {
        return httpClient.safeRequest<UserPreferenceResponse?> {
            url("${BaseApiService.Companion.BASE_URL}api/social/user-preferences/me/")
            contentType(ContentType.Application.Json)
            method = HttpMethod.Companion.Get
        }
    }

    suspend fun setUserPreferences(userPreferenceDTO: UserPreferenceDTO): ResultHandler<UserPreferenceResponse?> {
        return httpClient.safeRequest<UserPreferenceResponse?> {
            url("${BaseApiService.Companion.BASE_URL}api/social/user-preferences/")
            method = HttpMethod.Companion.Post
            setBody(
                userPreferenceDTO
            )
        }
    }

    suspend fun updateUserPreferences(
        userPreferenceDTO: UserPreferenceDTO,
        prefId: Int?
    ): ResultHandler<UserPreferenceResponse?> {
        return httpClient.safeRequest<UserPreferenceResponse?> {
            url("${BaseApiService.Companion.BASE_URL}api/social/user-preferences/${prefId}/")
            method = HttpMethod.Companion.Put
            contentType(ContentType.Application.Json)
            setBody(
                userPreferenceDTO
            )
        }
    }

}