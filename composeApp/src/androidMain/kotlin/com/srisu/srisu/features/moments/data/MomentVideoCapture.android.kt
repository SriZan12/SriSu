package com.srisu.srisu.features.moments.data

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.srisu.srisu.theme.spacing
import java.io.File
import java.util.UUID

/** App-private camera output; never inserts into the device gallery. */
@Composable
internal fun MomentVideoCapture(onResult: (File?, String?) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val callback by rememberUpdatedState(onResult)
    val executor = remember(context) { ContextCompat.getMainExecutor(context) }
    val preview = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            keepScreenOn = true
        }
    }
    val recorder = remember {
        Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HD, FallbackStrategy.lowerQualityThan(Quality.HD)))
            .setTargetVideoEncodingBitRate(3_000_000)
            .build()
    }
    val capture = remember { VideoCapture.withOutput(recorder) }
    val session = remember { MomentCaptureSession { file, error -> callback(file, error) } }
    var front by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var canSwitch by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(lifecycle, front) {
        var disposed = false
        ready = false
        cameraError = null
        var provider: ProcessCameraProvider? = null
        val useCase = Preview.Builder().build().also { it.surfaceProvider = preview.surfaceProvider }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (!disposed) try {
                val camera = future.get()
                provider = camera
                val back = camera.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                val selfie = camera.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                canSwitch = back && selfie
                val selector = if ((front && selfie) || !back) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                camera.bindToLifecycle(lifecycle, selector, useCase, capture)
                ready = true
            } catch (_: Exception) {
                cameraError = "Camera unavailable. Check camera access or choose a video from your library."
            }
        }, executor)
        onDispose { disposed = true; provider?.unbind(useCase, capture) }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            // Never keep recording after leaving the app or locking the device.
            if (event == Lifecycle.Event.ON_STOP) session.cancel()
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer); session.dispose(); preview.keepScreenOn = false }
    }
    Dialog(onDismissRequest = session::cancel, properties = DialogProperties(
        usePlatformDefaultWidth = false, securePolicy = SecureFlagPolicy.SecureOn,
    )) {
        Surface(Modifier.fillMaxSize()) {
            val space = MaterialTheme.spacing
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = space.small), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = session::cancel) { Icon(Icons.Outlined.Close, "Cancel recording") }
                    Column(Modifier.weight(1f)) {
                        Text("Record video", style = MaterialTheme.typography.titleMedium)
                        Text("Up to 60 seconds", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { front = !front }, enabled = ready && canSwitch && !session.busy) {
                        Icon(Icons.Outlined.Cameraswitch, "Switch camera")
                    }
                }
                AndroidView(factory = { preview }, modifier = Modifier.weight(1f).fillMaxWidth())
                Column(Modifier.fillMaxWidth().heightIn(max = space.touchTarget * 4)
                    .verticalScroll(rememberScrollState()).padding(space.medium),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(space.small)) {
                    (cameraError ?: session.error)?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Text("0:${session.seconds.toString().padStart(2, '0')} / 1:00", style = MaterialTheme.typography.labelLarge)
                    Button(onClick = {
                        if (session.busy) session.stop() else {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
                                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                                session.permissionLost()
                            } else try {
                                capture.targetRotation = preview.display?.rotation ?: android.view.Surface.ROTATION_0
                                val folder = File(context.cacheDir, "srisu-moment-capture").apply { mkdirs() }
                                // Process death can leave a private partial clip; it is never a draft.
                                folder.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24 * 60 * 60 * 1000L }?.forEach { it.delete() }
                                val file = File(folder, "${UUID.randomUUID()}.mp4")
                                session.start(file) {
                                    val options = FileOutputOptions.Builder(file)
                                        // Leave room for the final encoded frame under the strict server limit.
                                        .setDurationLimitMillis(MOMENT_VIDEO_MAX_DURATION - 250)
                                        .setFileSizeLimit(MOMENT_VIDEO_MAX_BYTES - 1024 * 1024)
                                        .build()
                                    recorder.prepareRecording(context, options).withAudioEnabled().start(executor, session::event)
                                }
                            } catch (_: Exception) { session.failedStart() }
                        }
                    }, enabled = ready && !session.finishing, modifier = Modifier.fillMaxWidth().heightIn(min = space.touchTarget).testTag("moment-camera-record")) {
                        Text(if (session.finishing) "Finishing…" else if (session.busy) "Stop recording" else "Start recording")
                    }
                }
            }
        }
    }
}

/** Owns the file until finalize: cancellation cannot race into publishing a partial clip. */
internal class MomentCaptureSession(private val result: (File?, String?) -> Unit) {
    var busy by mutableStateOf(false); private set
    var finishing by mutableStateOf(false); private set
    var seconds by mutableIntStateOf(0); private set
    var error by mutableStateOf<String?>(null); private set
    private var recording: Recording? = null
    private var file: File? = null
    private var ended = false
    fun start(output: File, begin: () -> Recording) {
        if (busy || ended) { output.delete(); return }
        file = output; busy = true; error = null; seconds = 0
        recording = begin()
    }
    fun failedStart() {
        recording?.close(); recording = null; file?.delete(); file = null
        busy = false; finishing = false; error = "Recording could not start. Check available storage and try again."
    }
    fun permissionLost() { error = "Allow camera and microphone access in Settings to record a video." }
    fun stop() { if (busy && !finishing) { finishing = true; recording?.stop() } }
    fun event(event: VideoRecordEvent) {
        if (event is VideoRecordEvent.Status) seconds = (event.recordingStats.recordedDurationNanos / 1_000_000_000).toInt().coerceAtMost(60)
        if (event !is VideoRecordEvent.Finalize) return
        recording?.close(); recording = null
        val output = file; file = null; busy = false; finishing = false
        if (ended) { output?.delete(); return }
        val usable = event.error in setOf(VideoRecordEvent.Finalize.ERROR_NONE,
            VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED, VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED)
        if (usable && output != null && output.length() > 0) {
            ended = true; result(output, null)
        } else {
            output?.delete(); error = "Video could not be recorded. Try again or choose a video from your library."
        }
    }
    fun cancel() { if (!ended) { dispose(); result(null, null) } }
    fun dispose() {
        ended = true
        if (recording != null) recording?.close() else { file?.delete(); file = null }
        // An active recorder still owns its file. Finalize deletes it after closing the writer.
    }
}
