package com.srisu.srisu.features.notifications

import androidx.compose.runtime.Composable

/** Platform-specific, debug-only tools; registration identifiers never enter shared UI state. */
@Composable
internal expect fun NotificationRegistrationTools()
