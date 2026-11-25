package com.srisu.srisu.features.settings

import androidx.lifecycle.viewModelScope
import com.srisu.srisu.core.session
import com.srisu.srisu.core.data.remote.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class PartnerNicknameTest {
    private open class Source: SettingsSource {
        var partner=samplePartner.copy(nicknameRevision=1)
        var writes=0
        override suspend fun load():NetworkAPIResult<SettingsContext?> = NetworkAPIResult.Success(SettingsContext("settings-1",partner))
        override suspend fun disconnect(partner:SettingsPartner):NetworkAPIResult<SettingsContext?> = error("Unused")
        override suspend fun saveNickname(partner:SettingsPartner,nickname:String):NetworkAPIResult<SettingsContext?> {
            writes++;this.partner=partner.copy(nickname=nickname.ifBlank {null},nicknameRevision=partner.nicknameRevision!!+1)
            return load()
        }
    }
    @Test fun duplicateSaveClearAndInvalidInput()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source=Source();val vm=SettingsViewModel(source,session()) {}
        try {
            vm.reload();runCurrent();val partner=vm.state.value.partner!!
            vm.saveNickname(partner,"💛".repeat(41));runCurrent();assertEquals(0,source.writes)
            vm.saveNickname(partner,"  Sunshine  ");vm.saveNickname(partner,"Other");runCurrent()
            assertEquals(1,source.writes);assertEquals("Sunshine",vm.state.value.partner!!.nickname)
            assertEquals(1L,vm.state.value.nicknameSaved)
            vm.reload();runCurrent();assertEquals(1L,vm.state.value.nicknameSaved)
            vm.saveNickname(partner,"Stale");runCurrent();assertEquals(1,source.writes)
            vm.saveNickname(vm.state.value.partner!!,"");runCurrent()
            assertNull(vm.state.value.partner!!.nickname);assertEquals(2L,vm.state.value.nicknameSaved)
        } finally {vm.viewModelScope.cancel();Dispatchers.resetMain()}
    }
    @Test fun lostResponseReconcilesWithoutDuplicatingWrite()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source=object:Source() {
            override suspend fun saveNickname(partner:SettingsPartner,nickname:String):NetworkAPIResult<SettingsContext?> {
                super.saveNickname(partner,nickname)
                return NetworkAPIResult.Error("Timed out",NetworkAPIResult.ErrorType.TIMEOUT)
            }
        }
        val vm=SettingsViewModel(source,session()) {}
        try {
            vm.reload();runCurrent();vm.saveNickname(vm.state.value.partner!!,"Moon");runCurrent()
            assertEquals(1,source.writes);assertEquals("Moon",vm.state.value.partner!!.nickname)
            assertNull(vm.state.value.nicknameError);assertFalse(vm.state.value.nicknameSaving)
        } finally {vm.viewModelScope.cancel();Dispatchers.resetMain()}
    }
    @Test fun conflictRefreshesRevisionAndDoesNotSilentlyOverwrite()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source=object:Source() {
            override suspend fun saveNickname(partner:SettingsPartner,nickname:String):NetworkAPIResult<SettingsContext?> {
                writes++;this.partner=partner.copy(nickname="From another device",nicknameRevision=3)
                return NetworkAPIResult.Error(ApiError(NetworkAPIResult.ErrorType.CONFLICT,"conflict","Changed",status=409))
            }
        }
        val vm=SettingsViewModel(source,session()) {}
        try {
            vm.reload();runCurrent();vm.saveNickname(vm.state.value.partner!!,"Local draft");runCurrent()
            assertEquals(1,source.writes);assertEquals(3L,vm.state.value.partner!!.nicknameRevision)
            assertEquals("From another device",vm.state.value.partner!!.nickname)
            assertNotNull(vm.state.value.nicknameError);assertEquals(0L,vm.state.value.nicknameSaved)
        } finally {vm.viewModelScope.cancel();Dispatchers.resetMain()}
    }
    @Test fun logoutCancelsSaveAndOldResponseCannotRestorePartner()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val gate=CompletableDeferred<Unit>();val sessions=session()
        val source=object:Source() {
            override suspend fun saveNickname(partner:SettingsPartner,nickname:String):NetworkAPIResult<SettingsContext?> {
                withContext(NonCancellable) {gate.await()};return super.saveNickname(partner,nickname)
            }
        }
        val vm=SettingsViewModel(source,sessions) {}
        try {
            vm.reload();runCurrent();vm.saveNickname(vm.state.value.partner!!,"Private");runCurrent()
            sessions.clearSession();runCurrent();gate.complete(Unit);runCurrent()
            assertNull(vm.state.value.partner);assertFalse(vm.state.value.loaded)
        } finally {vm.viewModelScope.cancel();Dispatchers.resetMain()}
    }
    @Test fun disconnectedDuringSaveRefreshesMembership()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var disconnected=false;var changes=0
        val source=object:Source() {
            override suspend fun load():NetworkAPIResult<SettingsContext?> =
                if(disconnected) NetworkAPIResult.Success(SettingsContext("settings-1")) else super.load()
            override suspend fun saveNickname(partner:SettingsPartner,nickname:String):NetworkAPIResult<SettingsContext?> {
                disconnected=true
                return NetworkAPIResult.Error(ApiError(NetworkAPIResult.ErrorType.CONFLICT,"conflict","Changed",status=409))
            }
        }
        val vm=SettingsViewModel(source,session()) {changes++}
        try {
            vm.reload();runCurrent();vm.saveNickname(vm.state.value.partner!!,"My draft");runCurrent()
            assertNull(vm.state.value.partner);assertEquals(1,changes)
        } finally {vm.viewModelScope.cancel();Dispatchers.resetMain()}
    }
    @Test fun failedWriteAndReadCanRecoverThenSaveWithoutLosingCounter()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var offline=false
        val source=object:Source() {
            override suspend fun load():NetworkAPIResult<SettingsContext?> =
                if(offline) NetworkAPIResult.Error("Offline",NetworkAPIResult.ErrorType.NETWORK) else super.load()
            override suspend fun saveNickname(partner:SettingsPartner,nickname:String):NetworkAPIResult<SettingsContext?> =
                if(offline) NetworkAPIResult.Error("Offline",NetworkAPIResult.ErrorType.NETWORK) else super.saveNickname(partner,nickname)
        }
        val vm=SettingsViewModel(source,session()) {}
        try {
            vm.reload();runCurrent();val expected=vm.state.value.partner!!
            offline=true;vm.saveNickname(expected,"Kept draft");runCurrent()
            assertFalse(vm.state.value.loaded);assertEquals(expected,vm.state.value.partner)
            assertNotNull(vm.state.value.nicknameError)
            offline=false;vm.reload();runCurrent();assertTrue(vm.state.value.loaded)
            vm.saveNickname(vm.state.value.partner!!,"Kept draft");runCurrent()
            assertEquals("Kept draft",vm.state.value.partner!!.nickname);assertEquals(1L,vm.state.value.nicknameSaved)
        } finally {vm.viewModelScope.cancel();Dispatchers.resetMain()}
    }
}
