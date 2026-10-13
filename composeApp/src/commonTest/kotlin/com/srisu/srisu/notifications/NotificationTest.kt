package com.srisu.srisu.notifications

import com.srisu.srisu.features.notifications.*
import com.srisu.srisu.navigation.graph.ChatNav
import com.srisu.srisu.features.coupleprofile.navigation.*
import com.srisu.srisu.features.couplechat.data.ChatEnvelope
import kotlinx.serialization.json.Json
import kotlin.test.*

class NotificationTest {
    private val room="11111111-1111-4111-8111-111111111111"
    private val message="22222222-2222-4222-8222-222222222222"
    @AfterTest fun clear() { NativeNotifications.clear() }
    @Test fun strictTypedDestinationsRejectInventedAndIncompleteTargets() {
        assertEquals(ChatNav.PartnerRequests,NotificationDestination("invitations").route())
        assertEquals(ChatNav.CoupleConversation(room,message,"notification"),NotificationDestination("conversation",room,message).route())
        assertEquals(CoupleProfileDestination(7,CoupleSection.PLAN,8),NotificationDestination("plan",coupleId=7,planId=8).route())
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
    @Test fun legacyServerDoesNotReceiveUnnegotiatedPlanField() {
        val json=Json {encodeDefaults=true;explicitNulls=true}
        val wire=ChatEnvelope(deviceId=room,recipientDeviceId=message,operationId=message,kind="message",targetId=message,signalType=3,ciphertext="synthetic")
        assertFalse(json.encodeToString(wire).contains("notification_plan_id"))
        assertTrue(json.encodeToString(wire.copy(notificationPlanId=12)).contains("\"notification_plan_id\":12"))
    }
}
