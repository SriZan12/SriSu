@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.srisu.srisu.features.moments.data

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitViewController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.cinterop.readBytes
import platform.Foundation.*
import platform.UIKit.UIViewController

private fun ownedVideoPath(path:String):String {
    val root=(NSSearchPathForDirectoriesInDomains(NSCachesDirectory,NSUserDomainMask,true).first() as String)+"/SriSuMomentVideos/"
    require(path.startsWith(root) && Regex("[a-fA-F0-9-]+\\.mp4").matches(path.removePrefix(root)))
    return path
}
actual suspend fun readMomentVideoChunk(video:LocalMomentVideo,offset:Long,count:Int):ByteArray = withContext(Dispatchers.Default) {
    require(offset>=0 && count in 1..2*1024*1024 && offset+count<=video.size)
    val handle=checkNotNull(NSFileHandle.fileHandleForReadingAtPath(ownedVideoPath(video.path)))
    try {handle.seekToFileOffset(offset.toULong());val data=handle.readDataOfLength(count.toULong())
        require(data.length.toInt()==count);checkNotNull(data.bytes).readBytes(count)
    } finally {handle.closeFile()}
}
actual fun removeMomentVideoFile(video:LocalMomentVideo) {runCatching {NSFileManager.defaultManager.removeItemAtPath(ownedVideoPath(video.path),null)}}
@Composable actual fun rememberMomentVideoPicker():MomentVideoPicker = remember {MomentVideoPickerRuntime.create()}

interface IosMomentVideoPlayer {
    val controller:UIViewController
    fun update(playing:Boolean,muted:Boolean)
    fun close()
}
object MomentVideoPlayerRuntime {
    private var factory:((MomentPlayback,(MomentPlaybackEvent)->Unit)->IosMomentVideoPlayer)?=null
    fun install(value:(MomentPlayback,(MomentPlaybackEvent)->Unit)->IosMomentVideoPlayer) {factory=value}
    internal fun create(source:MomentPlayback,callback:(MomentPlaybackEvent)->Unit)=checkNotNull(factory)(source,callback)
}
@Composable actual fun MomentVideoPlayer(source:MomentPlayback,playing:Boolean,muted:Boolean,modifier:Modifier,embedded:Boolean,onEvent:(MomentPlaybackEvent)->Unit) {
    val callback by rememberUpdatedState(onEvent)
    val native=remember(source) {MomentVideoPlayerRuntime.create(source) {callback(it)}}
    DisposableEffect(native) {onDispose {native.close()}}
    SideEffect {native.update(playing,muted)}
    UIKitViewController(factory={native.controller},modifier=modifier)
}
