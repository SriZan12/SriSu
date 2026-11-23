package com.srisu.srisu.features.plans

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.srisu.srisu.features.plans.data.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[32])
class PlanDeviceTest {
    private val copy = object : PlanPreviewStore {
        val values = mutableMapOf<String,String>()
        override fun read(id: String) = values[id]
        override fun write(id: String, value: String) { values[id] = value }
        override fun remove(id: String) { values.remove(id) }
    }
    @Test fun replacingAndClearingSchedulesKeepsOnlyCurrentAlarms() {
        val context=ApplicationProvider.getApplicationContext<Context>();val device=AndroidPlanDevice(context,copy)
        device.permission {assertEquals("granted",it)}
        val manager=shadowOf(context.getSystemService(AlarmManager::class.java))
        val alarm=PlanAlarm("plan-7",System.currentTimeMillis()+60_000,"srisu://plans/3/7")
        device.replaceReminders(listOf(alarm)) {assertNull(it)};assertEquals(1,manager.scheduledAlarms.size)
        device.replaceReminders(listOf(alarm)) {assertNull(it)};assertEquals(1,manager.scheduledAlarms.size)
        device.replaceReminders(listOf(alarm.copy(atMillis=alarm.atMillis+60_000))) {assertNull(it)}
        assertEquals(1,manager.scheduledAlarms.size)
        device.replaceReminders(emptyList()) {assertNull(it)};assertTrue(manager.scheduledAlarms.isEmpty())
        PlanAlarmReceiver().onReceive(context,Intent().setData(Uri.parse("srisu-alarm://plan-7")))
        assertTrue(context.getSystemService(NotificationManager::class.java).activeNotifications.isEmpty())
    }
    @Test fun aRoutineSyncDoesNotRemoveAnAlreadyDeliveredReminder() {
        val context=ApplicationProvider.getApplicationContext<Context>();val device=AndroidPlanDevice(context,copy)
        device.permission {}
        val alarm=PlanAlarm("plan-8",System.currentTimeMillis()-1_000,"srisu://plans/3/8","Coming up: Synthetic picnic","18:00 · Synthetic garden")
        device.replaceReminders(listOf(alarm)) {assertNull(it)}
        PlanAlarmReceiver { copy }.onReceive(context,Intent().setData(Uri.parse("srisu-alarm://plan-8")))
        val notifications=context.getSystemService(NotificationManager::class.java)
        assertEquals(1,notifications.activeNotifications.size)
        val shown=notifications.activeNotifications.single().notification
        assertEquals(alarm.title,shown.extras.getString(android.app.Notification.EXTRA_TITLE))
        assertEquals(alarm.body,shown.extras.getString(android.app.Notification.EXTRA_TEXT))
        assertFalse(shown.publicVersion.extras.toString().contains("Synthetic"))
        assertFalse(context.getSharedPreferences("plan-alarms",Context.MODE_PRIVATE).all.toString().contains("Synthetic"))
        device.replaceReminders(listOf(alarm)) {assertNull(it)};assertEquals(1,notifications.activeNotifications.size)
        device.replaceReminders(emptyList()) {assertNull(it)};assertTrue(notifications.activeNotifications.isEmpty())
        assertNull(copy.read(alarm.id))
    }
}
