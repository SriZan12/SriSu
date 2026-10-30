package com.srisu.srisu.features.couplechat.presentation

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Size
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.srisu.srisu.theme.spacing
import kotlinx.coroutines.*

/** CameraX returns an in-memory JPEG; no plaintext file or gallery insertion. */
@Composable
internal fun ChatCameraCapture(onResult: (Bitmap?)->Unit) {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current
    val scope=rememberCoroutineScope()
    val executor=remember(context) {ContextCompat.getMainExecutor(context)}
    val preview=remember(context) {PreviewView(context).apply {implementationMode=PreviewView.ImplementationMode.COMPATIBLE}}
    val capture=remember {ImageCapture.Builder().setResolutionSelector(ResolutionSelector.Builder()
        .setResolutionStrategy(ResolutionStrategy(Size(1920,1080),ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER)).build()).build()}
    var ready by remember {mutableStateOf(false)}
    var busy by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    DisposableEffect(lifecycle,preview) {
        var disposed=false
        var provider: ProcessCameraProvider?=null
        val useCase=Preview.Builder().build().also {it.surfaceProvider=preview.surfaceProvider}
        val future=ProcessCameraProvider.getInstance(context)
        future.addListener({
            if(!disposed)try {
                provider=future.get()
                provider!!.bindToLifecycle(lifecycle,CameraSelector.DEFAULT_BACK_CAMERA,useCase,capture)
                ready=true
            } catch(_: Exception) {error="Camera unavailable. Check system camera access."}
        },executor)
        onDispose {disposed=true;provider?.unbind(useCase,capture)}
    }
    Dialog(onDismissRequest={onResult(null)},properties=DialogProperties(usePlatformDefaultWidth=false,securePolicy=SecureFlagPolicy.SecureOn)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Text("Take a photo",Modifier.padding(MaterialTheme.spacing.medium),style=MaterialTheme.typography.titleLarge)
                AndroidView(factory={preview},modifier=Modifier.weight(1f).fillMaxWidth())
                error?.let {Text(it,Modifier.padding(MaterialTheme.spacing.medium))}
                Row(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.medium),horizontalArrangement=Arrangement.SpaceBetween) {
                    TextButton(onClick={onResult(null)}) {Text("Cancel")}
                    Button(enabled=ready && !busy,onClick={
                        busy=true;error=null
                        capture.targetRotation=preview.display?.rotation ?: android.view.Surface.ROTATION_0
                        capture.takePicture(executor,object: ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                scope.launch {
                                    var bitmap: Bitmap?=null
                                    try {
                                        withContext(Dispatchers.Default) {
                                            require(image.width.toLong()*image.height<=12_000_000)
                                            val source=image.toBitmap()
                                            val angle=image.imageInfo.rotationDegrees
                                            bitmap=if(angle==0)source else {
                                                val rotated=Bitmap.createBitmap(source,0,0,source.width,source.height,Matrix().apply {postRotate(angle.toFloat())},true)
                                                if(rotated!==source)source.recycle()
                                                rotated
                                            }
                                        }
                                        onResult(bitmap);bitmap=null
                                    } catch(cancelled: CancellationException) {throw cancelled}
                                    catch(_: Exception) {error="Photo could not be captured. Try again."}
                                    finally {bitmap?.recycle();image.close();busy=false}
                                }.invokeOnCompletion {image.close()}
                            }
                            override fun onError(exception: ImageCaptureException) {busy=false;error="Photo could not be captured. Try again."}
                        })
                    }) {Text(if(busy)"Capturing…" else "Capture")}
                }
            }
        }
    }
}
