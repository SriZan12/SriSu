package com.srisu.srisu.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.work.*
import com.srisu.srisu.MainActivity
import com.srisu.srisu.R
import com.srisu.srisu.app.AndroidApplicationGraph
import com.srisu.srisu.core.lifecycle.ApplicationLifetime
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.features.notifications.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*
import okio.ByteString.Companion.encodeUtf8
import java.util.concurrent.TimeUnit

internal data class PreviewEnvelope(val delivery: String, val expires: Long, val group: String,
    val kind: String, val title: String, val body: String, val audible: Boolean,
    val installation: String, val version: Long, val created: Long) {
    fun data() = mapOf("schema" to "2", "delivery_id" to delivery, "expires_at" to expires.toString(),
        "group" to group, "kind" to kind, "title" to title, "body" to body, "audible" to audible.toString(),
        "installation_id" to installation, "binding_version" to version.toString(), "created_at" to created.toString())
    companion object {
        fun parse(data: Map<String, String>, now: Long = System.currentTimeMillis()): PreviewEnvelope? {
            val id = data["delivery_id"]?.takeIf(::validNotificationId) ?: return null
            val installation = data["installation_id"]?.takeIf(::validNotificationId) ?: return null
            val expires = data["expires_at"]?.toLongOrNull()?.takeIf { it > now / 1000 && it <= now / 1000 + 86460 } ?: return null
            val created = data["created_at"]?.toLongOrNull()?.takeIf { it > 0 && it <= now + 30_000 } ?: return null
            val version = data["binding_version"]?.toLongOrNull()?.takeIf { it > 0 } ?: return null
            val group = data["group"]?.takeIf { Regex("[0-9a-f]{32}").matches(it) } ?: return null
            val kind = data["kind"]?.takeIf { Regex("[a-z]+\\.[a-z_]+").matches(it) && it.length <= 32 } ?: return null
            if (data["schema"] != "2" || data["audible"] !in setOf("true", "false")) return null
            return PreviewEnvelope(id, expires, group, kind, bounded(data["title"].orEmpty(), 120),
                bounded(data["body"].orEmpty(), 240), data["audible"] == "true", installation, version, created)
        }
    }
}

internal fun bounded(value: String, size: Int): String = value.replace(Regex("[\\p{Cc}\\u202a-\\u202e\\u2066-\\u2069]"), " ")
    .replace(Regex("\\s+"), " ").trim()
    .let { if (it.length <= size) it else it.take(size - 1).trimEnd { c -> c.isHighSurrogate() } + "…" }

/** Bounded, content-free replay/order fence, retained across process death. */
internal class PreviewLedger(private val preferences: SharedPreferences) {
    fun accept(value: PreviewEnvelope, nowSeconds: Long): Boolean {
        if (preferences.contains("seen:${value.delivery}") ||
            preferences.getLong("time:${value.group}", 0) > value.created) return false
        val edit = preferences.edit()
        val seen = preferences.all.filterKeys { it.startsWith("seen:") }.toList().sortedBy { it.second as? Long ?: 0 }
        seen.filterIndexed { index, item -> (item.second as? Long ?: 0) <= nowSeconds || index < seen.size - 255 }
            .forEach { edit.remove(it.first) }
        val groups = preferences.all.filterKeys { it.startsWith("expiry:") }.toList().sortedBy { it.second as? Long ?: 0 }
        groups.filterIndexed { index, item -> (item.second as? Long ?: 0) <= nowSeconds || index < groups.size - 127 }
            .forEach { (key, _) ->
                val group = key.removePrefix("expiry:")
                edit.remove(key).remove("time:$group").remove("current:$group")
            }
        edit.putLong("seen:${value.delivery}", value.expires).putLong("time:${value.group}", value.created)
            .putLong("expiry:${value.group}", value.expires).putString("current:${value.group}", value.delivery).apply()
        return true
    }
    fun current(value: PreviewEnvelope) = preferences.getString("current:${value.group}", null) == value.delivery
}

internal object AndroidNotificationPreviews {
    private const val WORK = "srisu-notification-previews"
    private const val EXTRA = "srisu_delivery"
    private val lock = Any()
    private fun store(context: Context) = context.getSharedPreferences("notification_rendering_v1", Context.MODE_PRIVATE)
    private fun sessions() = AndroidApplicationGraph.koin.get<SessionCoordinator>()
    private fun sid(): String? = runCatching {
        val payload = sessions().accessToken()?.split('.')?.getOrNull(1) ?: return null
        Json.parseToJsonElement(android.util.Base64.decode(payload, android.util.Base64.URL_SAFE).decodeToString())
            .jsonObject["sid"]?.jsonPrimitive?.content
    }.getOrNull()
    fun owner(): String? = sid()?.let { "${sessions().stamp().accountId}:$it".encodeUtf8().sha256().hex() }
    fun owns(envelope: PreviewEnvelope, expectedOwner: String? = owner()): Boolean =
        expectedOwner != null && expectedOwner == owner() && sessions().stamp().accountId != null &&
            sid()?.let { AndroidNotifications.instance?.ownsPreview(envelope.installation, envelope.version, it) } == true &&
            envelope.expires > System.currentTimeMillis() / 1000

