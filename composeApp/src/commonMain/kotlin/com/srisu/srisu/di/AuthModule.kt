package com.srisu.srisu.di

import com.srisu.srisu.features.auth.presentation.vm.AuthViewModel
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.core.module.dsl.viewModel


val authModule = module {
    viewModel {
        AuthViewModel(
            authRepository = get(),
            sessionStorage = get(),
            connectivityObserver = get(),
            dataStoreRepo = get()
        )
    }
}

expect val kVaultPlatformModule: Module