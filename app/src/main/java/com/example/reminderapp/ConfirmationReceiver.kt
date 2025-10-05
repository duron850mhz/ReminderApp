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
import androidx.core.app.NotificationManagerCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Calendar

class ConfirmationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == "ACTION_CONFIRM") {
            Log.d("Reminder", "確認押下")
            addLog(context, "確認押下")

            // 通知をキャンセル（ID 1 と 2）
            NotificationManagerCompat.from(context).cancel(1) // 通常通知
            NotificationManagerCompat.from(context).cancel(2) // 再通知
            Log.d("Reminder", "通知をキャンセルしました (ID: 1, 2)")
            addLog(context, "通知をキャンセルしました (ID: 1, 2)")

            // アラームキャンセルと次通知スケジュール
            cancelAllAlarms(context)
            scheduleNextNotification(context, 0)
        }
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

    private fun cancelAllAlarms(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val normalIntent = PendingIntent.getBroadcast(
            context,
            100,
            Intent(context, NotificationReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val reIntent = PendingIntent.getBroadcast(
            context,
            101,
            Intent(context, ReNotificationReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(normalIntent)
        alarmManager.cancel(reIntent)
    }

    private fun scheduleNextNotification(context: Context, reNotifyCount: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val prefs = context.getSharedPreferences("ReminderPrefs", Context.MODE_PRIVATE)
        val nextTimeMillis = calculateNextNotificationTime(context, prefs, reNotifyCount)

        if (nextTimeMillis <= 0) {
            Log.d("Reminder", "通知スケジュールを設定できませんでした (時間帯外または無効な時間)")
            addLog(context, "通知スケジュールを設定できませんでした (時間帯外または無効な時間)")
            return
        }

        val intent = if (reNotifyCount == 0) {
            Intent(context, NotificationReceiver::class.java)
        } else {
            Intent(context, ReNotificationReceiver::class.java).putExtra("count", reNotifyCount)
        }

        val requestCode = if (reNotifyCount == 0) 100 else 101
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        Log.d("Reminder", if (reNotifyCount == 0) "次の通常通知をスケジュール" else "再通知をスケジュール")
        addLog(context, if (reNotifyCount == 0) "次の通常通知をスケジュール" else "再通知をスケジュール")
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + nextTimeMillis,
            pendingIntent
        )
    }

    private fun calculateNextNotificationTime(context: Context, prefs: SharedPreferences, reNotifyCount: Int): Long {
        val now = Calendar.getInstance()
        val intervalHours = prefs.getInt("intervalHours", 1)
        val intervalMinutes = prefs.getInt("intervalMinutes", 0)
        val reNotifyMinutes = prefs.getInt("reNotifyMinutes", 5)
        val intervalMillis = (intervalHours * 3600000L + intervalMinutes * 60000L)

        val delay = if (reNotifyCount > 0) reNotifyMinutes * 60000L else intervalMillis

        var nextCal = Calendar.getInstance().apply { timeInMillis = now.timeInMillis + delay }

        val startCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, prefs.getInt("startHour", 9))
            set(Calendar.MINUTE, prefs.getInt("startMinute", 0))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, prefs.getInt("endHour", 18))
            set(Calendar.MINUTE, prefs.getInt("endMinute", 0))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (!isWithinTimeBand(nextCal, prefs)) {
            if (nextCal.timeInMillis > endCal.timeInMillis) {
                nextCal = Calendar.getInstance().apply {
                    timeInMillis = startCal.timeInMillis
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            } else {
                nextCal = Calendar.getInstance().apply {
                    timeInMillis = startCal.timeInMillis
                }
            }
        }

        val nextTimeMillis = nextCal.timeInMillis - now.timeInMillis
        if (nextTimeMillis <= 0) {
            Log.d("Reminder", "エラー: 次の通知時間が過去または現在です: ${DateFormat.format("yyyy-MM-dd HH:mm:ss", nextCal.timeInMillis)}")
            addLog(context, "エラー: 次の通知時間が過去または現在です: ${DateFormat.format("yyyy-MM-dd HH:mm:ss", nextCal.timeInMillis)}")
            return -1
        }

        Log.d("Reminder", "次の通知時間: ${DateFormat.format("yyyy-MM-dd HH:mm:ss", nextCal.timeInMillis)}")
        addLog(context, "次の通知時間: ${DateFormat.format("yyyy-MM-dd HH:mm:ss", nextCal.timeInMillis)}")
        return nextTimeMillis
    }

    private fun isWithinTimeBand(cal: Calendar, prefs: SharedPreferences): Boolean {
        val startCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, prefs.getInt("startHour", 9))
            set(Calendar.MINUTE, prefs.getInt("startMinute", 0))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, prefs.getInt("endHour", 18))
            set(Calendar.MINUTE, prefs.getInt("endMinute", 0))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val time = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val start = startCal.get(Calendar.HOUR_OF_DAY) * 60 + startCal.get(Calendar.MINUTE)
        val end = endCal.get(Calendar.HOUR_OF_DAY) * 60 + endCal.get(Calendar.MINUTE)
        return if (start < end) time in start..end else time >= start || time <= end
    }
}