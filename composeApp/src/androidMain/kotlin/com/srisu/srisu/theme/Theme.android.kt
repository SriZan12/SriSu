package com.srisu.srisu.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
actual fun AppTheme(
    darkTheme: Boolean,
    dynamicColor: Boolean,
    content: @Composable () -> Unit
) {
    /**
     * For now we will be using the lightScheme only.
     * */

/*    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if(darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkScheme
        else -> lightScheme
    }*/

/*    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb() // Or your desired status bar color
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkTheme
        }
    }*/

    MaterialTheme(
        colorScheme = lightScheme,
        typography = appTypography(),
        content = content
    )
}