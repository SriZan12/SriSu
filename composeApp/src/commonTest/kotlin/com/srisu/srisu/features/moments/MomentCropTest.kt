package com.srisu.srisu.features.moments

import androidx.lifecycle.viewModelScope
import com.srisu.srisu.core.data.remote.ApiJson
import com.srisu.srisu.features.couplechat.presentation.NativePhotoBatch
import com.srisu.srisu.features.moments.data.*
import com.srisu.srisu.features.moments.presentation.MomentComposerViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class MomentCropTest {
    @Test fun portraitFillCanBeDraggedToKeepTheTopOfThePhoto() {
        val filled = MomentCrop.centered(.5, 1.5)
        assertEquals(1.0, filled.width)
        assertEquals(1.0 / 3, filled.height, .000001)
        val top = filled.move(0.0, 10.0)
        assertEquals(0.0, top.y)
        assertEquals(filled.width, top.width)
        assertEquals(0.0, top.x)
        assertTrue(top.valid)
    }
    @Test fun zoomAndPanStayInsideSourceAndPreserveAspect() {
        for (source in listOf(.2, .5, 1.0, 1.5, 5.0)) for (target in listOf(.8, 1.0, 16.0 / 9)) {
            val base = MomentCrop.centered(source, target)
            for (focal in listOf(0.0, .5, 1.0)) {
                val zoomed = base.zoom(100.0, source, focal, focal).move(50.0, -50.0)
                assertTrue(zoomed.valid)
                assertEquals(target, zoomed.aspect(source), .000001)
                assertEquals(4.0, zoomed.zoomLevel(source), .000001)
                val restored = zoomed.zoom(.001, source)
                assertTrue(restored.valid)
                assertEquals(base.width, restored.width, .000001)
                assertEquals(base.height, restored.height, .000001)
                assertEquals(1.0, restored.zoomLevel(source), .000001)
            }
        }
    }
    @Test fun zoomKeepsFocalSourcePixelUnderTheFinger() {
        val crop = MomentCrop.centered(1.5, 1.0)
        val zoomed = crop.zoom(2.0, 1.5, .25, .75)
        assertEquals(crop.x + crop.width * .25, zoomed.x + zoomed.width * .25, .000001)
        assertEquals(crop.y + crop.height * .75, zoomed.y + zoomed.height * .75, .000001)
    }
    @Test fun fullAndInvalidBounds() {
        assertTrue(MomentCrop.Full.full)
        assertFalse(MomentCrop(-.1, 0.0, 1.0, 1.0).valid)
        assertFalse(MomentCrop(0.0, 0.0, Double.NaN, 1.0).valid)
        assertFalse(MomentCrop(0.0, 0.0, 0.0, 1.0).valid)
        assertEquals(MomentCrop.Full, MomentCrop.Full.zoom(Double.NaN, 1.0))
    }
    @Test fun cropBelongsToPhotoAcrossReorderingRemovalAndPublication() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source = FakeMoments()
        val vm = MomentComposerViewModel(source, MomentFixtures.sessions())
        try {
            vm.open(3); runCurrent()
            vm.pickedPhotos(NativePhotoBatch(listOf(MomentFixtures.image, MomentFixtures.image)), vm.pickerStamp())
            val first = vm.state.value.photos[0].id
            val second = vm.state.value.photos[1].id
            val crop = MomentCrop(.25, 0.0, .5, 1.0)
            vm.crop(first, crop); vm.crop(second, crop); vm.reorder(first, 1)
            assertEquals(listOf(second, first), vm.state.value.record!!.order)
            assertEquals(crop, vm.state.value.record!!.crops[first])
            vm.remove(second)
            assertEquals(mapOf(first to crop), vm.state.value.record!!.crops)
            vm.share(); runCurrent()
            assertEquals(mapOf(first to crop), source.sentIntent!!.crops)
            assertEquals(listOf(first), source.sentIntent!!.photoIds)
            assertEquals(1, source.uploads)
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }
    @Test fun cropPersistsAndRetryFreezesItsExactIntent() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val values = mutableMapOf<String, String>()
        val sessions = MomentFixtures.sessions(values)
        val source = FakeMoments().also { it.lostPublication = true }
        val vm = MomentComposerViewModel(source, sessions)
        var restored: MomentComposerViewModel? = null
        try {
            vm.open(3); runCurrent()
            vm.pickedPhotos(NativePhotoBatch(listOf(MomentFixtures.image)), vm.pickerStamp())
            val photo = vm.state.value.photos.single().id
            val crop = MomentCrop(.25, .25, .5, .5)
            vm.crop(photo, crop); vm.share(); runCurrent()
            val intent = source.sentIntent!!
            vm.crop(photo, null)
            assertEquals(mapOf(photo to crop), vm.state.value.record!!.crops)
            source.published = null // Simulate an unconfirmed publication while reconnecting.
            vm.viewModelScope.cancel()
            restored = MomentComposerViewModel(source, sessions)
            restored.open(3); runCurrent()
            assertEquals(intent, restored.state.value.record!!.publication)
            assertEquals(crop, restored.state.value.record!!.crops[photo])
            restored.share(); runCurrent()
            assertEquals(intent, source.sentIntent)
            assertEquals(1, source.uploads)
        } finally { vm.viewModelScope.cancel(); restored?.viewModelScope?.cancel(); Dispatchers.resetMain() }
    }
    @Test fun resetAndLateAccountCallbacksCannotAttachStaleCrops() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val sessions = MomentFixtures.sessions()
        val vm = MomentComposerViewModel(FakeMoments(), sessions)
        try {
            vm.open(3); runCurrent()
            vm.pickedPhotos(NativePhotoBatch(listOf(MomentFixtures.image)), vm.pickerStamp())
            val photo = vm.state.value.photos.single().id
            vm.crop(photo, MomentCrop(.25, .25, .5, .5)); vm.crop(photo, MomentCrop.Full)
            assertTrue(vm.state.value.record!!.crops.isEmpty())
            sessions.clearSession(); runCurrent()
            vm.crop(photo, MomentCrop(.25, .25, .5, .5))
            assertNull(vm.state.value.record)
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }
    @Test fun existingSavedDraftsDefaultToFullAndWireRoundTripKeepsCoordinates() {
        val old = ApiJson.decodeFromString<MomentDraftRecord>("""{"id":"old","couple":3}""")
        assertTrue(old.crops.isEmpty())
        val intent = PublishMoment(2, "Together", "private", listOf("photo"), mapOf("photo" to MomentCrop(.1, .2, .7, .6)))
        assertEquals(intent, ApiJson.decodeFromString<PublishMoment>(ApiJson.encodeToString(intent)))
    }
}
