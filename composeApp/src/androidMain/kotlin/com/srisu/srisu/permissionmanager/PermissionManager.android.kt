package com.srisu.srisu.permissionmanager

import android.Manifest
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.core.permissionmanager.PermissionCallback
import com.srisu.srisu.core.permissionmanager.PermissionHandler
import com.srisu.srisu.core.permissionmanager.PermissionState
import com.srisu.srisu.core.permissionmanager.PermissionType
import com.srisu.srisu.core.permissionmanager.PermissionsManager
import com.srisu.srisu.utils.AppContext
import kotlinx.coroutines.launch


@Composable
actual fun createPermissionsManager(callback: PermissionCallback): PermissionsManager {
    return remember { PermissionsManager(callback) }
}

actual class PermissionsManager actual constructor(private val callback: PermissionCallback) :
    PermissionHandler {

    @OptIn(ExperimentalPermissionsApi::class)
    @Composable
    override fun askPermission(permission: PermissionType, permissionRequestCompleted: Boolean) {
        val lifecycleOwner = LocalLifecycleOwner.current

        @Composable
        fun HandlePermission(
            permissionState: com.google.accompanist.permissions.PermissionState,
            permission: PermissionType
        ) {
            val permissionStatus = permissionState.status
            LaunchedEffect(Unit) {
                when (permissionStatus) {
                    is PermissionStatus.Granted -> {
                        AppLogger.log("$permission GRANTED")
                        callback.onPermissionStatus(permission, PermissionState.GRANTED)
                    }

                    is PermissionStatus.Denied -> {
                        AppLogger.log("PERMISSION DENIED = ${permissionStatus.shouldShowRationale}")
                        AppLogger.log("PERMISSION REQUEST COMPLETED = $permissionRequestCompleted")

                        if (permissionRequestCompleted) {
                            if (permissionStatus.shouldShowRationale) {
                                lifecycleOwner.lifecycleScope.launch {
                                    permissionState.launchPermissionRequest()
                                }
                                callback.onPermissionStatus(
                                    permission,
                                    PermissionState.SHOW_RATIONALE
                                )
                            } else {
                                AppLogger.log("PERMISSION DENIED PERMANENTLY")
                                launchSettings()
                                callback.onPermissionStatus(permission, PermissionState.DENIED)
                            }
                        } else {
                            AppLogger.log("PERMISSION ASKED FIRST TIME")
                            lifecycleOwner.lifecycleScope.launch {
                                permissionState.launchPermissionRequest()
                            }
                            callback.onPermissionStatus(
                                permission,
                                PermissionState.REQUEST_LAUNCHED
                            )
                        }
                    }
                }
            }
        }

        when (permission) {
            PermissionType.CAMERA -> {
                val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
                HandlePermission(cameraPermissionState, PermissionType.CAMERA)
            }

            PermissionType.RECORD_AUDIO -> {
                val audioPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
                HandlePermission(audioPermissionState, PermissionType.RECORD_AUDIO)
            }

            PermissionType.STORAGE -> {

                callback.onPermissionStatus(
                    permission,
                    PermissionState.GRANTED
                )


//                For picker no need to handle permission.

                /* if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) { // Android 12 and below
                     val storagePermissionState =
                         rememberPermissionState(Manifest.permission.READ_EXTERNAL_STORAGE)
                     HandlePermission(storagePermissionState, PermissionType.STORAGE)
                 } else { // Android 13 and above
                     val mediaPermissions = rememberMultiplePermissionsState(
                         permissions = listOf(
                             Manifest.permission.READ_MEDIA_IMAGES,
                             Manifest.permission.READ_MEDIA_VIDEO,
                             Manifest.permission.READ_MEDIA_AUDIO,
                         )
                     )

                     // Request permissions only when needed
                     LaunchedEffect(mediaPermissions) {
                         if (mediaPermissions.allPermissionsGranted) {
                             AppLogger.log("MEDIA PERMISSIONS GRANTED")
                             callback.onPermissionStatus(
                                 PermissionType.STORAGE,
                                 PermissionState.GRANTED
                             )
                         } else {
                             if (permissionRequestCompleted) {
                                 if (mediaPermissions.shouldShowRationale) {
                                     lifecycleOwner.lifecycleScope.launch {
                                         mediaPermissions.launchMultiplePermissionRequest()
                                     }

                                     AppLogger.log("MEDIA PERMISSION RATIONALE NEEDED")
                                     callback.onPermissionStatus(
                                         PermissionType.STORAGE,
                                         PermissionState.SHOW_RATIONALE
                                     )
                                 } else {
                                     AppLogger.log("MEDIA PERMISSION DENIED PERMANENTLY")
                                     launchSettings() // Open settings
                                     callback.onPermissionStatus(
                                         PermissionType.STORAGE,
                                         PermissionState.DENIED
                                     )
                                 }
                             } else {
                                 AppLogger.log("MEDIA PERMISSION ASKED FIRST TIME")
                                 lifecycleOwner.lifecycleScope.launch {
                                     mediaPermissions.launchMultiplePermissionRequest()
                                 }
                                 callback.onPermissionStatus(
                                     PermissionType.STORAGE,
                                     PermissionState.REQUEST_LAUNCHED
                                 )
                             }
                         }
                     }
                 }*/


            }

        }
    }


    @OptIn(ExperimentalPermissionsApi::class)
    @Composable
    override fun isPermissionGranted(permission: PermissionType): Boolean {
        return when (permission) {
            PermissionType.CAMERA -> {
                val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
                cameraPermissionState.status.isGranted
            }

            PermissionType.RECORD_AUDIO -> {
                val audioPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
                audioPermissionState.status.isGranted
            }

            PermissionType.STORAGE -> {
                /* if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) { // Android 12 and below
                     val storagePermissionState =
                         rememberPermissionState(Manifest.permission.READ_EXTERNAL_STORAGE)
                     storagePermissionState.status.isGranted
                 } else { // Android 13+ (API 33 and above)
                     val mediaPermissionsState = rememberMultiplePermissionsState(
                         permissions = listOf(
                             Manifest.permission.READ_MEDIA_IMAGES,
                             Manifest.permission.READ_MEDIA_VIDEO,
                             Manifest.permission.READ_MEDIA_AUDIO
                         )
                     )
                     mediaPermissionsState.allPermissionsGranted
                 }*/
                true // No need to ask permission for android
            }
        }
    }


    override fun launchSettings() {
        val context = AppContext.get() ?: return // Ensure context is not null

        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).apply {
            addFlags(FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)
    }

}
