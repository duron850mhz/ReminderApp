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

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("ReminderPrefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("isEnabled", false)) return

        val title = prefs.getString("title", "リマインダー") ?: "リマインダー"
        val message = prefs.getString("message", "確認してください") ?: "確認してください"

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

        // ログ追加（MainActivityが開いてない場合もログしたい場合、別途処理）
        Log.d("Reminder", "通知送信")

        // 再通知スケジュール (カウント1から)
        val mainActivity = MainActivity()  // インスタンスがないので、static的にスケジュール
        // 実際にはContextからスケジュール呼び出し
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val reIntent = Intent(context, ReNotificationReceiver::class.java).putExtra("count", 1)
        val pendingRe = PendingIntent.getBroadcast(context, 0, reIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val reDelay = prefs.getInt("reNotifyMinutes", 5) * 60000L
        alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + reDelay, pendingRe)
    }
}