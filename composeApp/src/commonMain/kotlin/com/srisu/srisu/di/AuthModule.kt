package com.srisu.srisu.di

import com.srisu.srisu.features.auth.presentation.vm.AuthViewModel
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.core.module.dsl.viewModel


val authModule = module {
    single { com.srisu.srisu.features.auth.domain.StartupCoordinator(get(), get(), get<com.srisu.srisu.core.lifecycle.ApplicationLifetime>().scope, get()) }
    viewModel {
        AuthViewModel(
            authRepository = get(),
            sessionStorage = get(),
            dataStoreRepo = get(),
            startup = get()
        )
    }
}

expect val kVaultPlatformModule: Module
