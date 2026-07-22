package com.srisu.srisu.di

import com.srisu.srisu.core.coroutines.AppCoroutineDispatchers
import com.srisu.srisu.core.coroutines.ApplicationCoroutineScope
import com.srisu.srisu.core.data.local.createDataStore
import com.srisu.srisu.core.session.SessionUtils
import com.srisu.srisu.features.auth.data.local.datastore.AuthDataStore
import com.srisu.srisu.utils.ConnectivityObserver
import org.koin.dsl.module

val commonModule = module {
    single { AppCoroutineDispatchers() }
    single { ApplicationCoroutineScope(dispatcher = get<AppCoroutineDispatchers>().default) }
    single { ConnectivityObserver() }
    single { AuthDataStore(dataStore = createDataStore()) }
    single { SessionUtils(sessionStorage = get()) }
}

