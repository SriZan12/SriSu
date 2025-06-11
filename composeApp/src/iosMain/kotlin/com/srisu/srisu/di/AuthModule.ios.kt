package com.srisu.srisu.di

import com.liftric.kvault.KVault
import org.koin.dsl.module
import com.srisu.srisu.session.IOSSessionStorage
import com.srisu.srisu.session.SessionStorage


actual val kVaultPlatformModule = module {
//    single { KVault(serviceName = SESSION, accessGroup = SESSION_FILE) }
    single { KVault() }
    single<SessionStorage> { IOSSessionStorage(get()) }
}
