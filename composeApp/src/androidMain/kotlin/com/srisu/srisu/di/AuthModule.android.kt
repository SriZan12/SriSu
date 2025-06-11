package com.srisu.srisu.di

import com.liftric.kvault.KVault
import com.srisu.srisu.session.AndroidSessionStorage
import com.srisu.srisu.session.SessionStorage
import com.srisu.srisu.utils.Constants.SESSION_FILE
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

actual val kVaultPlatformModule = module {
    single { KVault(context = androidContext(), fileName = SESSION_FILE) }
    single<SessionStorage> { AndroidSessionStorage(get()) }
}
