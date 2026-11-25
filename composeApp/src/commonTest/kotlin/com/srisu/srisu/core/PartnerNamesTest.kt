package com.srisu.srisu.core

import com.srisu.srisu.core.data.remote.*
import com.srisu.srisu.core.identity.*
import com.srisu.srisu.core.lifecycle.ApplicationLifetime
import com.srisu.srisu.features.settings.*
import com.srisu.srisu.navigation.CoupleAccess
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class PartnerNamesTest {
    @Test fun aliasesMatchIdentityAndClearBackToCanonicalName() {
        val names=PartnerNameSnapshot(1,4,2,3,"Sunshine",2,"Sam")
        assertEquals("Sunshine",names.resolve(2,"Sam",4))
        assertEquals("My real name",names.resolve(1,"My real name",4))
        assertEquals("Stranger",names.resolve(9,"Stranger",4))
        assertEquals("Old relationship",names.resolve(2,"Old relationship",8))
        assertEquals("Sam",names.copy(nickname=null).resolve(2,"Old cached nickname",4))
        assertEquals("Literal message mentioning Sam",names.resolve(null,"Literal message mentioning Sam"))
    }
    @Test fun unicodeLengthAndUnsafeInputAreValidated() {
        assertEquals(40,nicknameLength("💛".repeat(40)))
        assertNull(nicknameValidation("💛".repeat(40)))
        assertNotNull(nicknameValidation("💛".repeat(41)))
        assertNotNull(nicknameValidation("Sam\nAdmin"))
        assertNotNull(nicknameValidation("Sam\u202e"))
        assertNull(nicknameValidation(" माया "))
        assertNull(nicknameValidation(""))
    }
    @Test fun refreshDeduplicatesAndOldReadsCannotRollBackSavedNickname()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val sessions=session();val access=MutableStateFlow<CoupleAccess>(CoupleAccess.Ready(sessions.stamp(),4,listOf(1,2)))
        val lifetime=ApplicationLifetime();val gate=CompletableDeferred<Unit>();var reads=0
        val old=SettingsContext("settings-1",samplePartner.copy(nickname="Old",nicknameRevision=1))
        val source=object:SettingsSource {
            override suspend fun load():NetworkAPIResult<SettingsContext?> {reads++;gate.await();return NetworkAPIResult.Success(old)}
            override suspend fun disconnect(partner:SettingsPartner):NetworkAPIResult<SettingsContext?> = error("Not used")
        }
        val names=PartnerNames(sessions,access,source,lifetime)
        try {
            lifetime.setForeground(true);runCurrent();names.refresh();names.refresh();runCurrent();assertEquals(1,reads)
            names.accept(old.copy(partner=old.partner!!.copy(nickname="New",nicknameRevision=2)),sessions.stamp())
            gate.complete(Unit);runCurrent();assertEquals("New",names.state.value.nickname)
            names.accept(old.copy(partner=old.partner!!.copy(nickname=null,nicknameRevision=3)),sessions.stamp())
            assertEquals("Sam",names.state.value.resolve(2,"Old"))
        } finally {lifetime.close();Dispatchers.resetMain()}
    }
    @Test fun membershipAndSessionChangesDiscardAliasesAndLateResponses()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val sessions=session();val access=MutableStateFlow<CoupleAccess>(CoupleAccess.Ready(sessions.stamp(),4,listOf(1,2)))
        val lifetime=ApplicationLifetime();val gate=CompletableDeferred<Unit>()
        val context=SettingsContext("settings-1",samplePartner.copy(nickname="Private",nicknameRevision=1))
        val source=object:SettingsSource {
            override suspend fun load():NetworkAPIResult<SettingsContext?> {
                withContext(NonCancellable) {gate.await()};return NetworkAPIResult.Success(context)
            }
            override suspend fun disconnect(partner:SettingsPartner):NetworkAPIResult<SettingsContext?> = error("Not used")
        }
        val names=PartnerNames(sessions,access,source,lifetime)
        try {
            lifetime.setForeground(true);runCurrent();names.accept(context,sessions.stamp())
            assertEquals("Private",names.state.value.nickname)
            access.value=CoupleAccess.Ready(sessions.stamp(),null,emptyList());runCurrent()
            assertNull(names.state.value.nickname)
            gate.complete(Unit);runCurrent();assertNull(names.state.value.nickname)
            access.value=CoupleAccess.Ready(sessions.stamp(),5,listOf(1,3));runCurrent()
            names.accept(context,sessions.stamp());assertNull(names.state.value.nickname)
            sessions.clearSession();runCurrent();assertNull(names.state.value.partnerId)
        } finally {lifetime.close();Dispatchers.resetMain()}
    }
}
