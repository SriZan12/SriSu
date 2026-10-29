package com.srisu.srisu.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.liftric.kvault.KVault
import com.srisu.srisu.features.notifications.*
import java.security.SecureRandom
import java.util.UUID

class AndroidNotifications(private val context: Context) : NotificationPlatform {
    private val vault by lazy { KVault(context=context, fileName="srisu_notifications_v1") }
    private var fid: String? = null
    private var registering = false
    private var unregistering = false
    private var activeBinding: String? = null
    var requestRuntimePermission: (() -> Unit)? = null
    private val configured get() = FirebaseApp.getApps(context).isNotEmpty()
    init {
        FirebaseApp.initializeApp(context)
        if(Build.VERSION.SDK_INT >= 26) {
            val manager=context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel("srisu_updates","SriSu updates",NotificationManager.IMPORTANCE_DEFAULT).apply {
                description="Private alerts for messages and shared activity";lockscreenVisibility=android.app.Notification.VISIBILITY_PRIVATE
            })
            manager.createNotificationChannel(NotificationChannel("srisu_updates_quiet","SriSu updates · quiet",NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null,null);lockscreenVisibility=android.app.Notification.VISIBILITY_PRIVATE
            })
        }
        if(configured) {
            FirebaseCrashlytics.getInstance().setCustomKey("environment",CrashReporter.environment)
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(vault.string("diagnostics")=="yes")
            if(vault.string("diagnostics")!="yes") FirebaseCrashlytics.getInstance().deleteUnsentReports()
        }
    }
    private fun permitted() = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)
    override fun refresh() {
        publish()
        if(configured && activeBinding != null && permitted() && fid==null && !registering && !unregistering) {
            registering=true
            FirebaseMessaging.getInstance().isAutoInitEnabled=true
            FirebaseMessaging.getInstance().register().addOnCompleteListener { registering=false }
        }
    }
    fun registered(value:String) { if(activeBinding != null && !unregistering) { fid=value;publish() } }
    private fun publish() {
        if(!configured) { NativeNotifications.update(false,"unavailable",false,true);return }
        val permission=when {permitted()->"granted";vault.string("asked")=="yes"->"denied";else->"not_determined"}
        NativeNotifications.update(configured,permission,vault.string("diagnostics")=="yes",vault.string("dismissed")=="yes")
    }
    override fun requestPermission() {
        if(!configured) return
        vault.set("asked","yes")
        if(Build.VERSION.SDK_INT >= 33) requestRuntimePermission?.invoke() else refresh()
    }
    override fun dismissPrompt() { vault.set("dismissed","yes");publish() }
    override fun openSettings() { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    override fun registration(binding:String): InstallationRequest? {
        if(activeBinding != binding) {activeBinding=binding;refresh()}
        val target=fid ?: return null
        val id=vault.string("id") ?: UUID.randomUUID().toString().also { check(vault.set("id",it)) }
        val proof=vault.string("proof") ?: android.util.Base64.encodeToString(ByteArray(32).also {SecureRandom().nextBytes(it)},android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP).also {check(vault.set("proof",it))}
        val identity="$binding:$target:${permitted()}"
        var version=vault.string("version")?.toLongOrNull() ?: 0L
        if(vault.string("binding")!=identity) {
            version++
            check(vault.set("version",version.toString()))
            check(vault.set("binding",identity))
        }
        return InstallationRequest(id,proof,version,"android","fid",target,permitted())
    }
    override fun renewRegistration() {check(vault.set("binding",""));publish()}
    override fun clearAccount() {
        context.getSystemService(NotificationManager::class.java).cancelAll()
        activeBinding=null;fid=null
        if(configured) {
            FirebaseMessaging.getInstance().isAutoInitEnabled=false
            unregistering=true
            FirebaseMessaging.getInstance().unregister().addOnCompleteListener { unregistering=false;refresh() }
        }
        setDiagnostics(false)
    }
    override fun setDiagnostics(enabled:Boolean) {
        if(!configured) return
        val crash=FirebaseCrashlytics.getInstance()
        crash.setCrashlyticsCollectionEnabled(false)
        crash.deleteUnsentReports()
        crash.setUserId("")
        check(vault.set("diagnostics",if(enabled) "yes" else "no"))
        crash.setCrashlyticsCollectionEnabled(enabled)
        publish()
    }
    override fun report(code:DiagnosticCode) {
        if(configured && vault.string("diagnostics")=="yes") {
            // Deliberately replace the error, including its message, causes and arbitrary frames.
            val safe=IllegalStateException("SriSu diagnostic: ${code.name}")
            safe.stackTrace=safe.stackTrace.filter {it.className.startsWith("com.srisu.srisu.")}.toTypedArray()
            FirebaseCrashlytics.getInstance().recordException(safe)
        }
    }
    companion object {
        var instance: AndroidNotifications? = null
        fun install(context:Context) {
            if(instance==null) {
                instance=runCatching {AndroidNotifications(context)}.getOrNull()
                instance?.let {NativeNotifications.install(it)} ?: NativeNotifications.update(false,"unavailable",false,true)
            }
        }
        fun receive(intent:Intent?) {
            NativeNotifications.receive(intent?.getStringExtra("schema"),intent?.getStringExtra("delivery_id"),intent?.getStringExtra("expires_at"),true)
        }
    }
}

class SriSuMessagingService : FirebaseMessagingService() {
    override fun onRegistered(installationId:String) {
        Handler(Looper.getMainLooper()).post {AndroidNotifications.install(applicationContext);AndroidNotifications.instance?.registered(installationId)}
    }
    override fun onMessageReceived(message:RemoteMessage) {
        val data=message.data
        Handler(Looper.getMainLooper()).post {
            NativeNotifications.receive(data["schema"],data["delivery_id"],data["expires_at"],false)
        }
    }
}
