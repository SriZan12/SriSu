package com.srisu.srisu.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.luminance

/** Large selected/grouped surfaces stay neutral at night; light reference colours are unchanged. */
val ColorScheme.selectedSurface get() = if (background.luminance() < .5f) surfaceContainerHigh else secondaryContainer
val ColorScheme.onSelectedSurface get() = if (background.luminance() < .5f) onSurface else onSecondaryContainer

/** Preserve Material's light treatment and use the same neutral selection across app filters. */
@androidx.compose.runtime.Composable
fun sriSuFilterChipColors() =
    if (androidx.compose.material3.MaterialTheme.colorScheme.background.luminance() >= .5f)
        androidx.compose.material3.FilterChipDefaults.filterChipColors()
    else androidx.compose.material3.FilterChipDefaults.filterChipColors(
    selectedContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.selectedSurface,
    selectedLabelColor = androidx.compose.material3.MaterialTheme.colorScheme.onSelectedSurface,
    selectedLeadingIconColor = androidx.compose.material3.MaterialTheme.colorScheme.onSelectedSurface,
    selectedTrailingIconColor = androidx.compose.material3.MaterialTheme.colorScheme.onSelectedSurface,
)
