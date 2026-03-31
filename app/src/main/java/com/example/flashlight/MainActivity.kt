package com.example.flashlight

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences(FlashlightAccessibilityService.PREFS, Context.MODE_PRIVATE)
    }

    // UI refs
    private lateinit var statusIcon: TextView
    private lateinit var statusTitle: TextView
    private lateinit var statusDesc: TextView
    private lateinit var enableBtn: Button
    private lateinit var batPercent: TextView
    private lateinit var batBar: ProgressBar
    private lateinit var batStatus: TextView
    private lateinit var autoSwitch: Switch
    private lateinit var timerSlider: SeekBar
    private lateinit var timerLabel: TextView
    private lateinit var timerSection: LinearLayout
    private lateinit var presetGroup: RadioGroup

    private var timerMinutes = 1

    // Battery broadcast receiver
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val charging = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
            updateBatteryUI(pct, charging)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        loadPrefs()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updateServiceUI()
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(batteryReceiver) } catch (e: Exception) {}
    }

    private fun bindViews() {
        statusIcon   = findViewById(R.id.statusIcon)
        statusTitle  = findViewById(R.id.statusTitle)
        statusDesc   = findViewById(R.id.statusDesc)
        enableBtn    = findViewById(R.id.enableBtn)
        batPercent   = findViewById(R.id.batPercent)
        batBar       = findViewById(R.id.batBar)
        batStatus    = findViewById(R.id.batStatus)
        autoSwitch   = findViewById(R.id.autoSwitch)
        timerSlider  = findViewById(R.id.timerSlider)
        timerLabel   = findViewById(R.id.timerLabel)
        timerSection = findViewById(R.id.timerSection)
        presetGroup  = findViewById(R.id.presetGroup)
    }

    private fun loadPrefs() {
        val autoEnabled = prefs.getBoolean(FlashlightAccessibilityService.KEY_AUTO_ENABLED, true)
        timerMinutes   = prefs.getInt(FlashlightAccessibilityService.KEY_TIMER_MINUTES, 1)

        autoSwitch.isChecked = autoEnabled
        timerSection.visibility = if (autoEnabled) View.VISIBLE else View.GONE
        timerSlider.progress = timerMinutes - 1
        timerLabel.text = "Таймер: $timerMinutes мин"
        updatePresetSelection(timerMinutes)
    }

    private fun savePrefs() {
        val autoEnabled = autoSwitch.isChecked
        prefs.edit()
            .putBoolean(FlashlightAccessibilityService.KEY_AUTO_ENABLED, autoEnabled)
            .putInt(FlashlightAccessibilityService.KEY_TIMER_MINUTES, timerMinutes)
            .apply()
        // Применяем к живому сервису если активен
        FlashlightServiceHolder.service?.applySettings(autoEnabled, timerMinutes)
    }

    private fun setupListeners() {
        enableBtn.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        autoSwitch.setOnCheckedChangeListener { _, checked ->
            timerSection.visibility = if (checked) View.VISIBLE else View.GONE
            savePrefs()
        }

        timerSlider.max = 19 // 1..20
        timerSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                timerMinutes = progress + 1
                timerLabel.text = "Таймер: $timerMinutes мин"
                updatePresetSelection(timerMinutes)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) { savePrefs() }
        })

        presetGroup.setOnCheckedChangeListener { _, checkedId ->
            val min = when (checkedId) {
                R.id.preset1  -> 1
                R.id.preset3  -> 3
                R.id.preset5  -> 5
                R.id.preset10 -> 10
                R.id.preset20 -> 20
                else -> timerMinutes
            }
            timerMinutes = min
            timerSlider.progress = min - 1
            timerLabel.text = "Таймер: $min мин"
            savePrefs()
        }
    }

    private fun updateServiceUI() {
        val active = isAccessibilityEnabled()
        if (active) {
            statusIcon.text  = "✓"
            statusIcon.setTextColor(ContextCompat.getColor(this, R.color.green))
            statusTitle.text = "Сервис активен"
            statusDesc.text  = "3× кнопку громкости → фонарик\n4× кнопку громкости → выкл"
            enableBtn.text   = "Настройки доступности"
        } else {
            statusIcon.text  = "!"
            statusIcon.setTextColor(ContextCompat.getColor(this, R.color.yellow))
            statusTitle.text = "Сервис не включён"
            statusDesc.text  = "Нажми кнопку ниже → найди «Flashlight Service» → включи"
            enableBtn.text   = "Включить сервис"
        }
    }

    private fun updateBatteryUI(pct: Int, charging: Boolean) {
        if (pct < 0) { batPercent.text = "—%"; return }
        batPercent.text = "$pct%"
        batBar.progress = pct

        val color = when {
            pct <= 10 -> R.color.red
            pct <= 25 -> R.color.orange
            pct <= 50 -> R.color.yellow
            else      -> R.color.green
        }
        batBar.progressTintList =
            android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, color))

        batStatus.text = when {
            charging  -> "⚡ Заряжается"
            pct <= 10 -> "Критически мало"
            pct <= 25 -> "Мало заряда"
            else      -> "Заряд нормальный"
        }
    }

    private fun updatePresetSelection(min: Int) {
        val id = when (min) {
            1  -> R.id.preset1
            3  -> R.id.preset3
            5  -> R.id.preset5
            10 -> R.id.preset10
            20 -> R.id.preset20
            else -> -1
        }
        if (id != -1) presetGroup.check(id)
        else presetGroup.clearCheck()
    }

    private fun isAccessibilityEnabled(): Boolean {
        val svc = "$packageName/${FlashlightAccessibilityService::class.java.canonicalName}"
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            if (splitter.next().equals(svc, ignoreCase = true)) return true
        }
        return false
    }
}
