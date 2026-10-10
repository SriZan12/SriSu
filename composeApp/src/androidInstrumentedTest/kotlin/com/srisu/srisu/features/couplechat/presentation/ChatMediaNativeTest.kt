package com.srisu.srisu.features.couplechat.presentation

import android.graphics.Bitmap
import android.graphics.Color
import android.media.ExifInterface
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real platform decoding and preparation with synthetic fixtures only. */
@RunWith(AndroidJUnit4::class)
class ChatMediaNativeTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    @Test fun safetyQrUsesNativeEncoderAndDecoder() {
        val text="srisu-safety:1:U3ludGhldGljIHFyIGZpeHR1cmU="
        var driver:AndroidChatMedia?=null;var result:JSONObject?=null;val done=CountDownLatch(1)
        try {
            instrumentation.runOnMainSync {
                driver=AndroidChatMedia(context)
                val png=driver!!.safetyQr(text)!!
                File(context.filesDir,"chat-test-fixtures").mkdirs()
                File(context.filesDir,"chat-test-fixtures/android-safety-qr.png").writeBytes(png)
                driver!!.pick("safety-photo") {raw,_->result=JSONObject(raw);done.countDown()}
                driver!!.captured(android.graphics.BitmapFactory.decodeByteArray(png,0,png.size))
            }
            assertTrue(done.await(15,TimeUnit.SECONDS));assertEquals("qr",result!!.getString("event"));assertEquals(text,result!!.getString("value"))
        } finally {instrumentation.runOnMainSync {driver?.close()}}
    }
    @Test fun photoPreparationAppliesOrientationAndStripsLocation() {
        val source=File(context.cacheDir,"chat-test-photo.jpg")
        val bitmap=Bitmap.createBitmap(640,320,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.GREEN)} // theme-audit: image pixels; synthetic codec fixture
        source.outputStream().use {bitmap.compress(Bitmap.CompressFormat.JPEG,95,it)};bitmap.recycle()
        ExifInterface(source.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_ROTATE_90.toString())
            setAttribute(ExifInterface.TAG_GPS_LATITUDE,"27/1,42/1,0/1");setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF,"N");saveAttributes()
        }
        var media:AndroidChatMedia?=null
        var value:JSONObject?=null;var bytes:ByteArray?=null;val done=CountDownLatch(1)
        try {
            instrumentation.runOnMainSync {
                media=AndroidChatMedia(context)
                media!!.pick("photo") {raw,data ->value=JSONObject(raw);bytes=data;done.countDown()}
                media!!.selected(Uri.fromFile(source))
            }
            assertTrue(done.await(15,TimeUnit.SECONDS));assertEquals("ready",value!!.getString("event"))
            assertEquals(320,value!!.getInt("width"));assertEquals(640,value!!.getInt("height"))
            assertNotNull(bytes);assertNull(ExifInterface(bytes!!.inputStream()).getAttribute(ExifInterface.TAG_GPS_LATITUDE))
            assertTrue(value!!.getString("thumbnail").length<=8192)
        } finally {source.delete();instrumentation.runOnMainSync {media?.close()} }
    }
    @Test fun iosPreparedMp4AndWavDecodeAndVideoMetadataIsRemoved() {
        // The host runner supplies only generated, non-private codec fixtures.
        val folder=File(context.filesDir,"chat-test-fixtures")
        val source=File(folder,"ios-prepared.mp4");assertTrue("Run the native media fixture procedure",source.isFile)
        val prepared=AndroidChatVideo.prepare(context,Uri.fromFile(source))
        assertEquals("video/mp4",prepared.first.getString("mime"))
        assertTrue(prepared.second.size in 1..16*1024*1024)
        val metadata=MediaMetadataRetriever()
        try {metadata.setDataSource(MemoryMediaSource(prepared.second));assertNull(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION))
            val frame=metadata.getFrameAtTime(0);assertNotNull(frame);frame?.recycle()
        } finally {metadata.release()}
        File(folder,"android-prepared.mp4").writeBytes(prepared.second)
        val audio=MediaExtractor()
        try {audio.setDataSource(File(folder,"voice.wav").path);assertEquals(1,audio.trackCount)
            assertEquals("audio/raw",audio.getTrackFormat(0).getString(MediaFormat.KEY_MIME))
            assertEquals(48000,audio.getTrackFormat(0).getInteger(MediaFormat.KEY_SAMPLE_RATE))
        } finally {audio.release()}
    }
}
