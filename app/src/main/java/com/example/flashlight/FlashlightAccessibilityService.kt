package com.example.flashlight

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat

class FlashlightAccessibilityService : AccessibilityService() {

    companion object {
        const val CHANNEL_ID    = "flashlight_service"
        const val NOTIF_ID      = 1
        const val PREFS         = "flashlight_prefs"
        const val KEY_AUTO_ENABLED  = "auto_enabled"
        const val KEY_TIMER_MINUTES = "timer_minutes"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var pressCount = 0
    private var isFlashlightOn = false
    private var autoEnabled = true
    private var timerMinutes = 1
    private var countDownTimer: CountDownTimer? = null

    private val cameraManager by lazy { getSystemService(CAMERA_SERVICE) as CameraManager }
    private val cameraId by lazy { cameraManager.cameraIdList[0] }
    private val notifManager by lazy { getSystemService(NOTIFICATION_SERVICE) as NotificationManager }
    private val prefs: SharedPreferences by lazy {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    private val PRESS_TIMEOUT = 600L
    private val resetRunnable = Runnable { pressCount = 0 }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onServiceConnected() {
        serviceInfo = AccessibilityServiceInfo().apply {
            flags        = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            eventTypes   = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        }
        autoEnabled  = prefs.getBoolean(KEY_AUTO_ENABLED, true)
        timerMinutes = prefs.getInt(KEY_TIMER_MINUTES, 1)
        FlashlightServiceHolder.service = this
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Ожидание — 3× кнопку громкости"))
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAutoTimer()
        FlashlightServiceHolder.service = null
        try { cameraManager.setTorchMode(cameraId, false) } catch (_: Exception) {}
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    // ── Key handling ──────────────────────────────────────────────────────────

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN &&
            event.action == KeyEvent.ACTION_DOWN) {

            handler.removeCallbacks(resetRunnable)
            pressCount++

            when (pressCount) {
                3 -> { setFlashlight(true);  pressCount = 0; return true }
                4 -> { setFlashlight(false); pressCount = 0; return true }
                else -> handler.postDelayed(resetRunnable, PRESS_TIMEOUT)
            }
        }
        return false
    }

    // ── Flashlight ────────────────────────────────────────────────────────────

    private fun setFlashlight(on: Boolean) {
        try {
            cameraManager.setTorchMode(cameraId, on)
            isFlashlightOn = on
            if (on) {
                if (autoEnabled) startAutoTimer()
                else updateNotification("🔦 Включён  |  4× — выкл")
            } else {
                stopAutoTimer()
                updateNotification("Выключен — 3× кнопку громкости")
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    /** Вызывается из BroadcastReceiver (кнопка в уведомлении) */
    fun turnOffFromNotification() = setFlashlight(false)

    // ── Auto timer ────────────────────────────────────────────────────────────

    private fun startAutoTimer() {
        stopAutoTimer()
        countDownTimer = object : CountDownTimer(timerMinutes * 60 * 1000L, 1000L) {
            override fun onTick(ms: Long) {
                val rem = ms / 1000
                val m = rem / 60
                val s = rem % 60
                updateNotification("🔦 Включён — выкл через $m:${s.toString().padStart(2,'0')}  |  4× — выкл")
            }
            override fun onFinish() { setFlashlight(false) }
        }.start()
        updateNotification("🔦 Включён — выкл через $timerMinutes:00  |  4× — выкл")
    }

    private fun stopAutoTimer() {
        countDownTimer?.cancel()
        countDownTimer = null
    }

    // ── Settings (called from MainActivity) ───────────────────────────────────

    fun applySettings(autoOn: Boolean, minutes: Int) {
        autoEnabled  = autoOn
        timerMinutes = minutes
        if (isFlashlightOn) {
            if (autoEnabled) startAutoTimer()
            else { stopAutoTimer(); updateNotification("🔦 Включён  |  4× — выкл") }
        }
    }

    // ── Notification ─────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel(
                CHANNEL_ID,
                "Flashlight Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
                notifManager.createNotificationChannel(this)
            }
        }
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val offIntent = PendingIntent.getBroadcast(
            this, 1,
            Intent(this, FlashlightActionReceiver::class.java).apply {
                action = "FLASHLIGHT_OFF"
            },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Flashlight")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_flashlight)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .addAction(android.R.drawable.ic_delete, "Выключить", offIntent)
            .build()
    }

    private fun updateNotification(text: String) =
        notifManager.notify(NOTIF_ID, buildNotification(text))
}
