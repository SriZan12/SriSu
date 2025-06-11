package com.srisu.srisu.di

import com.srisu.srisu.core.data.apiservice.AuthApiService
import com.srisu.srisu.core.data.apiservice.SuggestionApiService
import com.srisu.srisu.core.data.network.HttpClientFactory
import com.srisu.srisu.core.data.repository.AuthRepository
import com.srisu.srisu.core.data.repository.SuggestionRepository
import io.ktor.client.engine.cio.CIO
import org.koin.core.module.Module
import org.koin.dsl.module

val sharedNetworkModule = module {
    single { HttpClientFactory.create(engine = CIO.create(), sessionStorage = get()) }

    single { AuthApiService(get()) }

    single { SuggestionApiService(get()) }

    single { AuthRepository(get()) }

    single { SuggestionRepository(get()) }
}

expect val platformNetworkModule: Module
