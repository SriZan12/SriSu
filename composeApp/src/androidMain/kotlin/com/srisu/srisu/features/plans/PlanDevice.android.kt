package com.srisu.srisu.features.plans.data

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.srisu.srisu.MainActivity
import org.json.JSONArray
import org.json.JSONObject

object PlanPermissionHost { var request: (((String)->Unit)->Unit)? = null }
private const val CHANNEL = "srisu-plans"
private fun preferences(c: Context) = c.getSharedPreferences("plan-alarms",Context.MODE_PRIVATE)
private fun alarmIntent(c: Context, id: String): PendingIntent = PendingIntent.getBroadcast(c,0,
    Intent(c,PlanAlarmReceiver::class.java).setData(Uri.parse("srisu-alarm://$id")),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
private fun allowed(c: Context): Boolean = NotificationManagerCompat.from(c).areNotificationsEnabled() &&
    (Build.VERSION.SDK_INT<33 || ContextCompat.checkSelfPermission(c,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED) &&
    (Build.VERSION.SDK_INT<26 || c.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE)
private fun channel(c: Context) {
    if(Build.VERSION.SDK_INT>=26) c.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,"Plan reminders",NotificationManager.IMPORTANCE_DEFAULT))
}
class AndroidPlanDevice(private val context: Context): PlanDevice {
    override fun permission(result:(String)->Unit) { channel(context); result(if(allowed(context)) "granted" else "denied") }
    override fun requestPermission(result:(String)->Unit) {
        channel(context)
        if(allowed(context)) result("granted") else PlanPermissionHost.request?.invoke { permission(result) } ?: result("denied")
    }
    override fun replaceReminders(alarms: List<PlanAlarm>, result:(String?)->Unit) {
        try {
            val prefs=preferences(context); val manager=context.getSystemService(AlarmManager::class.java)
            val old=JSONArray(prefs.getString("alarms","[]"))
            for(i in 0 until old.length()) {
                val row=old.getJSONObject(i);val id=row.getString("id")
                if(alarms.none {it.id==id && it.atMillis==row.getLong("at") && it.url==row.getString("url")}) {
                    manager.cancel(alarmIntent(context,id));NotificationManagerCompat.from(context).cancel(id.hashCode())
                }
            }
            val encoded=JSONArray()
            for(alarm in alarms) encoded.put(JSONObject().put("id",alarm.id).put("at",alarm.atMillis).put("url",alarm.url))
            // Save before scheduling; a receiver never displays an alarm removed by logout/cancellation.
            prefs.edit().putString("alarms",encoded.toString()).commit()
            for(alarm in alarms) {
                val unchanged=(0 until old.length()).any {val row=old.getJSONObject(it);row.getString("id")==alarm.id && row.getLong("at")==alarm.atMillis && row.getString("url")==alarm.url}
                if(!unchanged && alarm.atMillis>System.currentTimeMillis()) manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,alarm.atMillis,alarmIntent(context,alarm.id))
            }
            result(null)
        } catch(_:Exception) { result("This phone couldn’t schedule reminders. Try again.") }
    }
    override fun calendar(title:String,atMillis:Long,place:String,result:(String)->Unit) {
        try { context.startActivity(Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE,title).putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME,atMillis)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME,atMillis+3_600_000L).putExtra(CalendarContract.Events.EVENT_LOCATION,place)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); result("Calendar opened. Save the copy there; later plan changes won’t update it.")
        } catch(_:ActivityNotFoundException) { result("No calendar app is available on this phone.") }
    }
    override fun openPlace(place:String) {
        val url=if(place.startsWith("https://")) place else "geo:0,0?q=${Uri.encode(place)}"
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
private fun scheduleSaved(context:Context) {
    val list=JSONArray(preferences(context).getString("alarms","[]")); val manager=context.getSystemService(AlarmManager::class.java)
    for(i in 0 until list.length()) { val row=list.getJSONObject(i); val at=row.getLong("at")
        if(at>System.currentTimeMillis()) manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,alarmIntent(context,row.getString("id")))
    }
}
class PlanAlarmReceiver: BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        if(intent.action==Intent.ACTION_BOOT_COMPLETED || intent.action==Intent.ACTION_MY_PACKAGE_REPLACED) { runCatching { scheduleSaved(context) }; return }
        val id=intent.data?.host ?: return
        val list=JSONArray(preferences(context).getString("alarms","[]"))
        val row=(0 until list.length()).map { list.getJSONObject(it) }.firstOrNull { it.getString("id")==id } ?: return
        // A stale broadcast cannot display a rescheduled event early or hours late.
        val age=System.currentTimeMillis()-row.getLong("at")
        if(age !in 0..3_600_000 || !allowed(context)) return
        val open=PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java).setData(Uri.parse(row.getString("url"))),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        runCatching { NotificationManagerCompat.from(context).notify(id.hashCode(),NotificationCompat.Builder(context,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar).setContentTitle("A plan together")
            .setContentText("Open SriSu to check the latest details.").setContentIntent(open).setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()) }
    }
}
