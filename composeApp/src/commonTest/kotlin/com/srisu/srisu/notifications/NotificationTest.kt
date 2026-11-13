package com.srisu.srisu.notifications

import com.srisu.srisu.features.notifications.*
import com.srisu.srisu.navigation.graph.ChatNav
import com.srisu.srisu.features.coupleprofile.navigation.*
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.srisu.srisu.core.data.remote.ApiJson
import kotlin.test.*

class NotificationTest {
    private val room="11111111-1111-4111-8111-111111111111"
    private val message="22222222-2222-4222-8222-222222222222"
    @AfterTest fun clear() { NativeNotifications.clear() }
    @Test fun preferenceSwitchSendsRequiredUnchangedMuteCommand() {
        val body=ApiJson.encodeToJsonElement(PreferenceWrite(2,false,true,true)).jsonObject
        assertEquals("unchanged",body.getValue("mute").jsonPrimitive.content)
        assertEquals(setOf("expected_revision","push","chat","activity","mute"),body.keys)
    }
    @Test fun strictTypedDestinationsRejectInventedAndIncompleteTargets() {
        assertEquals(ChatNav.PartnerRequests,NotificationDestination("invitations").route())
        assertEquals(ChatNav.CoupleConversation(room,message,"notification"),NotificationDestination("conversation",room,message).route())
        assertEquals(com.srisu.srisu.features.plans.navigation.PlansDestination(8),NotificationDestination("plan",coupleId=7,planId=8).route())
        assertNull(NotificationDestination("plan",coupleId=-1,planId=8).route())
        assertNull(NotificationDestination("conversation","https://example.com",message).route())
        assertNull(NotificationDestination("delete-account").route())
    }
    @Test fun payloadValidationExpiryTapAndForegroundDedupAreIndependent() {
        NativeNotifications.receive("1",message,"1",true)
        assertNull(NativeNotifications.signal.value)
        NativeNotifications.receive("2",message,"4102444800",true)
        assertNull(NativeNotifications.signal.value)
        NativeNotifications.receive("1",message,"4102444800",false)
        val first=NativeNotifications.signal.value!!
        NativeNotifications.consume(first)
        NativeNotifications.receive("1",message,"4102444800",false)
        assertNull(NativeNotifications.signal.value)
        NativeNotifications.receive("1",message,"4102444800",true)
        assertTrue(NativeNotifications.signal.value!!.tapped)
        NativeNotifications.consume(NativeNotifications.signal.value!!)
        NativeNotifications.receive("1",message,"4102444800",true)
        assertNull(NativeNotifications.signal.value)
    }
    @Test fun relevantConversationDoesNotShowBannerForAnotherAnchor() {
        assertTrue(notificationMatches(ChatNav.CoupleConversation(room,message),ChatNav.CoupleConversation(room)))
        assertFalse(notificationMatches(ChatNav.CoupleConversation(room),ChatNav.CoupleConversation(message)))
        assertFalse(notificationMatches(CoupleProfileDestination(1,CoupleSection.SHARING),CoupleProfileDestination(1)))
    }
    @Test fun matrixChatUsesTheAuthorizedDjangoRoomWithoutTreatingEventIdsAsLegacyMessages() {
        val destination = NotificationDestination("matrix_conversation", roomId=room.uppercase())
        assertEquals(ChatNav.CoupleConversation(room, origin="notification"), destination.route())
        assertTrue(notificationMatches(destination.route()!!, ChatNav.CoupleConversation(room)))
        assertNull(NotificationDestination("matrix_conversation", roomId="!private:matrix.test").route())
        assertNull(NotificationDestination("matrix_conversation", roomId=room, messageId="\$event").route())
        assertNull(NotificationDestination("matrix_conversation").route())
    }
    @Test fun androidChatCapabilityIsExplicitAndOlderClientsCanStillOmitIt() {
        val legacy = InstallationRequest(room, "s".repeat(48), 1, "android", "fid", "c".repeat(22), true)
        assertFalse("capabilities" in ApiJson.encodeToJsonElement(legacy).jsonObject)
        val android = legacy.copy(capabilities=listOf("matrix-chat-push-1"))
        assertEquals("[\"matrix-chat-push-1\"]", ApiJson.encodeToJsonElement(android).jsonObject["capabilities"].toString())
    }
    @Test fun leavingOldScreenCannotClearNewScreenVisibility() {
        val old=Any();val current=Any()
        try {
            NotificationVisibility.show(old,ChatNav.CoupleConversation(room))
            val plan=CoupleProfileDestination(1,CoupleSection.PLAN,2)
            NotificationVisibility.show(current,plan)
            NotificationVisibility.hide(old)
            assertEquals(plan,NotificationVisibility.current.value)
            NotificationVisibility.hide(current)
            assertNull(NotificationVisibility.current.value)
        } finally {NotificationVisibility.hide(old);NotificationVisibility.hide(current)}
    }
    @Test fun sparkDestinationsAreTypedAndInvalidIdsAreRejected() {
        assertEquals(com.srisu.srisu.features.sparks.navigation.SparksDestination(message), NotificationDestination("spark",sparkId=message).route())
        assertNull(NotificationDestination("spark",sparkId="https://example.com").route())
        assertTrue(notificationMatches(com.srisu.srisu.features.plans.navigation.PlansDestination(8),com.srisu.srisu.features.plans.navigation.PlansDestination(8,fromChat=true)))
    }
}
