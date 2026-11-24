@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.srisu.srisu.features.moments.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.*
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.effect.Presentation
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.transformer.*
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private fun videoRoot(): File = File(org.koin.mp.KoinPlatform.getKoin().get<Context>().cacheDir,"srisu-moment-videos")
private fun ownedFile(path: String): File {
    val file=File(path).canonicalFile
    require(file.parentFile==videoRoot().canonicalFile && file.extension=="mp4")
    return file
}
actual suspend fun readMomentVideoChunk(video: LocalMomentVideo, offset: Long, count: Int): ByteArray = withContext(Dispatchers.IO) {
    require(offset>=0 && count in 1..2*1024*1024 && offset+count<=video.size)
    RandomAccessFile(ownedFile(video.path),"r").use { file ->
        require(file.length()==video.size);file.seek(offset)
        ByteArray(count).also {file.readFully(it)}
    }
}
actual fun removeMomentVideoFile(video: LocalMomentVideo) { runCatching {ownedFile(video.path).delete()} }

@Composable actual fun rememberMomentVideoPicker(): MomentVideoPicker {
    val context=LocalContext.current
    val driver=remember(context) {AndroidMomentVideoPicker(context)}
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {driver.selected(it)}
    SideEffect {driver.launch={launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))}}
    DisposableEffect(driver) {onDispose {driver.close()}}
    return driver
}

private class AndroidMomentVideoPicker(private val context: Context): MomentVideoPicker {
    var launch:(()->Unit)?=null
    private var callback:((MomentVideoSelection)->Unit)?=null
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var job:Job?=null
    override fun pick(result:(MomentVideoSelection)->Unit) {
        if(callback!=null) return
        callback=result
        try {checkNotNull(launch).invoke()} catch(_:Exception) {deliver(MomentVideoSelection(error="The video picker could not open. Try again."))}
    }
    private fun deliver(value:MomentVideoSelection) {val cb=callback;callback=null;if(cb!=null) cb(value) else value.video?.let(::removeMomentVideoFile)}
    fun selected(uri:Uri?) {
        if(callback==null) return
        if(uri==null) {deliver(MomentVideoSelection());return}
        job=scope.launch {
            var output:File?=null
            try {
                val meta=withContext(Dispatchers.IO) {
                    val sourceBytes=context.contentResolver.query(uri,arrayOf(OpenableColumns.SIZE),null,null,null)?.use {cursor ->
                        if(cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else 0L
                    } ?: 0L
                    require(sourceBytes<=MOMENT_VIDEO_MAX_BYTES)
                    MediaMetadataRetriever().use {r ->
                        r.setDataSource(context,uri)
                        val duration=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
                        var width=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                        var height=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                        require(duration in 100..MOMENT_VIDEO_MAX_DURATION && width in 2..4096 && height in 2..4096 && width.toLong()*height<=9_000_000)
                        if(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() in listOf(90,270)) {val tmp=width;width=height;height=tmp}
                        val ratio=minOf(1.0,1280.0/maxOf(width,height))
                        val w=maxOf(2,(width*ratio).toInt()/2*2);val h=maxOf(2,(height*ratio).toInt()/2*2)
                        val thumb=if(android.os.Build.VERSION.SDK_INT>=27) r.getScaledFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC, maxOf(2,w/4),maxOf(2,h/4)) else r.getFrameAtTime(0)
                        val bytes=thumb?.let {bitmap ->try {ByteArrayOutputStream().use {out->bitmap.compress(Bitmap.CompressFormat.JPEG,75,out);out.toByteArray()}} finally {bitmap.recycle()}}
                        Triple(LocalMomentVideo("",0,duration,w,h),bytes,sourceBytes)
                    }
                }
                output=withContext(Dispatchers.IO) {
                    videoRoot().apply {mkdirs();listFiles()?.filter {System.currentTimeMillis()-it.lastModified()>7L*24*60*60*1000}?.forEach {it.delete()}}
                    File(videoRoot(),UUID.randomUUID().toString()+".mp4")
                }
                val file=output!!
                try {
                    withTimeout(150_000) {transcode(uri,file,meta.first)}
                    // Do not expand an already-efficient source merely to upload it.
                    check(meta.third<=0 || file.length()<meta.third)
                }
                catch(cancelled:CancellationException) {throw cancelled}
                catch(_:Exception) {
                    // Some devices cannot encode the source codec/HDR. Bounded original
                    // upload lets the server normalize it without rejecting a valid clip.
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)!!.use {input->file.outputStream().use {out->
                            val buffer=ByteArray(65536);var total=0L
                            while(true) {ensureActive();val n=input.read(buffer);if(n<0)break;total+=n;require(total<=MOMENT_VIDEO_MAX_BYTES);out.write(buffer,0,n)}
                        }}
                    }
                }
                require(file.length() in 1..MOMENT_VIDEO_MAX_BYTES)
                deliver(MomentVideoSelection(meta.first.copy(path=file.absolutePath,size=file.length()),meta.second));output=null
            } catch(_:TimeoutCancellationException) {deliver(MomentVideoSelection(error="Preparing this video took too long. Try a shorter clip."))}
            catch(cancelled:CancellationException) {throw cancelled}
            catch(_:Exception) {deliver(MomentVideoSelection(error="Choose a playable video up to 60 seconds and 100 MiB."))}
            finally {output?.delete()}
        }
    }
    private suspend fun transcode(uri:Uri,file:File,meta:LocalMomentVideo) = suspendCancellableCoroutine<Unit> {continuation ->
        val item=EditedMediaItem.Builder(MediaItem.fromUri(uri)).setEffects(Effects(emptyList(),listOf(
            Presentation.createForWidthAndHeight(meta.width,meta.height,Presentation.LAYOUT_SCALE_TO_FIT)))).build()
        val transformer=Transformer.Builder(context).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(DefaultEncoderFactory.Builder(context).setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(2_500_000).build()).build())
            .addListener(object:Transformer.Listener {
                override fun onCompleted(composition:androidx.media3.transformer.Composition,exportResult:ExportResult) {if(continuation.isActive)continuation.resume(Unit)}
                override fun onError(composition:androidx.media3.transformer.Composition,exportResult:ExportResult,exportException:ExportException) {if(continuation.isActive)continuation.resumeWithException(exportException)}
            }).build()
        continuation.invokeOnCancellation {transformer.cancel()}
        transformer.start(item,file.absolutePath)
    }
    override fun cancel() {job?.cancel();deliver(MomentVideoSelection());job=null}
    override fun close() {cancel();scope.cancel()}
}

