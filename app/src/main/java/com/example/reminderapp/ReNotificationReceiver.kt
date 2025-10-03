package com.example.reminderapp

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class ReNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("ReminderPrefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("isEnabled", false)) return

        var count = intent.getIntExtra("count", 1)

        val title = prefs.getString("title", "リマインダー") ?: "リマインダー"
        val message = (prefs.getString("message", "確認してください") ?: "確認してください") + " (再通知($count'回目'))"

        val confirmIntent = PendingIntent.getBroadcast(context, 1, Intent("ACTION_CONFIRM"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, "reminder_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(android.R.drawable.ic_menu_save, "確認", confirmIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(1, notification)

        Log.d("Reminder", "再通知送信 ($count)")

        // 次再通知スケジュール (カウント+1)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val nextIntent = Intent(context, ReNotificationReceiver::class.java).putExtra("count", count + 1)
        val pendingNext = PendingIntent.getBroadcast(context, 0, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val reDelay = prefs.getInt("reNotifyMinutes", 5) * 60000L
        alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + reDelay, pendingNext)
    }
}