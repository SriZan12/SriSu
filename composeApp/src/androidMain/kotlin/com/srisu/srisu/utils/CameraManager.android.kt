package com.srisu.srisu.utils

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import java.io.File

@Composable actual fun rememberCameraManager(onResult: (MediaFile) -> Unit, onError: () -> Unit): CameraManager {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val result by rememberUpdatedState(onResult)
    val error by rememberUpdatedState(onError)
    var file by remember { mutableStateOf<File?>(null) }
    var uri by remember { mutableStateOf<android.net.Uri?>(null) }
    val capture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val target = file; val source = uri
        if (success && source != null) scope.launch {
            try { FileManager().createProfilePhotoFromPath(source.toString())?.let(result) ?: error() }
            finally { target?.delete(); file = null; uri = null }
        } else { target?.delete(); file = null; uri = null }
    }
    val launchCapture = {
        try {
            val folder = File(context.cacheDir, "profile-camera").apply { mkdirs() }
            folder.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000 }?.forEach(File::delete)
            file = File.createTempFile("cover-", ".jpg", folder)
            uri = FileProvider.getUriForFile(context, "${context.packageName}.profile-camera", requireNotNull(file))
            capture.launch(requireNotNull(uri))
        } catch (_: Exception) { file?.delete(); error() }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed -> if (allowed) launchCapture() else error() }
    val available = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    return CameraManager(available) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCapture()
        else permission.launch(Manifest.permission.CAMERA)
    }
}
