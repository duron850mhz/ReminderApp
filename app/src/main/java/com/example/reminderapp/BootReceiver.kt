package com.example.reminderapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.AlarmManager
import android.app.PendingIntent
import android.os.SystemClock

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("ReminderPrefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("isEnabled", false)) {
                val normalIntent = Intent(context, NotificationReceiver::class.java)
                val pendingIntent = PendingIntent.getBroadcast(context, 0, normalIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val interval = (prefs.getInt("intervalHours", 1) * 3600000L + prefs.getInt("intervalMinutes", 0) * 60000L)
                alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + interval, pendingIntent)
            }
        }
    }
}