@Composable actual fun MomentVideoPlayer(source:MomentPlayback,playing:Boolean,muted:Boolean,modifier:Modifier,embedded:Boolean,onEvent:(MomentPlaybackEvent)->Unit) {
    val context=LocalContext.current
    val callback by rememberUpdatedState(onEvent)
    val player=remember(source) {
        val http=DefaultHttpDataSource.Factory().setConnectTimeoutMs(10_000).setReadTimeoutMs(15_000)
        if(source.cookieValue.isNotEmpty()) http.setDefaultRequestProperties(mapOf("Cookie" to "${source.cookieName}=${source.cookieValue}"))
        ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context,http)))
            .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(1500,10000,500,1500).setTargetBufferBytes(8*1024*1024).setBackBuffer(0,false).build())
            .build().apply {setAudioAttributes(AudioAttributes.DEFAULT,true);setHandleAudioBecomingNoisy(true);setMediaItem(MediaItem.fromUri(source.url));prepare()}
    }
    var frame by remember(source) {mutableStateOf(false)}
    DisposableEffect(player) {
        val listener=object:Player.Listener {
            override fun onRenderedFirstFrame() {frame=true;callback(MomentPlaybackEvent("ready",player.currentPosition,player.duration.coerceAtLeast(0),true))}
            override fun onPlayWhenReadyChanged(playWhenReady:Boolean,reason:Int) {
                if(!playWhenReady && reason in listOf(Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS,Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY))
                    callback(MomentPlaybackEvent("paused",player.currentPosition,player.duration.coerceAtLeast(0),frame))
            }
            override fun onPlayerError(error:PlaybackException) {callback(MomentPlaybackEvent("failed"))}
            override fun onPlaybackStateChanged(playbackState:Int) {if(playbackState==Player.STATE_ENDED)callback(MomentPlaybackEvent("ended",player.currentPosition,player.duration.coerceAtLeast(0),frame))}
        }
        player.addListener(listener)
        onDispose {player.removeListener(listener);player.release()}
    }
    LaunchedEffect(player,playing,muted) {player.volume=if(muted)0f else 1f;if(playing && player.playbackState==Player.STATE_ENDED)player.seekTo(0);player.playWhenReady=playing}
    LaunchedEffect(player) {while(true) {
        if(player.playerError==null && player.playbackState!=Player.STATE_ENDED) callback(MomentPlaybackEvent(
            if(player.playbackState==Player.STATE_BUFFERING)"buffering" else if(player.isPlaying)"playing" else "ready",
            player.currentPosition.coerceAtLeast(0),player.duration.coerceAtLeast(0),frame))
        delay(250)
    }}
    AndroidView(factory={
        val view=if(embedded) android.view.LayoutInflater.from(it).inflate(com.srisu.srisu.R.layout.moment_video_embedded,null) as PlayerView else PlayerView(it)
        view.apply {useController=false;resizeMode=AspectRatioFrameLayout.RESIZE_MODE_FIT;setEnableComposeSurfaceSyncWorkaround(true);this.player=player}
    },
        update={it.player=player},modifier=modifier)
}
