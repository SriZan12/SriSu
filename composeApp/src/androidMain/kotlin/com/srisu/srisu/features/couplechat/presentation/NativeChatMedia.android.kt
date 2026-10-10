package com.srisu.srisu.features.couplechat.presentation

import android.Manifest
import android.app.Dialog
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.*
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.MediaController
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

internal class MemoryMediaSource(private val bytes: ByteArray) : MediaDataSource() {
    override fun getSize()=bytes.size.toLong()
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if(position<0 || position>=bytes.size) return -1
        val count=minOf(size,bytes.size-position.toInt()); bytes.copyInto(buffer,offset,position.toInt(),position.toInt()+count); return count
    }
    override fun close() = Unit
}

internal class AndroidChatMedia(private val context: Context) : NativeChatMedia {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var closed=false
    private var playerReady=false
    private val main=Handler(Looper.getMainLooper())
    var picker: ((Boolean)->Unit)?=null
    var camera: (()->Unit)?=null
    var permission: ((String)->Unit)?=null
    var createDocument: ((String)->Unit)?=null
    private var selection: ((String,ByteArray?)->Unit)?=null
    private var safetySelection=false
    private var permissionAction: (()->Unit)?=null
    private var recordingResult: ((String,ByteArray?)->Unit)?=null
    private var recorder: AudioRecord?=null
    @Volatile private var recording=false
    @Volatile private var discardRecording=false
    @Volatile private var stopRequested=false
    private var recordingJob: Job?=null
    private var player: MediaPlayer?=null
    private var playerResult: ((String)->Unit)?=null
    private var progress: Job?=null
    private var videoDialog: Dialog?=null
    private val audio=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val focus=AudioManager.OnAudioFocusChangeListener { change -> if(change<0) main.post {
        if(recording) { stopRecording(true); recordingResult?.invoke("{\"event\":\"error\",\"code\":\"interrupted\"}",null) }
        playback("pause",0.0)
    } }
    private var audioRequest: AudioFocusRequest?=null
    private var exportBytes: ByteArray?=null
    private var exportResult: ((String)->Unit)?=null
    private val noisy=object: BroadcastReceiver() { override fun onReceive(c: Context?,i: Intent?) {
        if(recording) { stopRecording(true); recordingResult?.invoke("{\"event\":\"error\",\"code\":\"interrupted\"}",null) }
        playback("pause",0.0)
    } }
    init { ContextCompat.registerReceiver(context,noisy,IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),ContextCompat.RECEIVER_NOT_EXPORTED) }
    private fun focus(): Boolean {
        val result=if(Build.VERSION.SDK_INT>=26) {
            val request=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setOnAudioFocusChangeListener(focus).build()
            audioRequest=request; audio.requestAudioFocus(request)
        } else @Suppress("DEPRECATION") audio.requestAudioFocus(focus,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        return result==AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }
    private fun abandonFocus() { if(Build.VERSION.SDK_INT>=26) audioRequest?.let(audio::abandonAudioFocusRequest)
        else @Suppress("DEPRECATION") audio.abandonAudioFocus(focus); audioRequest=null }
    fun permissionResult(granted: Boolean) {
        val action=permissionAction; permissionAction=null
        if(granted) action?.invoke() else if(action!=null) {
            (recordingResult ?: selection)?.invoke("{\"event\":\"error\",\"code\":\"permission\"}",null)
        }
    }
    private fun permitted(name: String, action: ()->Unit) {
        if(ContextCompat.checkSelfPermission(context,name)==PackageManager.PERMISSION_GRANTED) action()
        else { permissionAction=action; permission?.invoke(name) }
    }
    override fun pick(source: String, result: (String,ByteArray?)->Unit) {
        selection=result;safetySelection=source.startsWith("safety-")
        try { if(source=="camera" || source=="safety-camera") permitted(Manifest.permission.CAMERA) { camera?.invoke() }
            else picker?.invoke(source=="video") }
        catch(_: Exception) { selection=null; result("{\"event\":\"error\",\"code\":\"unavailable\"}",null) }
    }
    fun selected(uri: Uri?) {
        val result=selection ?: return; selection=null;val safety=safetySelection
        if(uri==null) { result("{\"event\":\"cancelled\"}",null);return }
        scope.launch {
            try {
                val media=withContext(Dispatchers.IO) {
                    if(context.contentResolver.getType(uri)?.startsWith("video/")==true) AndroidChatVideo.prepare(context,uri)
                    else {
                        val source=context.contentResolver.openInputStream(uri)?.use { stream ->
                            val output=ByteArrayOutputStream();val buffer=ByteArray(32768)
                            while(true) {val read=stream.read(buffer);if(read<0)break;require(output.size()+read<=32*1024*1024);output.write(buffer,0,read)}
                            output.toByteArray()
                        } ?: error("Media missing")
                        val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
                        BitmapFactory.decodeByteArray(source,0,source.size,bounds)
                        require(bounds.outWidth>0 && bounds.outHeight>0 && bounds.outWidth.toLong()*bounds.outHeight<=100_000_000)
                        val opts=BitmapFactory.Options().apply { var sample=1;while(max(bounds.outWidth,bounds.outHeight)/sample>2048)sample*=2;inSampleSize=sample }
                        val bitmap=BitmapFactory.decodeByteArray(source,0,source.size,opts) ?: error("Invalid photo")
                        val orientation=try {ExifInterface(source.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION,1)}
                            catch(_: java.io.IOException) {ExifInterface.ORIENTATION_NORMAL}
                        val matrix=Matrix().apply {when(orientation) {2->setScale(-1f,1f);3->setRotate(180f);4->setScale(1f,-1f)
                            5->{setRotate(90f);postScale(-1f,1f)};6->setRotate(90f);7->{setRotate(-90f);postScale(-1f,1f)};8->setRotate(-90f)}}
                        val rotated=Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true)
                        if(rotated!==bitmap)bitmap.recycle()
                        try { if(safety) safetyPhoto(rotated) to null else photo(rotated) } finally {rotated.recycle()}
                    }
                }
                result(media.first.toString(),media.second)
            } catch(cancelled: CancellationException) {throw cancelled}
            catch(_: Exception) {result("{\"event\":\"error\",\"code\":\"limit\"}",null)}
        }
    }
    fun captured(bitmap: Bitmap?) {
        val result=selection ?: return;selection=null;val safety=safetySelection
        if(bitmap==null) {result("{\"event\":\"cancelled\"}",null);return}
        scope.launch {try {val media=withContext(Dispatchers.Default) {if(safety)safetyPhoto(bitmap) to null else photo(bitmap)};result(media.first.toString(),media.second)}
            catch(_: Exception) {result("{\"event\":\"error\",\"code\":\"unavailable\"}",null)} finally {bitmap.recycle()} }
    }
    override fun safetyQr(text: String): ByteArray? = try {
        require(text.length<=1024)
        val matrix=com.google.zxing.qrcode.QRCodeWriter().encode(text,com.google.zxing.BarcodeFormat.QR_CODE,512,512)
        val bitmap=Bitmap.createBitmap(matrix.width,matrix.height,Bitmap.Config.ARGB_8888)
        try {for(y in 0 until matrix.height)for(x in 0 until matrix.width)bitmap.setPixel(x,y,if(matrix[x,y])android.graphics.Color.BLACK else android.graphics.Color.WHITE) // theme-audit: image pixels; binary QR contrast in every app theme
            ByteArrayOutputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it);it.toByteArray()}
        } finally {bitmap.recycle()}
    } catch(_: Exception) {null}
    private fun safetyPhoto(bitmap: Bitmap): JSONObject {
        val pixels=IntArray(bitmap.width*bitmap.height)
        bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
        val source=com.google.zxing.RGBLuminanceSource(bitmap.width,bitmap.height,pixels)
        val decoded=com.google.zxing.qrcode.QRCodeReader().decode(com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(source)))
        require(decoded.text.length<=1024)
        return JSONObject().put("event","qr").put("value",decoded.text)
    }
    override fun startRecording(result: (String,ByteArray?)->Unit) {
        if(recordingJob?.isActive==true) return
        recordingResult=result;discardRecording=false;stopRequested=false;playback("stop",0.0)
        permitted(Manifest.permission.RECORD_AUDIO) {
            if(discardRecording || stopRequested)return@permitted
            if(!focus()) {result("{\"event\":\"error\",\"code\":\"interrupted\"}",null);return@permitted}
            recordingJob=scope.launch {
                try {
                    val voice=withContext(Dispatchers.IO) {
                        val rate=48000
                        val size=AudioRecord.getMinBufferSize(rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(9600)
                        @Suppress("MissingPermission") val device=AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,size*2)
                        recorder=device;check(device.state==AudioRecord.STATE_INITIALIZED)
                        val pcm=ByteArrayOutputStream();val buffer=ShortArray(4800);val levels=mutableListOf<Int>()
                        try {
                            if(stopRequested)return@withContext null
                            recording=true;device.startRecording()
                            withContext(Dispatchers.Main) {result("{\"event\":\"recording\"}",null)}
                            while(recording && !stopRequested && !discardRecording && pcm.size()<rate*2*120) {
                                val count=device.read(buffer,0,buffer.size)
                                if(count<=0) { if(!recording)break;error("Audio interrupted") }
                                var peak=0
                                for(i in 0 until count) {val value=buffer[i].toInt();peak=max(peak,abs(value));pcm.write(value and 255);pcm.write((value shr 8) and 255)}
                                val level=(peak*100/32768).coerceIn(0,100);levels.add(level)
                                withContext(Dispatchers.Main) {result(JSONObject().put("event","level").put("level",level).put("durationMs",pcm.size()*1000L/(rate*2)).toString(),null)}
                            }
                        } finally {recording=false;try {device.stop()}catch(_: IllegalStateException){};device.release();recorder=null}
                        if(discardRecording || pcm.size()<rate/2) null else {
                            val data=pcm.toByteArray();val header=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
                            header.put("RIFF".toByteArray()).putInt(36+data.size).put("WAVEfmt ".toByteArray()).putInt(16)
                                .putShort(1).putShort(1).putInt(rate).putInt(rate*2).putShort(2).putShort(16).put("data".toByteArray()).putInt(data.size)
                            val waveform=levels.chunked((levels.size/100+1)).map {it.maxOrNull() ?: 0}
                            JSONObject().put("event","ready").put("kind","voice").put("mime","audio/wav").put("durationMs",data.size*1000L/(rate*2))
                                .put("waveform",org.json.JSONArray(waveform)) to (header.array()+data)
                        }
                    }
                    if(voice==null) result("{\"event\":\"cancelled\"}",null) else result(voice.first.toString(),voice.second)
                } catch(cancelled: CancellationException) {throw cancelled}
                catch(_: Exception) {result("{\"event\":\"error\",\"code\":\"interrupted\"}",null)}
                finally {abandonFocus();recordingResult=null;recordingJob=null}
            }
        }
    }
    override fun stopRecording(discard: Boolean) {discardRecording=discard;stopRequested=true;recording=false;permissionAction=null
        if(discard && recordingJob==null) recordingResult?.invoke("{\"event\":\"cancelled\"}",null)
    }
    override fun play(bytes: ByteArray,mime: String,result: (String)->Unit) {
        stopRecording(true);playback("stop",0.0);playerResult=result
        if(!focus()) {result("{\"event\":\"error\"}");return}
        try {
            val media=MediaPlayer();player=media
            media.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            media.setDataSource(MemoryMediaSource(bytes))
            media.setOnErrorListener {_,_,_->result("{\"event\":\"error\"}");playback("stop",0.0);true}
            media.setOnCompletionListener {result("{\"event\":\"ended\"}");abandonFocus()}
            media.setOnPreparedListener {
                playerReady=true
                media.start();result("{\"event\":\"playing\"}")
                progress=scope.launch {while(isActive && player===media) {result(JSONObject().put("event","position").put("positionMs",media.currentPosition).put("durationMs",media.duration).toString());delay(200)}}
            }
            if(mime.startsWith("video/")) {
                val surface=SurfaceView(context);val dialog=Dialog(context,android.R.style.Theme_Material_NoActionBar)
                val frame=FrameLayout(context);frame.addView(surface,FrameLayout.LayoutParams(-1,-1))
                val controls=MediaController(context)
                controls.setMediaPlayer(object:MediaController.MediaPlayerControl {
                    override fun start()=playback("resume",0.0)
                    override fun pause()=playback("pause",0.0)
                    override fun getDuration()=if(player===media && playerReady)media.duration else 0
                    override fun getCurrentPosition()=if(player===media && playerReady)media.currentPosition else 0
                    override fun seekTo(position:Int)=playback("seek",position.toDouble())
                    override fun isPlaying()=player===media && playerReady && media.isPlaying
                    override fun getBufferPercentage()=100
                    override fun canPause()=true
                    override fun canSeekBackward()=true
                    override fun canSeekForward()=true
                    override fun getAudioSessionId()=media.audioSessionId
                })
                controls.setAnchorView(frame);surface.setOnClickListener {controls.show(4000)}
                dialog.setContentView(frame);dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                dialog.setOnDismissListener {if(videoDialog===dialog) {videoDialog=null;playback("stop",0.0)}}
                surface.holder.addCallback(object: SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {if(player===media) {media.setDisplay(holder);media.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT);media.prepareAsync()} }
                    override fun surfaceChanged(h: SurfaceHolder,f: Int,w: Int,height: Int)=Unit
                    override fun surfaceDestroyed(h: SurfaceHolder) {if(player===media)playback("stop",0.0)}
                });videoDialog=dialog;dialog.show()
            } else media.prepareAsync()
        } catch(_: Exception) {result("{\"event\":\"error\"}");playback("stop",0.0)}
    }
    override fun playback(action: String,value: Double) {
        val media=player
        try {when(action) {
            "stop" -> {playerReady=false;progress?.cancel();progress=null;player=null;media?.release();val dialog=videoDialog;videoDialog=null;dialog?.dismiss();abandonFocus()}
            "pause" -> if(media!=null && media.isPlaying) {media.pause();playerResult?.invoke("{\"event\":\"paused\"}")}
            "resume" -> if(media!=null && focus()) {media.start();playerResult?.invoke("{\"event\":\"playing\"}")}
            "seek" -> media?.seekTo(value.toInt().coerceAtLeast(0))
            "speed" -> if(media!=null) {val wasPlaying=media.isPlaying;media.playbackParams=media.playbackParams.setSpeed(value.toFloat().coerceIn(0.5f,2f));if(!wasPlaying)media.pause()}
        }} catch(_: IllegalStateException) {playerResult?.invoke("{\"event\":\"error\"}")}
    }
    override fun export(bytes: ByteArray,mime: String,result: (String)->Unit) {
        exportBytes=bytes;exportResult=result
        createDocument?.invoke("SriSu-${System.currentTimeMillis()}."+when(mime) {"image/jpeg"->"jpg";"video/mp4"->"mp4";"audio/wav"->"wav";else->"aac"})
    }
    fun exported(uri: Uri?) {
        val bytes=exportBytes;val result=exportResult;exportBytes=null;exportResult=null
        if(uri==null || bytes==null) {result?.invoke("cancelled");return}
        scope.launch {try {withContext(Dispatchers.IO) {context.contentResolver.openOutputStream(uri,"w")?.use {it.write(bytes)} ?: error("Export unavailable")};result?.invoke("saved")}
            catch(_: Exception) {result?.invoke("error")} }
    }
    override fun interrupt() {stopRecording(true);playback("stop",0.0)}
    override fun close() {if(closed)return;closed=true;interrupt();selection=null;permissionAction=null;exportBytes=null;exportResult=null
        context.unregisterReceiver(noisy);scope.cancel()}
    companion object {
        fun photo(bitmap: Bitmap): Pair<JSONObject,ByteArray> {
            val ratio=minOf(1f,2048f/max(bitmap.width,bitmap.height))
            val scaled=Bitmap.createScaledBitmap(bitmap,(bitmap.width*ratio).toInt().coerceAtLeast(1),(bitmap.height*ratio).toInt().coerceAtLeast(1),true)
            try {val bytes=ByteArrayOutputStream().use {scaled.compress(Bitmap.CompressFormat.JPEG,85,it);it.toByteArray()}
                require(bytes.size<=16*1024*1024)
                return JSONObject().put("event","ready").put("kind","photo").put("mime","image/jpeg").put("width",scaled.width).put("height",scaled.height)
                    .put("thumbnail",thumbnail(scaled)) to bytes
            } finally {if(scaled!==bitmap)scaled.recycle()}
        }
        fun thumbnail(bitmap: Bitmap): String {
            val ratio=120f/max(bitmap.width,bitmap.height);val small=Bitmap.createScaledBitmap(bitmap,(bitmap.width*ratio).toInt().coerceAtLeast(1),(bitmap.height*ratio).toInt().coerceAtLeast(1),true)
            try {val bytes=ByteArrayOutputStream().use {small.compress(Bitmap.CompressFormat.JPEG,45,it);it.toByteArray()};require(bytes.size<=6144)
                return android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP)
            } finally {if(small!==bitmap)small.recycle()}
        }
    }
}

@Composable internal actual fun rememberNativeChatMedia(): NativeChatMedia {
    val context=LocalContext.current;val driver=remember(context) {AndroidChatMedia(context)}
    var cameraVisible by remember {mutableStateOf(false)}
    val pick=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia(),driver::selected)
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(),driver::permissionResult)
    val document=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream"),driver::exported)
    SideEffect {driver.picker={video->pick.launch(PickVisualMediaRequest(if(video) ActivityResultContracts.PickVisualMedia.VideoOnly else ActivityResultContracts.PickVisualMedia.ImageOnly))}
        driver.camera={cameraVisible=true};driver.permission={permission.launch(it)};driver.createDocument={document.launch(it)}}
    if(cameraVisible) ChatCameraCapture(onResult={bitmap->cameraVisible=false;driver.captured(bitmap)})
    return driver
}
internal actual fun decodePrivatePhoto(bytes: ByteArray): ImageBitmap? {
    val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
    BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
    if(bounds.outWidth !in 1..4096 || bounds.outHeight !in 1..4096 || bounds.outWidth.toLong()*bounds.outHeight>4096L*4096)return null
    return BitmapFactory.decodeByteArray(bytes,0,bytes.size)?.asImageBitmap()
}
