package com.srisu.srisu.app

import androidx.compose.runtime.Composable

@Composable
expect fun ApplicationDependencies(content: @Composable () -> Unit)
