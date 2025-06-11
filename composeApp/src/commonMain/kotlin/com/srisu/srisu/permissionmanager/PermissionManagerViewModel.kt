package com.srisu.srisu.permissionmanager

/*class PermissionManagerViewModel(
    private val permissionsController: PermissionsController
) : ViewModel() {

    fun requestPermission(
        permissionType: PermissionType,
        onPermissionGranted: () -> Unit,
        onPermissionDenied: () -> Unit,
        onPermissionDeniedAlways: () -> Unit,
        onPermissionCancelled: () -> Unit
    ) {

        val permission = PermissionManager.getPermission(permissionType)

        if (permission == null) {
            onPermissionDenied()
            return
        }

        viewModelScope.launch {
            try {
                permissionsController.providePermission(permission)
                AppLogger.log("Permission Granted VM")
                onPermissionGranted()
            } catch (e: DeniedAlwaysException) {
                onPermissionDeniedAlways()
            } catch (e: DeniedException) {
                onPermissionDenied()
            } catch (e: RequestCanceledException) {
                e.printStackTrace()
                onPermissionCancelled()
            }
        }
    }
}*/
