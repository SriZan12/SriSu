package com.srisu.srisu.di


import com.srisu.srisu.core.data.local.AppDataStoreRepo
import com.srisu.srisu.core.data.local.createDataStore
import com.srisu.srisu.utils.ConnectivityObserver
import org.koin.dsl.module

val commonModule = module {
    single { ConnectivityObserver() }
    single { AppDataStoreRepo(dataStore = createDataStore()) }
}

