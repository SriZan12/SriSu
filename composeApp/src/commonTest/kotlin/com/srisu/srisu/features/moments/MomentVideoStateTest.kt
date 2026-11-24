package com.srisu.srisu.features.moments

import androidx.lifecycle.viewModelScope
import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.features.moments.data.*
import com.srisu.srisu.features.moments.presentation.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class MomentVideoStateTest {
    private val local=LocalMomentVideo("/synthetic/video.mp4",5,1200,160,240)
    private class Videos:FakeMoments() {
        var status:MomentVideoInfo?=null
        val offsets=mutableListOf<Long>()
        var loseFirst=true
        var polls=0
        override suspend fun beginVideo(id:String,video:String,size:Long):MomentResult<MomentVideoInfo> {
            if(status==null) {
                status=MomentVideoInfo(video,"uploading",size,chunkBytes=3)
                staged=staged!!.copy(revision=2,photos=listOf(StagedPhoto(video,"poster",160,240,status)))
            }
            return NetworkAPIResult.Success(status)
        }
        override suspend fun videoChunk(id:String,video:String,offset:Long,bytes:ByteArray):MomentResult<MomentVideoInfo> {
            offsets+=offset;status=status!!.copy(receivedBytes=offset+bytes.size)
            if(loseFirst) {loseFirst=false;return MomentFixtures.failed()}
            return NetworkAPIResult.Success(status)
        }
        override suspend fun completeVideo(id:String,video:String):MomentResult<MomentVideoInfo> {
            status=status!!.copy(state="queued");return NetworkAPIResult.Success(status)
        }
        override suspend fun videoStatus(id:String,video:String):MomentResult<MomentVideoInfo> {
            status=status!!.copy(state=if(polls++==0) "processing" else "ready",durationMs=1200)
            staged=staged!!.copy(photos=listOf(StagedPhoto(video,"poster",160,240,status)))
            return NetworkAPIResult.Success(status)
        }
    }
    @Test fun lostChunkResponseResumesWithoutUploadingConfirmedBytesAgain()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source=Videos();val reads=mutableListOf<Long>()
        val vm=MomentComposerViewModel(source,MomentFixtures.sessions()) {_,offset,count->reads+=offset;ByteArray(count){7}}
        try {
            vm.open(3);runCurrent();vm.pickedVideo(MomentVideoSelection(local),vm.pickerStamp())
            vm.share();runCurrent()
            assertFalse(vm.state.value.busy);assertEquals(listOf(0L),reads);assertEquals(0,source.publications)
            vm.share();runCurrent();assertTrue(vm.state.value.busy)
            advanceTimeBy(2000);runCurrent()
            assertEquals(listOf(0L,3L),reads);assertEquals(reads,source.offsets)
            assertEquals(7,vm.state.value.publishedId);assertEquals(1,source.publications)
        } finally {vm.viewModelScope.cancel();Dispatchers.resetMain()}
    }
    @Test fun pauseKeepsDraftAndPreventsBackgroundPublication()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source=Videos().apply {loseFirst=false}
        val vm=MomentComposerViewModel(source,MomentFixtures.sessions()) {_,_,count->ByteArray(count)}
        try {
            vm.open(3);runCurrent();vm.pickedVideo(MomentVideoSelection(local),vm.pickerStamp())
            vm.share();runCurrent();assertTrue(vm.state.value.busy)
            vm.cancelUpload();advanceTimeBy(10000);runCurrent()
            assertEquals(0,source.publications);assertFalse(vm.state.value.busy)
            assertEquals(1,vm.state.value.record?.videos?.size)
        } finally {vm.viewModelScope.cancel();Dispatchers.resetMain()}
    }
    @Test fun videoPosterDoesNotRecordViewAndBackgroundClearsPlaybackCredentials()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source=object:FakeMoments() {
            override suspend fun playback(video:String):MomentResult<MomentPlayback> = NetworkAPIResult.Success(MomentPlayback("https://api.example.invalid/synthetic"))
        }.apply {moment=NetworkAPIResult.Success(MomentFixtures.partner.copy(photos=listOf(MomentFixtures.photo.copy(video=MomentVideoInfo("video","ready")))))}
        val vm=MomentViewerViewModel(source,MomentFixtures.sessions())
        try {
            vm.open(8,3,null);vm.active(true);runCurrent();assertNotNull(vm.state.value.playback)
            vm.presented();runCurrent();assertEquals(0,source.views)
            vm.videoPresented(999);runCurrent();assertEquals(0,source.views)
            vm.videoPresented(21);runCurrent();assertEquals(1,source.views)
            vm.active(false);assertNull(vm.state.value.playback);assertFalse(vm.state.value.videoReady)
        } finally {vm.viewModelScope.cancel();Dispatchers.resetMain()}
    }
    @Test fun croppingCannotChangeAVideoAndOldPhotosStillDecode()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm=MomentComposerViewModel(FakeMoments(),MomentFixtures.sessions())
        try {
            vm.open(3);runCurrent();vm.pickedVideo(MomentVideoSelection(local),vm.pickerStamp())
            vm.crop(vm.state.value.photos.single().id,MomentCrop(.1,.1,.8,.8))
            assertTrue(vm.state.value.record!!.crops.isEmpty())
            assertNull(com.srisu.srisu.core.data.remote.ApiJson.decodeFromString<MomentPhoto>("""{"id":1,"image":"poster"}""").video)
        } finally {vm.viewModelScope.cancel();Dispatchers.resetMain()}
    }

    @Test fun localVideoDraftRestoresWithoutAMisleadingMissingPhotoError()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val sessions=MomentFixtures.sessions()
        val source=FakeMoments()
        val first=MomentComposerViewModel(source,sessions)
        val restored=MomentComposerViewModel(source,sessions)
        try {
            first.open(3);runCurrent()
            first.pickedVideo(MomentVideoSelection(local),first.pickerStamp())
            first.viewModelScope.cancel()
            restored.open(3);runCurrent()
            assertEquals(local,restored.state.value.photos.single().video)
            assertNull(restored.state.value.error)
        } finally {
            first.viewModelScope.cancel();restored.viewModelScope.cancel();Dispatchers.resetMain()
        }
    }
}
