package com.srisu.srisu.features.settings

import androidx.compose.runtime.Composable

/** Keep native system chrome consistent with the app-level appearance choice. */
@Composable internal expect fun ApplyPlatformAppearance(appearance: AppAppearance, dark: Boolean)
