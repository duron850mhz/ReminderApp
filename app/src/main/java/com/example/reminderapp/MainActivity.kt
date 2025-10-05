package com.example.reminderapp

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.format.DateFormat
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.Manifest
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Calendar
import java.util.Locale
import com.example.reminderapp.R

class MainActivity : AppCompatActivity() {

    private lateinit var switchToggle: Switch
    private lateinit var etIntervalHours: EditText
    private lateinit var etIntervalMinutes: EditText
    private lateinit var etReNotifyMinutes: EditText
    private lateinit var tvStartTime: TextView
    private lateinit var tvEndTime: TextView
    private lateinit var etTitle: EditText
    private lateinit var etMessage: EditText
    private lateinit var lvLogs: ListView
    private lateinit var btnSave: Button

    private lateinit var prefs: SharedPreferences
    private val gson = Gson()
    private var logs: MutableList<String> = mutableListOf()

    private var startHour = 9
    private var startMinute = 0
    private var endHour = 18
    private var endMinute = 0

    // 通知権限リクエスト用のランチャー
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            addLog("通知権限が許可されました")
        } else {
            addLog("通知権限が拒否されました")
            Toast.makeText(this, "通知を有効にするには権限が必要です", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 通知権限のリクエスト
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        switchToggle = findViewById(R.id.switch_toggle)
        etIntervalHours = findViewById(R.id.et_interval_hours)
        etIntervalMinutes = findViewById(R.id.et_interval_minutes)
        etReNotifyMinutes = findViewById(R.id.et_renotify_minutes)
        tvStartTime = findViewById(R.id.tv_start_time)
        tvEndTime = findViewById(R.id.tv_end_time)
        etTitle = findViewById(R.id.et_title)
        etMessage = findViewById(R.id.et_message)
        lvLogs = findViewById(R.id.lv_logs)
        btnSave = findViewById(R.id.btn_save)

        prefs = getSharedPreferences("ReminderPrefs", MODE_PRIVATE)
        loadSettings()
        loadLogs()

        // SharedPreferences の変更を監視
        prefs.registerOnSharedPreferenceChangeListener { _, key ->
            if (key == "logs") {
                loadLogs()
            }
        }

        createNotificationChannel()

        switchToggle.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("isEnabled", isChecked).apply()
            addLog(if (isChecked) "動作ON" else "動作OFF")
            if (isChecked) {
                scheduleNextNotification(0)  // カウント0で通常通知
            } else {
                cancelAllAlarms()
            }
        }

        tvStartTime.setOnClickListener { showTimePicker(true) }
        tvEndTime.setOnClickListener { showTimePicker(false) }

        btnSave.setOnClickListener {
            saveSettings()
            Toast.makeText(this, "設定を保存しました", Toast.LENGTH_SHORT).show()
            if (switchToggle.isChecked) {
                scheduleNextNotification(0)
            }
        }
    }

    private fun loadSettings() {
        startHour = prefs.getInt("startHour", 9)
        startMinute = prefs.getInt("startMinute", 0)
        endHour = prefs.getInt("endHour", 18)
        endMinute = prefs.getInt("endMinute", 0)
        etIntervalHours.setText(prefs.getInt("intervalHours", 1).toString())
        etIntervalMinutes.setText(prefs.getInt("intervalMinutes", 0).toString())
        etReNotifyMinutes.setText(prefs.getInt("reNotifyMinutes", 5).toString())
        etTitle.setText(prefs.getString("title", "リマインダー"))
        etMessage.setText(prefs.getString("message", "確認してください"))
        tvStartTime.text = String.format(Locale.getDefault(), "%02d:%02d", startHour, startMinute)
        tvEndTime.text = String.format(Locale.getDefault(), "%02d:%02d", endHour, endMinute)
        switchToggle.isChecked = prefs.getBoolean("isEnabled", false)
    }

    private fun saveSettings() {
        val intervalHours = etIntervalHours.text.toString().toIntOrNull() ?: 1
        val intervalMinutes = etIntervalMinutes.text.toString().toIntOrNull() ?: 0
        val reNotifyMinutes = etReNotifyMinutes.text.toString().toIntOrNull() ?: 5
        prefs.edit().apply {
            putInt("intervalHours", intervalHours)
            putInt("intervalMinutes", intervalMinutes)
            putInt("reNotifyMinutes", reNotifyMinutes)
            putString("title", etTitle.text.toString())
            putString("message", etMessage.text.toString())
            putInt("startHour", startHour)
            putInt("startMinute", startMinute)
            putInt("endHour", endHour)
            putInt("endMinute", endMinute)
            apply()
        }
    }

    private fun addLog(message: String) {
        val timestamp = DateFormat.format("yyyy-MM-dd HH:mm:ss", Calendar.getInstance()).toString()
        logs.add("$timestamp: $message")
        prefs.edit().putString("logs", gson.toJson(logs)).apply()
        lvLogs.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, logs.reversed())
    }

    private fun loadLogs() {
        val logsJson = prefs.getString("logs", null)
        logs = if (logsJson != null) {
            gson.fromJson(logsJson, object : TypeToken<MutableList<String>>() {}.type)
        } else {
            mutableListOf()
        }
        lvLogs.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, logs.reversed())
    }

    private fun showTimePicker(isStart: Boolean) {
        val hour = if (isStart) startHour else endHour
        val minute = if (isStart) startMinute else endMinute
        TimePickerDialog(this, { _, h, m ->
            if (isStart) {
                startHour = h
                startMinute = m
                tvStartTime.text = String.format(Locale.getDefault(), "%02d:%02d", h, m)
            } else {
                endHour = h
                endMinute = m
                tvEndTime.text = String.format(Locale.getDefault(), "%02d:%02d", h, m)
            }
        }, hour, minute, true).show()
    }

    private fun scheduleNextNotification(reNotifyCount: Int) {
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        val nextTimeMillis = calculateNextNotificationTime(reNotifyCount)

        if (nextTimeMillis <= 0) {
            addLog("通知スケジュールを設定できませんでした (時間帯外または無効な時間)")
            return
        }

        val intent = if (reNotifyCount == 0) {
            Intent(this, NotificationReceiver::class.java)
        } else {
            Intent(this, ReNotificationReceiver::class.java).putExtra("count", reNotifyCount)
        }

        val requestCode = if (reNotifyCount == 0) 100 else 101
        val pendingIntent = PendingIntent.getBroadcast(
            this, requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        addLog(if (reNotifyCount == 0) "次の通常通知をスケジュール" else "再通知をスケジュール")
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + nextTimeMillis,
            pendingIntent
        )
    }

    private fun calculateNextNotificationTime(reNotifyCount: Int): Long {
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

        // 時間帯外なら次の時間帯開始までスキップ
        if (!isWithinTimeBand(nextCal)) {
            // 現在の時刻が終了時間を過ぎている場合、翌日の開始時間に設定
            if (nextCal.timeInMillis > endCal.timeInMillis) {
                nextCal = Calendar.getInstance().apply {
                    timeInMillis = startCal.timeInMillis
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            } else {
                // 開始時間より前なら当日の開始時間に設定
                nextCal = Calendar.getInstance().apply {
                    timeInMillis = startCal.timeInMillis
                }
            }
        }

        val nextTimeMillis = nextCal.timeInMillis - now.timeInMillis
        if (nextTimeMillis <= 0) {
            addLog("エラー: 次の通知時間が過去または現在です: ${DateFormat.format("yyyy-MM-dd HH:mm:ss", nextCal.timeInMillis)}")
            return -1 // 無効な時間を返してスケジュールを中断
        }

        addLog("次の通知時間: ${DateFormat.format("yyyy-MM-dd HH:mm:ss", nextCal.timeInMillis)}")
        return nextTimeMillis
    }

    private fun isWithinTimeBand(cal: Calendar): Boolean {
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

    private fun cancelAllAlarms() {
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager

        // ★修正：Request Code を 0 ではなく 100 と 101 に変更★
        // PendingIntent.getBroadcast の第2引数
        val normalIntent = PendingIntent.getBroadcast(
            this,
            100, // 通常通知の Request Code に合わせる
            Intent(this, NotificationReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val reIntent = PendingIntent.getBroadcast(
            this,
            101, // 再通知の Request Code に合わせる
            Intent(this, ReNotificationReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(normalIntent)
        alarmManager.cancel(reIntent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("reminder_channel", "Reminder Notifications", NotificationManager.IMPORTANCE_DEFAULT)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}