package com.srisu.srisu.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable

/** Compatibility entry point for existing previews; app content uses SriSuTheme. */
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) = SriSuTheme(darkTheme = darkTheme, content = content)
