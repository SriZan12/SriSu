package com.srisu.srisu.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import com.srisu.srisu.di.createKoinConfiguration
import org.koin.compose.KoinIsolatedContext
import org.koin.dsl.koinApplication
import org.koin.dsl.includes
import org.koin.android.ext.koin.androidContext

/** One graph/crypto store per process, shared by activities and push workers. */
object AndroidApplicationGraph {
    private var context: Context? = null
    fun initialize(value: Context) { context = value.applicationContext }
    val application by lazy { koinApplication {
        androidContext(checkNotNull(context))
        includes(createKoinConfiguration())
    } }
    val koin get() = application.koin
}

@Composable
actual fun ApplicationDependencies(content: @Composable () -> Unit) {
    AndroidApplicationGraph.initialize(LocalContext.current)
    KoinIsolatedContext(AndroidApplicationGraph.application, content)
}
