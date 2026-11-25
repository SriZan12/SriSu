package com.srisu.srisu.features.settings

import androidx.compose.runtime.Composable

/** Keep native system chrome consistent with the app-level appearance choice. */
@Composable internal expect fun ApplyPlatformAppearance(appearance: AppAppearance, dark: Boolean)

/** A modal has its own Android window; its chrome must follow the app, not the OS setting. */
@Composable internal expect fun ApplyOverlayAppearance(dark: Boolean)
