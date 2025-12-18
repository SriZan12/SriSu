package com.srisu.srisu.di

import com.srisu.srisu.core.data.apiservice.auth.AuthApiService
import com.srisu.srisu.core.data.apiservice.base.BaseApiService
import com.srisu.srisu.core.data.apiservice.chat.ChatApiService
import com.srisu.srisu.core.data.apiservice.connection.ConnectionApiService
import com.srisu.srisu.core.data.apiservice.profile.ProfileApiService
import com.srisu.srisu.core.data.apiservice.suggestion.SuggestionApiService
import com.srisu.srisu.core.data.network.HttpClientFactory
import com.srisu.srisu.core.data.repository.auth.AuthRepository
import com.srisu.srisu.core.data.repository.chat.ChatRepository
import com.srisu.srisu.core.data.repository.connection.ConnectionRepository
import com.srisu.srisu.core.data.repository.profile.ProfileRepository
import com.srisu.srisu.core.data.repository.suggestion.SuggestionRepository
import com.srisu.srisu.core.data.websocket.chat.ChatWebSocketClient
//import io.ktor.client.engine.cio.CIO
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
    single { ChatWebSocketClient(httpClient = get(), host = "192.168.1.114", port = 8000) }
    single { ChatRepository(webSocketClient = get()) }

}

expect val platformNetworkModule: Module
