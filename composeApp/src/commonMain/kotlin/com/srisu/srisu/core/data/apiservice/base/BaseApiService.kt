package com.srisu.srisu.core.data.apiservice.base

import com.srisu.srisu.core.data.apiservice.suggestion.SuggestionApiService.Companion.CITY_ENDPOINT
import com.srisu.srisu.core.data.response.suggestion.CityResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.http.contentType

class BaseApiService(private val httpClient: HttpClient) {

    companion object {
//        const val BASE_URL = "http://192.168.2.25:8000/" // office
        const val BASE_URL = "http://192.168.1.65:8000/" // home
    }

    suspend fun getCitiesList(country: String?): CityResponse? {
        return httpClient.get(CITY_ENDPOINT) {
            parameter("country", country)
            contentType(ContentType.Application.Json)
        }.body()
    }
}