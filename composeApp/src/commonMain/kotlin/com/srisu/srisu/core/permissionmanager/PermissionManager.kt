package com.srisu.srisu.core.permissionmanager

import androidx.compose.runtime.Composable

enum class PermissionType {
    CAMERA,
    STORAGE,
    RECORD_AUDIO
}

enum class PermissionState {
    NOT_ASKED_YET, GRANTED, REQUEST_LAUNCHED, DENIED, SHOW_RATIONALE
}

/*
object PermissionManager {
    private val permissionHashMap: HashMap<PermissionType, Permission> = hashMapOf(
        PermissionType.CAMERA to Permission.CAMERA,
        PermissionType.STORAGE to Permission.STORAGE,
        PermissionType.RECORD_AUDIO to Permission.RECORD_AUDIO
    )

    fun getPermission(permissionType: PermissionType): Permission? {
        return permissionHashMap[permissionType]
    }
}

@Composable
fun rememberPermissionsController(): PermissionsController {
    val factory = rememberPermissionsControllerFactory()
    return remember(factory) { factory.createPermissionsController() }
}
*/

/*@Composable
fun rememberPermissionViewModel(permissionsController: PermissionsController): PermissionManagerViewModel {

    BindEffect(permissionsController = permissionsController)

    return remember { PermissionManagerViewModel(permissionsController) }
}*/

expect class PermissionsManager(callback: PermissionCallback) : PermissionHandler

interface PermissionCallback {
    fun onPermissionStatus(permissionType: PermissionType, status: PermissionState)
}

@Composable
expect fun createPermissionsManager(callback: PermissionCallback): PermissionsManager

interface PermissionHandler {
    @Composable
    fun askPermission(permission: PermissionType, permissionRequestCompleted: Boolean = false)

    @Composable
    fun isPermissionGranted(permission: PermissionType): Boolean

    fun launchSettings()

}


