package com.srisu.srisu.di

import com.srisu.srisu.features.auth.data.remote.api.AuthApiService
import com.srisu.srisu.core.data.remote.BaseApiService
import com.srisu.srisu.features.chat.data.remote.api.ChatApiService
import com.srisu.srisu.features.home.connection.coupleconnection.data.remote.api.ConnectionApiService
import com.srisu.srisu.features.home.profile.data.remote.api.ProfileApiService
import com.srisu.srisu.features.home.suggestions.data.api.SuggestionApiService
import com.srisu.srisu.core.data.remote.HttpClientFactory
import com.srisu.srisu.features.auth.domain.repository.AuthRepository
import com.srisu.srisu.features.chat.data.remote.api.ChatRepository
import com.srisu.srisu.features.home.connection.coupleconnection.domain.repository.ConnectionRepository
import com.srisu.srisu.features.home.profile.domain.repository.ProfileRepository
import com.srisu.srisu.features.chat.data.remote.websocket.ChatWebSocketClient
import com.srisu.srisu.features.home.suggestions.domain.repository.SuggestionRepository
import com.srisu.srisu.core.session.SessionUtils
import org.koin.core.module.Module
import org.koin.dsl.module

val sharedNetworkModule = module {
    single { HttpClientFactory.create(sessionStorage = get()) }

    single { BaseApiService(httpClient = get()) }

    single { AuthApiService(httpClient = get()) } //apiService
    single { AuthRepository(authApiService = get()) } // Repo

    single { SuggestionApiService(httpClient = get()) }
    single { SuggestionRepository(suggestionApiService = get(), baseApiService = get()) }

    single { ProfileApiService(httpClient = get()) }
    single { ProfileRepository(profileApiService = get(), baseApiService = get()) }

    single { ConnectionApiService(httpClient = get()) }
    single { ConnectionRepository(connectionApiService = get()) }

    single { ChatApiService(httpClient = get()) }
    single {
        ChatWebSocketClient(
            httpClient = get(),
            host = "192.168.1.65",
            port = 8000,
            userToken = SessionUtils().getSession()?.access
        )
    }
    single { ChatRepository(webSocketClient = get(), chatApiService = get()) }

}

expect val platformNetworkModule: Module
