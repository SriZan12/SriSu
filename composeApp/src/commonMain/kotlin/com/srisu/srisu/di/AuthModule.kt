package com.srisu.srisu.di

import com.srisu.srisu.features.auth.presentation.vm.AuthViewModel
import org.koin.core.module.Module
import org.koin.dsl.module


val authModule = module {
    single {
        AuthViewModel(
            authRepository = get(),
            sessionStorage = get(),
            connectivityObserver = get(),
            dataStoreRepo = get()
        )
    }
}

expect val kVaultPlatformModule: Module