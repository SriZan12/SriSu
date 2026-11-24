package com.srisu.srisu.notifications

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.srisu.srisu.features.notifications.NotificationPreview
import kotlin.test.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AndroidNotificationPreviewTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    private fun envelope() = PreviewEnvelope("11111111-1111-4111-8111-111111111111", System.currentTimeMillis()/1000+300,
        "a".repeat(32), "matrix.message", "New message", "Open your chat to read the message.", true,
        "22222222-2222-4222-8222-222222222222", 2, System.currentTimeMillis())
    private fun prefs() = context.getSharedPreferences("notification_rendering_v1", Context.MODE_PRIVATE)

    @BeforeTest fun setUp() {
        prefs().edit().clear().commit()
        manager.createNotificationChannel(NotificationChannel("srisu_updates", "Updates", NotificationManager.IMPORTANCE_DEFAULT))
        manager.createNotificationChannel(NotificationChannel("srisu_updates_quiet", "Quiet", NotificationManager.IMPORTANCE_LOW).apply { setSound(null, null) })
    }
    @AfterTest fun clear() { manager.cancelAll(); prefs().edit().clear().commit() }

    @Test fun malformedExpiredAndFuturePayloadsFailClosed() {
        val data = envelope().data()
        assertNotNull(PreviewEnvelope.parse(data))
        for ((key, value) in listOf("schema" to "3", "delivery_id" to "../../file", "installation_id" to "invalid",
            "binding_version" to "0", "expires_at" to "1", "created_at" to "999999999999999", "group" to "raw-room-id",
            "audible" to "yes", "kind" to "unexpected/url")) {
            assertNull(PreviewEnvelope.parse(data + (key to value)), key)
        }
    }

    @Test fun duplicatesOlderEventsAndDismissalsCannotResurrectPreview() {
        val value = envelope()
        val ledger = PreviewLedger(prefs())
        assertTrue(ledger.accept(value, System.currentTimeMillis()/1000))
        assertFalse(ledger.accept(value, System.currentTimeMillis()/1000))
        assertFalse(ledger.accept(value.copy(delivery = "33333333-3333-4333-8333-333333333333", created = value.created-1), System.currentTimeMillis()/1000))
        AndroidNotificationPreviews.post(context, value, null)
        assertTrue(AndroidNotificationPreviews.current(context, value))
        manager.cancel(value.group, 0)
        assertFalse(AndroidNotificationPreviews.current(context, value))
        val newer = value.copy(delivery = "44444444-4444-4444-8444-444444444444", created = value.created+1)
        assertTrue(ledger.accept(newer, System.currentTimeMillis()/1000))
        AndroidNotificationPreviews.post(context, newer, null)
        AndroidNotificationPreviews.cancel(context, value)
        assertTrue(AndroidNotificationPreviews.current(context, newer))
        assertFalse(ledger.current(value))
    }

    @Test fun contentUpdateIsSilentPrivateAndKeepsTheExactTapWhileNewMessagesCanRing() {
        val value = envelope()
        AndroidNotificationPreviews.post(context, value, null)
        assertEquals(0, manager.activeNotifications.single().notification.flags and Notification.FLAG_ONLY_ALERT_ONCE)
        AndroidNotificationPreviews.post(context, value, NotificationPreview("matrix.message", "Synthetic partner", "Synthetic picnic at six?"))
        val notification = manager.activeNotifications.single().notification
        assertEquals(Notification.FLAG_ONLY_ALERT_ONCE, notification.flags and Notification.FLAG_ONLY_ALERT_ONCE)
        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
        assertEquals("New message", notification.publicVersion.extras.getString(Notification.EXTRA_TITLE))
        assertFalse(notification.publicVersion.extras.toString().contains("Synthetic"))
        val intent = shadowOf(notification.contentIntent).savedIntent
        assertEquals(value.delivery, intent.getStringExtra("delivery_id"))
        assertEquals("2", intent.getStringExtra("schema"))
        assertEquals("srisu_updates", notification.channelId)
        val style = androidx.core.app.NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)!!
        assertEquals("Synthetic picnic at six?", style.messages.single().text.toString())
        AndroidNotificationPreviews.post(context, value.copy(audible = false), null)
        assertEquals("srisu_updates_quiet", manager.activeNotifications.single().notification.channelId)
    }

    @Test fun replayMetadataIsBoundedAndContainsNoPrivatePreview() {
        val now = System.currentTimeMillis()/1000
        val ledger = PreviewLedger(prefs())
        repeat(300) { index ->
            val value = envelope().copy(delivery = java.util.UUID.randomUUID().toString(), group = index.toString(16).padStart(32, '0'))
            ledger.accept(value, now)
        }
        assertTrue(prefs().all.keys.count { it.startsWith("seen:") } <= 256)
        assertTrue(prefs().all.keys.count { it.startsWith("current:") } <= 128)
        assertTrue(prefs().all.keys.all { it.substringBefore(':') in setOf("seen", "current", "time", "expiry") })
    }

    @Test fun momentMediaCopyIsDisplayedWithoutChangingSafePublicCopyOrTapDestination() {
        val value=envelope().copy(kind="moment.published",title="New moment",
            body="Your partner shared a new moment.",audible=false)
        for((description,body) in listOf("video moment" to "Video moment",
            "photo moment" to "Photo moment", "moment with photos and videos" to "Photo and video moment",
            "video moment" to "Synthetic sunset together")) {
            val title="Synthetic partner shared a $description"
            AndroidNotificationPreviews.post(context,value,NotificationPreview("moment.published",title,body))
            val notification=manager.activeNotifications.single().notification
            assertEquals(title,notification.extras.getString(Notification.EXTRA_TITLE))
            assertEquals(body,notification.extras.getString(Notification.EXTRA_TEXT))
            assertEquals(body,notification.extras.getString(Notification.EXTRA_BIG_TEXT))
            assertEquals(Notification.VISIBILITY_PRIVATE,notification.visibility)
            assertEquals(value.body,notification.publicVersion.extras.getString(Notification.EXTRA_TEXT))
            assertEquals(value.delivery,shadowOf(notification.contentIntent).savedIntent.getStringExtra("delivery_id"))
            assertEquals(Notification.FLAG_ONLY_ALERT_ONCE,notification.flags and Notification.FLAG_ONLY_ALERT_ONCE)
        }
    }
}
