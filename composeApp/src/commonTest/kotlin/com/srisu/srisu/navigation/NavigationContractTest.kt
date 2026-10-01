package com.srisu.srisu.navigation

import com.srisu.srisu.navigation.graph.*
import com.srisu.srisu.features.coupleprofile.navigation.*
import kotlinx.serialization.json.Json
import kotlin.test.*

class NavigationContractTest {
    @Test fun pickerResultsAreBoundedAndMatchOnlyTheirRequest() {
        val result = Json.encodeToString(InterestPickerResult("first", listOf(1, 2)))
        assertEquals(listOf(1, 2), decodeInterestResult("first", result)?.ids)
        assertNull(decodeInterestResult("second", result))
        assertNull(decodeInterestResult(null, result))
        assertNull(decodeInterestResult("first", "broken"))
        assertNull(decodeInterestResult("first", Json.encodeToString(InterestPickerResult("first", listOf(-1)))))
        assertNull(decodeInterestResult("first", "x".repeat(8193)))
    }
    @Test fun minimalRoutesRoundTripWithoutJvmReflection() {
        val profile = CoupleProfileDestination(42, CoupleSection.PLAN, 9)
        assertEquals(profile, Json.decodeFromString<CoupleProfileDestination>(Json.encodeToString(profile)))
        val chat = ChatNav.ChatScreen("00000000-0000-4000-8000-000000000001")
        assertEquals(chat, Json.decodeFromString<ChatNav.ChatScreen>(Json.encodeToString(chat)))
        val picker = ProfileNav.InterestScreen("origin-entry", "request-1")
        assertEquals(picker, Json.decodeFromString<ProfileNav.InterestScreen>(Json.encodeToString(picker)))
        assertEquals(AuthNavigation.ProfileSetUp, Json.decodeFromString<AuthNavigation.ProfileSetUp>(Json.encodeToString(AuthNavigation.ProfileSetUp)))
        assertEquals(ChatNav.PartnerPreview(2), Json.decodeFromString<ChatNav.PartnerPreview>("""{"userId":2}"""))
    }
    @Test fun externalInputIsStrictAndCannotPerformActions() {
        assertEquals(CoupleProfileDestination(42), parseExternalEntry("srisu://couples/42"))
        assertIs<ChatNav.ChatScreen>(parseExternalEntry("srisu://chats/00000000-0000-4000-8000-000000000001"))
        listOf("srisu://singles/1", "srisu://couples/-1", "srisu://couples/0", "https://attacker/couples/1",
            "srisu://couples/1/edit", "srisu://couples/1?isOwner=true", "srisu://chats/not-a-uuid",
            "srisu://couples/9223372036854775808", "srisu://couples/1#accept", "x".repeat(513)).forEach { assertNull(parseExternalEntry(it), it) }
    }
    @Test fun pendingLoginIsOneShotAndAccountSwitchClearsIt() {
        val inbox = ExternalEntryInbox()
        assertTrue(inbox.receive("srisu://couples/1", "tap-1"))
        val initial = assertNotNull(inbox.pending.value)
        inbox.reconcile(1)
        assertEquals(CoupleProfileDestination(1), inbox.consume(initial, 1))
        assertNull(inbox.consume(initial, 1))
        assertFalse(inbox.receive("srisu://couples/1", "tap-1"))
        assertTrue(inbox.receive("srisu://couples/2", "tap-2"))
        inbox.reconcile(2)
        assertNull(inbox.pending.value)
        assertTrue(inbox.receive("srisu://couples/3"))
        inbox.reconcile(null)
        assertNull(inbox.pending.value)
    }
    @Test fun wrongAccountCannotConsumeBoundRequestAndInvalidLinkDoesNotReplacePending() {
        val inbox = ExternalEntryInbox(); inbox.reconcile(1)
        inbox.receive("srisu://couples/1")
        val request = assertNotNull(inbox.pending.value)
        assertFalse(inbox.receive("srisu://accept-invite/1"))
        assertEquals(request, inbox.pending.value)
        assertNull(inbox.consume(request, 2))
    }
    @Test fun reopeningALinkLaterIsAllowedButPendingDuplicatesAreIgnored() {
        val inbox = ExternalEntryInbox(); inbox.reconcile(1)
        assertTrue(inbox.receive("srisu://couples/1"))
        assertFalse(inbox.receive("srisu://couples/1"))
        inbox.consume(assertNotNull(inbox.pending.value), 1)
        assertTrue(inbox.receive("srisu://couples/1"))
    }
    @Test fun replacedSessionClearsPendingEvenWhenAccountIdIsTheSame() {
        val inbox = ExternalEntryInbox(); inbox.reconcile(1, 1)
        inbox.receive("srisu://couples/1")
        inbox.reconcile(1, 3)
        assertNull(inbox.pending.value)
    }
}