    fun receive(context: Context, data: Map<String, String>, highPriority: Boolean) {
        val envelope = PreviewEnvelope.parse(data) ?: return
        AndroidNotifications.install(context)
        val owner = owner() ?: return
        if (!owns(envelope, owner) || !NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        synchronized(lock) {
            if (!PreviewLedger(store(context)).accept(envelope, System.currentTimeMillis() / 1000)) return
            if (AndroidApplicationGraph.koin.get<ApplicationLifetime>().foreground.value) {
                Handler(Looper.getMainLooper()).post { NativeNotifications.receive("2", envelope.delivery, envelope.expires.toString(), false) }
                return
            }
            post(context, envelope, null)
        }
        // Only routing metadata and safe fallback copy are persisted by WorkManager.
        val input = Data.Builder().apply { envelope.data().forEach { (key, value) -> putString(key, value) }; putString("owner", owner) }.build()
        val request = OneTimeWorkRequestBuilder<NotificationPreviewWorker>().setInputData(input)
            .addTag(WORK).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
        if (highPriority && Build.VERSION.SDK_INT >= 31) request.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        WorkManager.getInstance(context).enqueueUniqueWork("preview:${envelope.delivery}", ExistingWorkPolicy.KEEP, request.build())
    }

    fun current(context: Context, value: PreviewEnvelope): Boolean =
        PreviewLedger(store(context)).current(value) &&
            context.getSystemService(NotificationManager::class.java).activeNotifications.any {
                it.tag == value.group && it.notification.extras.getString(EXTRA) == value.delivery
            }

    fun post(context: Context, value: PreviewEnvelope, preview: NotificationPreview?) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            data = Uri.parse("srisu-notification://${value.delivery}")
            putExtra("schema", "2"); putExtra("delivery_id", value.delivery); putExtra("expires_at", value.expires.toString())
        }
        val tap = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val channel = if (value.audible) "srisu_updates" else "srisu_updates_quiet"
        val public = NotificationCompat.Builder(context, channel).setSmallIcon(R.drawable.partner_heart)
            .setContentTitle(value.title).setContentText(value.body).build()
        val title = bounded(preview?.title ?: value.title, 120)
        val body = bounded(preview?.body ?: value.body, 240)
        val notification = NotificationCompat.Builder(context, channel).setSmallIcon(R.drawable.partner_heart)
            .setContentTitle(title).setContentText(body).setContentIntent(tap).setAutoCancel(true)
            .setOnlyAlertOnce(preview != null).setWhen(value.created).setShowWhen(true)
            .setTimeoutAfter((value.expires * 1000 - System.currentTimeMillis()).coerceAtLeast(1))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(public)
            .addExtras(Bundle().apply { putString(EXTRA, value.delivery) })
            .setCategory(if (value.kind == "matrix.message") NotificationCompat.CATEGORY_MESSAGE else NotificationCompat.CATEGORY_EVENT)
        if (preview != null && value.kind == "matrix.message") {
            notification.setStyle(NotificationCompat.MessagingStyle(Person.Builder().setName("You").build())
                .addMessage(body, value.created, Person.Builder().setName(title).build()))
        } else notification.setStyle(NotificationCompat.BigTextStyle().bigText(body))
        context.getSystemService(NotificationManager::class.java).notify(value.group, 0, notification.build())
    }

    fun cancel(context: Context, value: PreviewEnvelope) = synchronized(lock) {
        if (current(context, value)) context.getSystemService(NotificationManager::class.java).cancel(value.group, 0)
    }
    fun update(context: Context, value: PreviewEnvelope, owner: String, preview: NotificationPreview) = synchronized(lock) {
        if (owns(value, owner) && current(context, value)) post(context, value, preview)
    }
    fun clear(context: Context) = synchronized(lock) {
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK)
        store(context).edit().clear().apply()
    }
}

class NotificationPreviewWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val envelope = PreviewEnvelope.parse(inputData.keyValueMap.mapNotNull { (key, value) -> (value as? String)?.let { key to it } }.toMap())
            ?: return Result.success()
        val owner = inputData.getString("owner") ?: return Result.success()
        return try {
            AndroidNotifications.install(applicationContext)
            if (!AndroidNotificationPreviews.owns(envelope, owner) || !AndroidNotificationPreviews.current(applicationContext, envelope))
                return Result.success()
            val result = withTimeoutOrNull(26_000) { withContext(Dispatchers.Main.immediate) {
                AndroidApplicationGraph.koin.get<NotificationPreviewReader>().load(envelope.delivery)
            } }
            when (result) {
                is PreviewResult.Ready -> {
                    AndroidNotificationPreviews.update(applicationContext, envelope, owner, result.value)
                    if (!result.complete && runAttemptCount < 2) Result.retry() else Result.success()
                }
                PreviewResult.Unavailable -> { AndroidNotificationPreviews.cancel(applicationContext, envelope); Result.success() }
                else -> if (runAttemptCount < 2) Result.retry() else Result.success()
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { if (runAttemptCount < 2) Result.retry() else Result.success() }
    }
}
