package com.srisu.srisu.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/** Brand colors are fixed: wallpaper/dynamic colors intentionally do not participate. */
@Composable
fun SriSuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    useSerifHeadings: Boolean = false,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalSriSuColors provides if (darkTheme) DarkExtendedColors else LightExtendedColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) SriSuDarkColorScheme else SriSuLightColorScheme,
            typography = sriSuTypography(useSerifHeadings),
            shapes = SriSuShapes,
            content = content,
        )
    }
}
