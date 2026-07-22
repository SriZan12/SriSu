package com.srisu.srisu.core.data.remote

import com.srisu.srisu.features.home.suggestions.data.api.SuggestionApiService
import com.srisu.srisu.features.home.suggestions.data.response.CityResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.http.contentType

class BaseApiService(private val httpClient: HttpClient) {

    suspend fun getCitiesList(country: String?): CityResponse? {
        return httpClient.get(SuggestionApiService.CITY_ENDPOINT) {
            parameter("country", country)
            contentType(ContentType.Application.Json)
        }.body()
    }
}