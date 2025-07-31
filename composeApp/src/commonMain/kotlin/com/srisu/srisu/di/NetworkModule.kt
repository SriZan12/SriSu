package com.srisu.srisu.di

import com.srisu.srisu.core.data.apiservice.auth.AuthApiService
import com.srisu.srisu.core.data.apiservice.base.BaseApiService
import com.srisu.srisu.core.data.apiservice.profile.ProfileApiService
import com.srisu.srisu.core.data.apiservice.suggestion.SuggestionApiService
import com.srisu.srisu.core.data.network.HttpClientFactory
import com.srisu.srisu.core.data.repository.auth.AuthRepository
import com.srisu.srisu.core.data.repository.profile.ProfileRepository
import com.srisu.srisu.core.data.repository.suggestion.SuggestionRepository
import io.ktor.client.engine.cio.CIO
import org.koin.core.module.Module
import org.koin.dsl.module

val sharedNetworkModule = module {
    single { HttpClientFactory.create(engine = CIO.create(), sessionStorage = get()) }
    single { BaseApiService(httpClient = get()) }
    single { AuthApiService(httpClient = get()) } //apiService
    single { AuthRepository(authApiService = get()) } // Repo

    single { SuggestionApiService(httpClient = get()) }
    single { SuggestionRepository(suggestionApiService = get(), baseApiService = get()) }

    single { ProfileApiService(httpClient = get()) }
    single { ProfileRepository(profileApiService = get(), baseApiService = get()) }

}

expect val platformNetworkModule: Module
