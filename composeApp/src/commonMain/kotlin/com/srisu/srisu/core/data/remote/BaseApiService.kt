package com.srisu.srisu.core.data.remote

import com.srisu.srisu.core.data.remote.CityResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.http.contentType

class BaseApiService(private val httpClient: HttpClient) {

    suspend fun getCitiesList(country: String?): CityResponse? {
        return httpClient.get("https://countriesnow.space/api/v0.1/countries/cities/q") {
            parameter("country", country)
            contentType(ContentType.Application.Json)
        }.body()
    }
}
