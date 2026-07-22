package com.srisu.srisu.di

import com.srisu.srisu.core.data.remote.BaseApiService
import com.srisu.srisu.core.data.remote.HttpClientFactory
import com.srisu.srisu.core.data.remote.NetworkConfig
import com.srisu.srisu.features.auth.data.remote.api.AuthApiService
import com.srisu.srisu.features.auth.domain.repository.AuthRepository
import com.srisu.srisu.features.chat.data.remote.api.ChatApiService
import com.srisu.srisu.features.chat.data.remote.api.ChatRepository
import com.srisu.srisu.features.chat.data.remote.websocket.ChatWebSocketClient
import com.srisu.srisu.features.home.connection.data.remote.api.ConnectionApiService
import com.srisu.srisu.features.home.connection.domain.repository.ConnectionRepository
import com.srisu.srisu.features.home.profile.data.remote.api.ProfileApiService
import com.srisu.srisu.features.home.profile.domain.repository.ProfileRepository
import com.srisu.srisu.features.home.suggestions.data.api.SuggestionApiService
import com.srisu.srisu.features.home.suggestions.domain.repository.SuggestionRepository
import org.koin.core.module.Module
import org.koin.dsl.module

val sharedNetworkModule = module {
    single { NetworkConfig.localDevelopment() }
    single {
        HttpClientFactory.create(
            sessionStorage = get(),
            enableNetworkLogging = get<NetworkConfig>().enableNetworkLogging,
        )
    }

    single { BaseApiService(httpClient = get()) }

    single { AuthApiService(httpClient = get(), networkConfig = get()) }
    single { AuthRepository(authApiService = get()) }

    single { SuggestionApiService(httpClient = get(), networkConfig = get()) }
    single { SuggestionRepository(suggestionApiService = get(), baseApiService = get()) }

    single { ProfileApiService(httpClient = get(), networkConfig = get()) }
    single { ProfileRepository(profileApiService = get(), baseApiService = get()) }

    single { ConnectionApiService(httpClient = get(), networkConfig = get()) }
    single { ConnectionRepository(connectionApiService = get()) }

    single { ChatApiService(httpClient = get(), networkConfig = get()) }
    single {
        ChatWebSocketClient(
            httpClient = get(),
            networkConfig = get(),
            sessionUtils = get(),
            applicationScope = get(),
            dispatchers = get(),
        )
    }
    single {
        ChatRepository(
            webSocketClient = get(),
            chatApiService = get(),
            applicationScope = get(),
            sessionUtils = get(),
        )
    }
}

expect val platformNetworkModule: Module
