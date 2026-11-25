package com.srisu.srisu.utils

import androidx.compose.runtime.Composable

class CameraManager(val available: Boolean, val launch: () -> Unit)
@Composable expect fun rememberCameraManager(onResult: (MediaFile) -> Unit, onError: () -> Unit): CameraManager
