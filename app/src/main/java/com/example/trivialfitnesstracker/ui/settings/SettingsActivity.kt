package com.example.trivialfitnesstracker.ui.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.trivialfitnesstracker.R
import com.example.trivialfitnesstracker.data.AppDatabase
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let { exportDatabase(it) }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { showImportConfirmation(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        title = getString(R.string.settings)

        restTimerValue = findViewById(R.id.restTimerValue)
        updateRestTimerDisplay()

        findViewById<LinearLayout>(R.id.restTimerRow).setOnClickListener {
            showRestTimerDialog()
        }

        findViewById<TextView>(R.id.exportRow).setOnClickListener {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            exportLauncher.launch("fitness_backup_$timestamp.db")
        }

        findViewById<TextView>(R.id.importRow).setOnClickListener {
            importLauncher.launch(arrayOf("*/*"))
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

    private fun exportDatabase(uri: android.net.Uri) {
        try {
            // Close and checkpoint the database
            AppDatabase.closeDatabase()
            
            val dbFile = getDatabasePath(AppDatabase.DATABASE_NAME)
            contentResolver.openOutputStream(uri)?.use { output ->
                FileInputStream(dbFile).use { input ->
                    input.copyTo(output)
                }
            }
            Toast.makeText(this, R.string.export_success, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, R.string.export_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showImportConfirmation(uri: android.net.Uri) {
        AlertDialog.Builder(this)
            .setTitle(R.string.import_database)
            .setMessage(R.string.import_warning)
            .setPositiveButton(R.string.save) { _, _ ->
                importDatabase(uri)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun importDatabase(uri: android.net.Uri) {
        try {
            AppDatabase.closeDatabase()
            
            val dbFile = getDatabasePath(AppDatabase.DATABASE_NAME)
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dbFile).use { output ->
                    input.copyTo(output)
                }
            }
            Toast.makeText(this, R.string.import_success, Toast.LENGTH_SHORT).show()
            finish()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, R.string.import_error, Toast.LENGTH_SHORT).show()
        }
    }
}
