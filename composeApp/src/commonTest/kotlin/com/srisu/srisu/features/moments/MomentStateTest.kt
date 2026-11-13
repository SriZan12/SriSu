package com.srisu.srisu.features.moments

import androidx.lifecycle.viewModelScope
import com.srisu.srisu.core.data.remote.*
import com.srisu.srisu.core.CoreContractFixtures
import com.srisu.srisu.features.moments.data.*
import com.srisu.srisu.features.moments.presentation.*
import com.srisu.srisu.features.couplechat.presentation.NativePhotoBatch
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class MomentStateTest {
    // Both native library and camera adapters emit `photo`, not the MIME category `image`.
    private val nativePhoto = """{"event":"ready","kind":"photo","mime":"image/jpeg","width":12,"height":12,"thumbnail":""}"""

    @Test fun multiplePhotosKeepPickerOrderAndUseRemainingDraftSlots()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source=FakeMoments();val vm=MomentComposerViewModel(source,MomentFixtures.sessions())
        try {
            vm.open(3);runCurrent();vm.caption("A whole afternoon")
            vm.pickedPhotos(NativePhotoBatch(listOf(byteArrayOf(1),byteArrayOf(2))),vm.pickerStamp())
            assertEquals(3,vm.remainingPhotoSlots())
            val originalIds=vm.state.value.photos.map {it.id}
            vm.pickedPhotos(NativePhotoBatch(listOf(byteArrayOf(3),byteArrayOf(4),byteArrayOf(5),byteArrayOf(6))),vm.pickerStamp())
            assertEquals(listOf<Byte>(1,2,3,4,5),vm.state.value.photos.map {it.bytes!!.single()})
            assertEquals(originalIds,vm.state.value.photos.take(2).map {it.id})
            assertEquals("You can add up to five photos.",vm.state.value.error)
            assertEquals(0,vm.remainingPhotoSlots())
            val order=vm.state.value.photos.map {it.id}
            assertEquals(order,vm.state.value.record?.order)
            vm.share();runCurrent()
            assertEquals(order,source.sentIntent?.photoIds)
            assertEquals("A whole afternoon",source.sentIntent?.caption)
        } finally {vm.viewModelScope.cancel();Dispatchers.resetMain()}
    }
    @Test fun partialBatchFailureKeepsGoodPhotosAndCancellationKeepsDraft()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm=MomentComposerViewModel(FakeMoments(),MomentFixtures.sessions())
        try {
            vm.open(3);runCurrent()
            vm.pickedPhotos(NativePhotoBatch(listOf(MomentFixtures.image,byteArrayOf(),ByteArray(10*1024*1024+1)),1),vm.pickerStamp())
            assertEquals(1,vm.state.value.photos.size)
            assertTrue(vm.state.value.error!!.contains("2 photos could not be opened"))
            assertTrue(vm.state.value.error!!.contains("1 photo exceeds 10 MiB"))
            val before=vm.state.value
            vm.pickedPhotos(NativePhotoBatch(emptyList()),vm.pickerStamp())
            assertSame(before,vm.state.value)
            vm.pickedPhotos(NativePhotoBatch(listOf(MomentFixtures.image)),vm.pickerStamp())
            assertNull(vm.state.value.error);assertEquals(2,vm.state.value.photos.size)
        } finally {vm.viewModelScope.cancel();Dispatchers.resetMain()}
    }
    @Test fun accountSwitchDropsEntireLatePickerBatch()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val sessions=MomentFixtures.sessions();val vm=MomentComposerViewModel(FakeMoments(),sessions)
        try {
            vm.open(3);runCurrent();val stamp=vm.pickerStamp()
            sessions.clearSession();runCurrent()
            vm.pickedPhotos(NativePhotoBatch(listOf(MomentFixtures.image,MomentFixtures.image)),stamp)
            assertTrue(vm.state.value.photos.isEmpty());assertNull(vm.state.value.record)
        } finally {vm.viewModelScope.cancel();Dispatchers.resetMain()}
    }

    @Test fun nativePhotoCallbackAcceptsSmallPhotoAndPublishes()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source=FakeMoments();val vm=MomentComposerViewModel(source,MomentFixtures.sessions())
        try {
            vm.open(3);runCurrent()
            vm.picked(nativePhoto,MomentFixtures.image,vm.pickerStamp())
            assertNull(vm.state.value.error)
            assertContentEquals(MomentFixtures.image,vm.state.value.photos.single().bytes)
            vm.share();runCurrent()
            assertEquals(7,vm.state.value.publishedId)
            assertEquals(1,source.publications)
        } finally {vm.viewModelScope.cancel();Dispatchers.resetMain()}
    }
    @Test fun photoSizeLimitAcceptsExactBoundaryAndRejectsOnlyLargerPayloads()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm=MomentComposerViewModel(FakeMoments(),MomentFixtures.sessions())
        try {
            vm.open(3);runCurrent();vm.caption("Keep my draft")
            // This is the client byte-count boundary; server tests validate real image content.
            vm.picked(nativePhoto,ByteArray(10*1024*1024),vm.pickerStamp())
            assertNull(vm.state.value.error);assertEquals(1,vm.state.value.photos.size)
            vm.picked(nativePhoto,ByteArray(10*1024*1024+1),vm.pickerStamp())
            assertEquals("Choose a photo no larger than 10 MiB.",vm.state.value.error)
            assertEquals(1,vm.state.value.photos.size)
            assertEquals("Keep my draft",vm.state.value.record?.caption)
            vm.picked(nativePhoto,MomentFixtures.image,vm.pickerStamp())
            assertNull(vm.state.value.error);assertEquals(2,vm.state.value.photos.size)
        } finally {vm.viewModelScope.cancel();Dispatchers.resetMain()}
    }
    @Test fun unsupportedOrUnreadableMediaNeverReportsASizeError()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm=MomentComposerViewModel(FakeMoments(),MomentFixtures.sessions())
        try {
            vm.open(3);runCurrent()
            for(kind in listOf("video","voice","image")) {
                vm.picked("""{"event":"ready","kind":"$kind"}""",MomentFixtures.image,vm.pickerStamp())
                assertEquals("Choose a photo for your Moment.",vm.state.value.error)
            }
            for(bytes in listOf(null,byteArrayOf())) {
                vm.picked(nativePhoto,bytes,vm.pickerStamp())
                assertEquals("This photo could not be opened. Try another photo.",vm.state.value.error)
            }
            assertTrue(vm.state.value.photos.isEmpty())
            vm.picked(nativePhoto,MomentFixtures.image,vm.pickerStamp())
            vm.picked("""{"event":"cancelled"}""",null,vm.pickerStamp())
            assertNull(vm.state.value.error);assertEquals(1,vm.state.value.photos.size)
        } finally {vm.viewModelScope.cancel();Dispatchers.resetMain()}
    }
    @Test fun sharedBackendFixturesDecodeAndReferencesRejectForeignUrls() {
        val json=ApiJson
        val moment=json.decodeFromString<Moment>(CoreContractFixtures.MOMENT_PRIVATE)
        val home=json.decodeFromJsonElement<MomentHome>(json.parseToJsonElement(CoreContractFixtures.MOMENT_HOME).jsonObject.getValue("data"))
        val draft=json.decodeFromJsonElement<MomentDraft>(json.parseToJsonElement(CoreContractFixtures.MOMENT_DRAFT).jsonObject.getValue("data"))
        assertEquals("private",moment.visibility); assertFalse(moment.canReply)
        assertEquals(3,home.couple?.id);assertEquals(1,draft.photos.size)
        val ref=MomentReference(7,3);assertEquals(ref,MomentReference.parse(ref.url))
        listOf("https://example.org/7", "srisu://moments/0/couples/3", "srisu://moments/7/couples/3?photo=secret", "srisu://moments/9999999999999999999/couples/3").forEach {assertNull(MomentReference.parse(it))}
    }
    @Test fun homeDeduplicatesAndKeepsIdentityDuringPartialFailure()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source=FakeMoments();val vm=HomeMomentsViewModel(source,MomentFixtures.sessions())
        try {
            vm.visible(true);vm.refresh();vm.refresh();runCurrent();assertEquals(1,source.homeCalls)
            source.homeResult=MomentFixtures.failed();vm.refresh();runCurrent()
            assertEquals("Alex",vm.state.value.name);assertNotNull(vm.state.value.home);assertFalse(vm.state.value.fresh);assertNotNull(vm.state.value.error)
        } finally {vm.viewModelScope.cancel();Dispatchers.resetMain()}
    }
    @Test fun uploadFailurePreservesPhotosAndUncertainPublicationReconcilesSameDraft()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val values=mutableMapOf<String,String>();val sessions=MomentFixtures.sessions(values);val source=FakeMoments()
        val vm=MomentComposerViewModel(source,sessions)
        try {
            vm.open(3);runCurrent();val id=vm.state.value.record!!.id
            vm.picked(nativePhoto,MomentFixtures.image,vm.pickerStamp())
            vm.caption("Keep this caption");source.failUpload=true;vm.share();runCurrent()
            assertEquals(1,vm.state.value.photos.size);assertEquals("Keep this caption",vm.state.value.record?.caption);assertNull(vm.state.value.publishedId)
            source.failUpload=false;source.lostPublication=true;vm.share();runCurrent()
            assertNotNull(vm.state.value.record?.publication);assertFalse(vm.state.value.busy)
            vm.caption("Must not change uncertain request");vm.share();runCurrent()
            assertEquals(7,vm.state.value.publishedId);assertEquals(1,source.publications);assertEquals(id,source.staged?.id)
            assertEquals("Keep this caption",source.sentIntent?.caption)
            assertEquals("",values["moment-draft:1:3"])
        } finally {vm.viewModelScope.cancel();Dispatchers.resetMain()}
    }
    @Test fun removalAfterUncertainUploadReconcilesRemotePhotoBeforePublication()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source=object:FakeMoments() {
            var removed:String?=null
            override suspend fun upload(id:String,photo:String,bytes:ByteArray,progress:(Float)->Unit):MomentResult<MomentDraft> {
                super.upload(id,photo,bytes,progress);return MomentFixtures.failed()
            }
            override suspend fun removePhoto(id:String,photo:String):NetworkAPIResult.Success<MomentDraft?> {
                removed=photo;staged=staged!!.copy(photos=emptyList());return NetworkAPIResult.Success(staged)
            }
        }
        val vm=MomentComposerViewModel(source,MomentFixtures.sessions())
        try {
            vm.open(3);runCurrent();vm.picked(nativePhoto,MomentFixtures.image,vm.pickerStamp())
            val photo=vm.state.value.photos.single().id
            vm.share();runCurrent();assertEquals(1,source.staged?.photos?.size)
            vm.remove(photo);runCurrent();assertEquals(photo,source.removed);assertTrue(vm.state.value.photos.isEmpty());assertTrue(source.staged!!.photos.isEmpty())
        } finally {vm.viewModelScope.cancel();Dispatchers.resetMain()}
    }
    @Test fun recoverableMetadataRestoresWithoutPersistingImageBytes()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val values=mutableMapOf<String,String>();val sessions=MomentFixtures.sessions(values);val source=FakeMoments()
        val first=MomentComposerViewModel(source,sessions)
        var second:MomentComposerViewModel?=null
        try {
            first.open(3);runCurrent();first.caption("Still here")
            first.picked(nativePhoto,MomentFixtures.image,first.pickerStamp())
            val id=first.state.value.record!!.id;first.viewModelScope.cancel()
            second=MomentComposerViewModel(source,sessions);second.open(3);runCurrent()
            assertEquals(id,second.state.value.record?.id);assertEquals("Still here",second.state.value.record?.caption)
            assertTrue(second.state.value.photos.isEmpty());assertNotNull(second.state.value.error)
            assertFalse(values["moment-draft:1:3"]!!.contains("bytes"))
        } finally {first.viewModelScope.cancel();second?.viewModelScope?.cancel();Dispatchers.resetMain()}
    }
    @Test fun prefetchDoesNotRecordViewAndExactExpiryClearsPixels()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source=FakeMoments();source.moment=NetworkAPIResult.Success(MomentFixtures.partner.copy(expiresAt="2026-10-22T06:00:01Z"))
        val vm=MomentViewerViewModel(source,MomentFixtures.sessions())
        try {
            vm.open(8,3,null);vm.active(true);runCurrent()
            assertNotNull(vm.state.value.bytes);assertEquals(0,source.views)
            vm.presented();vm.presented();runCurrent();assertEquals(1,source.views)
            advanceTimeBy(1000);runCurrent();assertNull(vm.state.value.bytes);assertEquals("expired",vm.state.value.unavailable)
        } finally {vm.viewModelScope.cancel();Dispatchers.resetMain()}
    }
    @Test fun accountSwitchRejectsLatePhotosAndPickerCallbacks()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val pending=CompletableDeferred<ByteArray?>();val source=object:FakeMoments(){override suspend fun image(url:String)=withContext(NonCancellable){pending.await()}}
        val sessions=MomentFixtures.sessions();val viewer=MomentViewerViewModel(source,sessions);val composer=MomentComposerViewModel(source,sessions)
        try {
            viewer.open(8,3,null);viewer.active(true);composer.open(3);runCurrent();val stamp=composer.pickerStamp()
            sessions.clearSession();runCurrent();pending.complete(MomentFixtures.image);runCurrent()
            composer.picked(nativePhoto,MomentFixtures.image,stamp)
            assertNull(viewer.state.value.bytes);assertFalse(viewer.state.value.fresh);assertTrue(composer.state.value.photos.isEmpty())
        } finally {viewer.viewModelScope.cancel();composer.viewModelScope.cancel();Dispatchers.resetMain()}
    }
    @Test fun chatReferenceExpiresAndNeverRecordsAPrefetchView()=runTest {
        val source=FakeMoments();source.moment=NetworkAPIResult.Success(MomentFixtures.partner.copy(expiresAt="2026-10-22T06:00:01Z"))
        val sessions=MomentFixtures.sessions();val resolver=MomentReferenceResolver(source,sessions,backgroundScope);runCurrent()
        val reference=MomentReference(8,3);val state=resolver.observe(reference)
        resolver.retain(reference);resolver.active(true);runCurrent()
        assertEquals(MomentReferenceState.AVAILABLE,state.value);assertEquals(0,source.views)
        advanceTimeBy(1000);runCurrent();assertEquals(MomentReferenceState.EXPIRED,state.value)
        resolver.active(false);source.moment=MomentFixtures.failed(404);resolver.active(true);runCurrent()
        assertEquals(MomentReferenceState.UNAVAILABLE,state.value);resolver.release(reference)
    }
    @Test fun stalePaginationRowsAreDeduplicatedAndNextCursorStaysFirstParty()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source=object:FakeMoments(){override suspend fun sequence(couple:Long,creator:Long?,cursor:String?):MomentResult<MomentPage> = NetworkAPIResult.Success(
            if(cursor==null) MomentPage(listOf(MomentFixtures.partner),"https://api.example.invalid/sequence?cursor=next")
            else MomentPage(listOf(MomentFixtures.partner,MomentFixtures.mine)))
            override suspend fun get(id:Long):MomentResult<Moment> = NetworkAPIResult.Success(if(id==7L) MomentFixtures.mine else MomentFixtures.partner)
        }
        val vm=MomentViewerViewModel(source,MomentFixtures.sessions())
        try {vm.open(null,3,2);vm.active(true);runCurrent();assertEquals(8,vm.state.value.moment?.id)
            vm.move(1);runCurrent();assertEquals(7,vm.state.value.moment?.id);vm.move(-1);runCurrent();assertEquals(8,vm.state.value.moment?.id)
        } finally {vm.viewModelScope.cancel();Dispatchers.resetMain()}
    }
}
