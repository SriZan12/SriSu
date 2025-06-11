package com.srisu.srisu.di

import org.koin.dsl.KoinConfiguration

fun createKoinConfiguration(): KoinConfiguration {
    return KoinConfiguration {
        modules(
            authModule,
            kVaultPlatformModule,
            commonModule,
            sharedNetworkModule,
            mainModule,
        )
    }
}
