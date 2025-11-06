package com.srisu.srisu.core.data.apiservice.suggestion

import com.srisu.srisu.core.data.apiservice.base.BaseApiService.Companion.BASE_URL
import com.srisu.srisu.core.data.dto.couple.CoupleConnectionDTO
import com.srisu.srisu.core.data.dto.couple.SingleConnectionDTO
import com.srisu.srisu.core.data.dto.suggestion.UserPreferenceDTO
import com.srisu.srisu.core.data.network.ResultHandler
import com.srisu.srisu.core.data.network.safeRequest
import com.srisu.srisu.core.data.response.suggestion.CoupleConnectionResponse
import com.srisu.srisu.core.data.response.suggestion.LoveRequestListResponse
import com.srisu.srisu.core.data.response.suggestion.SingleConnectionResponse
import com.srisu.srisu.core.data.response.suggestion.UserPreferenceResponse
import com.srisu.srisu.core.data.response.suggestion.UserSuggestionResponse
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
            url("${BASE_URL}api/social/user-suggestions/")
            parameter("page", page)
            parameter("page_size", pageSize)

            method = HttpMethod.Get
        }
    }


    suspend fun getUserPreferences(): ResultHandler<UserPreferenceResponse?> {
        return httpClient.safeRequest<UserPreferenceResponse?> {
            url("${BASE_URL}api/social/user-preferences/me/")
            contentType(ContentType.Application.Json)
            method = HttpMethod.Get
        }
    }

    suspend fun setUserPreferences(userPreferenceDTO: UserPreferenceDTO): ResultHandler<UserPreferenceResponse?> {
        return httpClient.safeRequest<UserPreferenceResponse?> {
            url("${BASE_URL}api/social/user-preferences/")
            method = HttpMethod.Post
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
            url("${BASE_URL}api/social/user-preferences/${prefId}/")
            method = HttpMethod.Put
            contentType(ContentType.Application.Json)
            setBody(
                userPreferenceDTO
            )
        }
    }

}