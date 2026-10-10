@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.srisu.srisu.features.couplechat.presentation

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.container.Mp4OrientationData
import androidx.media3.muxer.BufferInfo
import androidx.media3.muxer.Mp4Muxer
import androidx.media3.muxer.SeekableMuxerOutput
import org.json.JSONObject
import java.nio.ByteBuffer

/** Only media samples and orientation enter a fresh MP4. Location, title, account,
 * capture timestamps, thumbnails and other source metadata are not copied.
 * V1 accepts H.264/AAC clips through 1080p/120s, with 16 MiB output.
 */
internal object AndroidChatVideo {
    fun prepare(context: Context, uri: Uri): Pair<JSONObject,ByteArray> {
        val extractor=MediaExtractor();val retriever=MediaMetadataRetriever()
        val output=MemoryMuxerOutput()
        try {
            retriever.setDataSource(context,uri)
            val duration=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: error("Missing duration")
            val width=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            require(duration in 1..120000 && width in 1..1920 && height in 1..1920 && width.toLong()*height<=1920*1080)
            val rotation=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val thumbnail=retriever.getFrameAtTime(0)?.let {bitmap ->try {AndroidChatMedia.thumbnail(bitmap)} finally {bitmap.recycle()}}
            extractor.setDataSource(context,uri,null)
            val muxer=Mp4Muxer.Builder(output).setSampleCopyingEnabled(false).setSampleBatchingEnabled(false).build()
            try {
                val tracks=mutableMapOf<Int,Int>();var videos=0
                for(i in 0 until extractor.trackCount) {
                    val source=extractor.getTrackFormat(i);val mime=source.getString(MediaFormat.KEY_MIME) ?: continue
                    if(!mime.startsWith("audio/") && !mime.startsWith("video/"))continue
                    require(mime=="video/avc" || mime=="audio/mp4a-latm")
                    val builder=Format.Builder().setSampleMimeType(mime)
                    if(mime=="video/avc") {videos++;builder.setWidth(width).setHeight(height)}
                    else builder.setSampleRate(source.getInteger(MediaFormat.KEY_SAMPLE_RATE)).setChannelCount(source.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
                    val initialization=mutableListOf<ByteArray>()
                    for(n in 0..2) source.getByteBuffer("csd-$n")?.duplicate()?.let {buffer ->initialization.add(ByteArray(buffer.remaining()).also {buffer.get(it)})}
                    builder.setInitializationData(initialization)
                    tracks[i]=muxer.addTrack(i,builder.build());extractor.selectTrack(i)
                }
                require(videos==1 && tracks.size<=2)
                if(rotation in listOf(90,180,270))muxer.addMetadataEntry(Mp4OrientationData(rotation))
                val buffer=ByteBuffer.allocateDirect(2*1024*1024)
                var samples=0
                while(extractor.sampleTrackIndex>=0) {
                    require(++samples<=50000 && extractor.sampleTime<=120000000)
                    buffer.clear();val count=extractor.readSampleData(buffer,0);if(count<0)break
                    require(count<=buffer.capacity() && extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED==0)
                    buffer.position(0);buffer.limit(count)
                    val flags=if(extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC!=0) C.BUFFER_FLAG_KEY_FRAME else 0
                    muxer.writeSampleData(tracks.getValue(extractor.sampleTrackIndex),buffer,BufferInfo(extractor.sampleTime,count,flags))
                    extractor.advance()
                }
                require(samples>0)
            } finally {muxer.close()}
            return JSONObject().put("event","ready").put("kind","video").put("mime","video/mp4").put("width",width).put("height",height)
                .put("durationMs",duration).put("thumbnail",thumbnail) to output.bytes()
        } finally {extractor.release();retriever.release()}
    }
}

/** No file-backed fallback. A clip over the cap fails before exceeding the bound. */
internal class MemoryMuxerOutput : SeekableMuxerOutput {
    private val data=ByteArray(16*1024*1024)
    private var cursor=0
    private var length=0
    private var open=true
    override fun isOpen()=open
    override fun close() {open=false}
    override fun getPosition()=cursor.toLong()
    override fun getSize()=length.toLong()
    override fun setPosition(position: Long) {require(position in 0..data.size.toLong());cursor=position.toInt()}
    override fun truncate(size: Long) {require(size in 0..length.toLong());length=size.toInt();cursor=minOf(cursor,length)}
    override fun write(buffer: ByteBuffer): Int {
        check(open);val count=buffer.remaining();require(count<=data.size-cursor)
        buffer.get(data,cursor,count);cursor+=count;length=maxOf(length,cursor);return count
    }
    fun bytes()=data.copyOf(length)
}
