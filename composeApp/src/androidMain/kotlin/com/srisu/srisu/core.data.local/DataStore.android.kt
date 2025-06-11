package com.srisu.srisu.core.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.srisu.srisu.utils.AppContext

actual fun createDataStore(): DataStore<Preferences> {

    val context = AppContext.get()

    /*require(
        value = true,
        lazyMessage = { "Context object is required." }
    )*/
    return AppDataStore.getDataStore(
        producePath = {
            context.filesDir
                .resolve(dataStoreFileName)
                .absolutePath
        }
    )
}