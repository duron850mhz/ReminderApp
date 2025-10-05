package com.example.reminderapp

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.SystemClock
import android.text.format.DateFormat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Calendar

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("ReminderPrefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("isEnabled", false)) return

        val title = prefs.getString("title", "リマインダー") ?: "リマインダー"
        val message = prefs.getString("message", "確認してください") ?: "確認してください"

        val confirmIntent = Intent(context, ConfirmationReceiver::class.java).setAction("ACTION_CONFIRM")
        val pendingConfirm = PendingIntent.getBroadcast(context, 1, confirmIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context, "reminder_channel")
            .setSmallIcon(R.drawable.ic_stat_name) // 通知アイコンを指定
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(android.R.drawable.ic_menu_save, "確認", pendingConfirm)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(1, notification)

        Log.d("Reminder", "通知送信: title=$title, message=$message")
        addLog(context, "通知送信: title=$title, message=$message")

        // 再通知スケジュール (カウント1から)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val reIntent = Intent(context, ReNotificationReceiver::class.java).putExtra("count", 1)
        val pendingRe = PendingIntent.getBroadcast(context, 101, reIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val reDelay = prefs.getInt("reNotifyMinutes", 5) * 60000L
        alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + reDelay, pendingRe)
        Log.d("Reminder", "再通知をスケジュール")
        addLog(context, "再通知をスケジュール")
    }

    private fun addLog(context: Context, message: String) {
        val prefs = context.getSharedPreferences("ReminderPrefs", Context.MODE_PRIVATE)
        val logsJson = prefs.getString("logs", null)
        val gson = Gson()
        val logs: MutableList<String> = if (logsJson != null) {
            gson.fromJson(logsJson, object : TypeToken<MutableList<String>>() {}.type)
        } else {
            mutableListOf()
        }
        val timestamp = DateFormat.format("yyyy-MM-dd HH:mm:ss", Calendar.getInstance()).toString()
        logs.add("$timestamp: $message")
        prefs.edit().putString("logs", gson.toJson(logs)).apply()
    }
}