package com.example.trivialfitnesstracker.ui.settings

import android.content.Context
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.trivialfitnesstracker.R

class SettingsActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME = "app_settings"
        const val KEY_REST_DURATION_SECONDS = "rest_duration_seconds"
        const val DEFAULT_REST_DURATION_SECONDS = 120

        fun getRestDurationMs(context: Context): Long {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getInt(KEY_REST_DURATION_SECONDS, DEFAULT_REST_DURATION_SECONDS) * 1000L
        }
    }

    private lateinit var restTimerValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        title = getString(R.string.settings)

        restTimerValue = findViewById(R.id.restTimerValue)
        updateRestTimerDisplay()

        findViewById<LinearLayout>(R.id.restTimerRow).setOnClickListener {
            showRestTimerDialog()
        }
    }

    private fun updateRestTimerDisplay() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val totalSeconds = prefs.getInt(KEY_REST_DURATION_SECONDS, DEFAULT_REST_DURATION_SECONDS)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        restTimerValue.text = String.format("%d:%02d", minutes, seconds)
    }

    private fun showRestTimerDialog() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentSeconds = prefs.getInt(KEY_REST_DURATION_SECONDS, DEFAULT_REST_DURATION_SECONDS)

        val view = layoutInflater.inflate(R.layout.dialog_timer_picker, null)
        val minutesPicker = view.findViewById<NumberPicker>(R.id.minutesPicker)
        val secondsPicker = view.findViewById<NumberPicker>(R.id.secondsPicker)

        minutesPicker.minValue = 0
        minutesPicker.maxValue = 10
        minutesPicker.value = currentSeconds / 60

        secondsPicker.minValue = 0
        secondsPicker.maxValue = 59
        secondsPicker.setFormatter { String.format("%02d", it) }
        secondsPicker.value = currentSeconds % 60

        AlertDialog.Builder(this)
            .setTitle(R.string.rest_timer_duration)
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ ->
                val totalSeconds = minutesPicker.value * 60 + secondsPicker.value
                prefs.edit().putInt(KEY_REST_DURATION_SECONDS, totalSeconds).apply()
                updateRestTimerDisplay()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
