package com.srisu.srisu.features.settings

import androidx.lifecycle.viewModelScope
import com.srisu.srisu.core.session
import com.srisu.srisu.core.MemoryPreferences
import com.srisu.srisu.core.data.remote.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.*
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.test.*
import kotlin.test.*

val samplePartner = SettingsPartner(2,"Sam",coupleId=4,connectionId=3,revision=2,connectedDays=248)
private open class FakeSettings : SettingsSource {
    var result:NetworkAPIResult<SettingsContext?> = NetworkAPIResult.Success(SettingsContext("settings-1",samplePartner))
    var disconnects=0
    override suspend fun load()=result
    override suspend fun disconnect(partner:SettingsPartner):NetworkAPIResult<SettingsContext?> {
        disconnects++;return NetworkAPIResult.Success(SettingsContext("settings-1"))
    }
}
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsTest {
    @Test fun absentPartnerAndFailedLoadAreDistinct()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source=FakeSettings();val vm=SettingsViewModel(source,session()) {}
        try {
            source.result=NetworkAPIResult.Error(ApiError(NetworkAPIResult.ErrorType.NETWORK,"offline","Offline"))
            vm.reload();runCurrent();assertFalse(vm.state.value.loaded);assertNotNull(vm.state.value.error)
            source.result=NetworkAPIResult.Success(SettingsContext("settings-1"))
            vm.reload();runCurrent();assertTrue(vm.state.value.loaded);assertNull(vm.state.value.partner);assertNull(vm.state.value.error)
        } finally {vm.viewModelScope.cancel();Dispatchers.resetMain()}
    }
    @Test fun duplicateAndStaleDisconnectAreGuarded()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source=FakeSettings();var changes=0;val vm=SettingsViewModel(source,session()) {changes++}
        try {
            vm.reload();runCurrent()
            vm.disconnect(samplePartner.copy(revision=1));runCurrent();assertEquals(0,source.disconnects)
            vm.disconnect(samplePartner);vm.disconnect(samplePartner);runCurrent()
            assertEquals(1,source.disconnects);assertEquals(1,changes);assertNull(vm.state.value.partner)
        } finally {vm.viewModelScope.cancel();Dispatchers.resetMain()}
    }
    @Test fun failedDisconnectReconcilesBeforeRetry()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source=object:FakeSettings() {
            override suspend fun disconnect(partner:SettingsPartner):NetworkAPIResult<SettingsContext?> {
                result=NetworkAPIResult.Success(SettingsContext("settings-1"))
                return NetworkAPIResult.Error(ApiError(NetworkAPIResult.ErrorType.NETWORK,"offline","Offline"))
            }
        }
        var changes=0;val vm=SettingsViewModel(source,session()) {changes++}
        try {
            vm.reload();runCurrent();vm.disconnect(samplePartner);runCurrent()
            assertNull(vm.state.value.error);assertNull(vm.state.value.partner);assertEquals(1,changes)
        } finally {vm.viewModelScope.cancel();Dispatchers.resetMain()}
    }
    @Test fun logoutDropsInFlightPartnerData()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val gate=CompletableDeferred<Unit>();val sessions=session()
        val source=object:FakeSettings() {override suspend fun load():NetworkAPIResult<SettingsContext?> {
            withContext(NonCancellable) {gate.await()};return result
        }}
        val vm=SettingsViewModel(source,sessions) {}
        try {
            vm.reload();runCurrent();sessions.clearSession();runCurrent();gate.complete(Unit);runCurrent()
            assertNull(vm.state.value.partner);assertFalse(vm.state.value.loaded)
        } finally {vm.viewModelScope.cancel();Dispatchers.resetMain()}
    }
    @Test fun appearancePersistsAndDefaultsToSystem()=runTest {
        val storage=MemoryPreferences();val first=AppearancePreferences(storage)
        assertEquals(AppAppearance.SYSTEM,first.appearance.first())
        first.save(AppAppearance.DARK)
        assertEquals(AppAppearance.DARK,AppearancePreferences(storage).appearance.first())
        first.save(AppAppearance.SYSTEM)
        assertEquals(AppAppearance.SYSTEM,first.appearance.first())
    }
    @Test fun appearanceRecoversAfterATemporaryStorageReadFailure()=runTest {
        for(failure in listOf(okio.IOException("Temporary file failure"),androidx.datastore.core.IOException("Temporary storage failure"))) {
        val backing=MemoryPreferences()
        var reads=0
        val storage=object:DataStore<Preferences> {
            override val data=flow {
                if(reads++ == 0) throw failure
                emitAll(backing.data)
            }
            override suspend fun updateData(transform:suspend (Preferences)->Preferences)=backing.updateData(transform)
        }
        val prefs=AppearancePreferences(storage)
        val seen=mutableListOf<AppAppearance>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {prefs.appearance.toList(seen)}
        assertEquals(AppAppearance.SYSTEM,seen.last())
        prefs.save(AppAppearance.DARK)
        advanceTimeBy(2_000);runCurrent()
        assertEquals(AppAppearance.DARK,seen.last())
        }
    }
}
