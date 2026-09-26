package com.srisu.srisu.di

import com.srisu.srisu.features.auth.data.remote.api.AuthApiService
import com.srisu.srisu.core.data.remote.BaseApiService
import com.srisu.srisu.features.chat.data.remote.api.ChatApiService
import com.srisu.srisu.features.home.connection.data.remote.api.ConnectionApiService
import com.srisu.srisu.features.home.profile.data.remote.api.ProfileApiService
import com.srisu.srisu.features.home.suggestions.data.api.SuggestionApiService
import com.srisu.srisu.core.data.remote.HttpClientFactory
import com.srisu.srisu.features.auth.domain.repository.AuthRepository
import com.srisu.srisu.features.chat.data.remote.api.ChatRepository
import com.srisu.srisu.features.home.connection.domain.repository.ConnectionRepository
import com.srisu.srisu.features.home.profile.domain.repository.ProfileRepository
import com.srisu.srisu.features.chat.data.remote.websocket.ChatWebSocketClient
import com.srisu.srisu.features.home.suggestions.domain.repository.SuggestionRepository
import com.srisu.srisu.core.session.SessionUtils
import org.koin.core.module.Module
import org.koin.dsl.onClose
import org.koin.dsl.module

val sharedNetworkModule = module {
    single { com.srisu.srisu.core.config.ApiEnvironment.configured() }
    single { com.srisu.srisu.core.lifecycle.ApplicationLifetime() } onClose { it?.close() }
    single { com.srisu.srisu.core.session.SessionCoordinator(get(org.koin.core.qualifier.named("platformSessionStorage"))) }
    single<com.srisu.srisu.core.session.SessionStorage> { get<com.srisu.srisu.core.session.SessionCoordinator>() }
    single { HttpClientFactory.create(sessions = get(), environment = get(), engine = get()) } onClose { it?.close() }
    single { get<com.srisu.srisu.core.data.local.CatalogueDatabase>().catalogue() }
    single { com.srisu.srisu.features.home.profile.data.InterestCatalogueRepository(get(), get(), get(), get()) }

    single { BaseApiService(httpClient = get()) }

    single { AuthApiService(httpClient = get()) } //apiService
    single { AuthRepository(authApiService = get()) } // Repo

    single { SuggestionApiService(httpClient = get()) }
    single { SuggestionRepository(suggestionApiService = get(), baseApiService = get()) }

    single { ProfileApiService(httpClient = get(), environment = get()) }
    single { ProfileRepository(profileApiService = get(), baseApiService = get(), catalogue = get()) }

    single { ConnectionApiService(httpClient = get()) }
    single { ConnectionRepository(connectionApiService = get()) }

    single { ChatApiService(httpClient = get(), environment = get()) }
    single<com.srisu.srisu.core.data.remote.SocketConnector> { com.srisu.srisu.core.data.remote.KtorSocketConnector(get(), get()) }
    single { ChatWebSocketClient(connector = get(), sessions = get(), lifetime = get()) } onClose { it?.close() }

    single { ChatRepository(webSocketClient = get(), chatApiService = get(), sessions = get(), lifetime = get()) } onClose { it?.close() }

}

expect val platformNetworkModule: Module
