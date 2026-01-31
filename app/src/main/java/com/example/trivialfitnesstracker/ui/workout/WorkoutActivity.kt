package com.example.trivialfitnesstracker.ui.workout

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModelProvider
import com.example.trivialfitnesstracker.R
import com.example.trivialfitnesstracker.data.AppDatabase
import com.example.trivialfitnesstracker.data.WorkoutRepository
import com.example.trivialfitnesstracker.data.entity.DayOfWeek

class WorkoutActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DAY = "extra_day"
        private const val PREFS_NAME = "workout_state"
        private const val KEY_SESSION_ID = "session_id"
        private const val KEY_DAY = "day"
        private const val KEY_EXERCISE_INDEX = "exercise_index"
        private const val CHANNEL_ID = "rest_timer"
        private const val NOTIFICATION_ID = 1
        private const val REST_DURATION_MS = 120_000L // 2 minutes
        private const val PERMISSION_REQUEST_CODE = 100
    }

    private lateinit var viewModel: WorkoutViewModel
    private lateinit var prefs: android.content.SharedPreferences

    private lateinit var exerciseName: TextView
    private lateinit var historyText: TextView
    private lateinit var weightInput: EditText
    private lateinit var todaySetsText: TextView
    private lateinit var repsDisplay: TextView
    private lateinit var prevButton: Button
    private lateinit var nextButton: Button
    private lateinit var undoButton: Button
    private lateinit var progressDots: LinearLayout
    private lateinit var noteText: TextView
    private lateinit var addNoteButton: Button
    private lateinit var timerContainer: LinearLayout
    private lateinit var timerText: TextView
    private lateinit var timerProgress: ProgressBar
    private lateinit var skipTimerButton: Button

    private var countDownTimer: CountDownTimer? = null
    private var isTimerRunning = false
    private var isInForeground = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workout)

        // Hide action bar for cleaner look
        supportActionBar?.hide()

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        // Bind views
        exerciseName = findViewById(R.id.exerciseName)
        historyText = findViewById(R.id.historyText)
        weightInput = findViewById(R.id.weightInput)
        todaySetsText = findViewById(R.id.todaySetsText)
        repsDisplay = findViewById(R.id.repsDisplay)
        prevButton = findViewById(R.id.prevExerciseButton)
        nextButton = findViewById(R.id.nextExerciseButton)
        undoButton = findViewById(R.id.undoButton)
        progressDots = findViewById(R.id.progressDots)
        noteText = findViewById(R.id.noteText)
        addNoteButton = findViewById(R.id.addNoteButton)
        timerContainer = findViewById(R.id.timerContainer)
        timerText = findViewById(R.id.timerText)
        timerProgress = findViewById(R.id.timerProgress)
        skipTimerButton = findViewById(R.id.skipTimerButton)

        createNotificationChannel()
        requestNotificationPermission()

        val db = AppDatabase.getDatabase(this)
        val repository = WorkoutRepository(
            db.exerciseDao(),
            db.workoutSessionDao(),
            db.exerciseLogDao(),
            db.setLogDao()
        )
        viewModel = ViewModelProvider(this, WorkoutViewModelFactory(repository))
            .get(WorkoutViewModel::class.java)

        setupObservers()
        setupClickListeners()

        // Check for saved session first
        val savedSessionId = prefs.getLong(KEY_SESSION_ID, -1)
        val savedDay = prefs.getString(KEY_DAY, null)
        val savedIndex = prefs.getInt(KEY_EXERCISE_INDEX, 0)

        val dayExtra = intent.getStringExtra(EXTRA_DAY)
        
        if (savedSessionId != -1L && savedDay != null) {
            // Resume existing session
            viewModel.resumeWorkout(DayOfWeek.valueOf(savedDay), savedSessionId, savedIndex)
        } else if (dayExtra != null) {
            viewModel.startWorkout(DayOfWeek.valueOf(dayExtra))
        } else {
            showDaySelectionDialog()
        }
    }

    private fun saveWorkoutState(sessionId: Long, day: DayOfWeek, exerciseIndex: Int) {
        prefs.edit()
            .putLong(KEY_SESSION_ID, sessionId)
            .putString(KEY_DAY, day.name)
            .putInt(KEY_EXERCISE_INDEX, exerciseIndex)
            .apply()
    }

    private fun clearWorkoutState() {
        prefs.edit().clear().apply()
    }

    override fun onBackPressed() {
        // Disable back button - must use Finish
    }

    private fun setupObservers() {
        viewModel.currentExercise.observe(this) { exercise ->
            exerciseName.text = exercise?.name ?: ""
        }

        viewModel.progress.observe(this) { (current, total) ->
            prevButton.isEnabled = !viewModel.isFirstExercise()
            prevButton.alpha = if (viewModel.isFirstExercise()) 0.5f else 1f
            nextButton.text = if (viewModel.isLastExercise()) 
                getString(R.string.finish_workout) else getString(R.string.next)
            
            // Save state whenever exercise changes
            viewModel.getSessionInfo()?.let { (sessionId, day) ->
                saveWorkoutState(sessionId, day, viewModel.getCurrentIndex())
            }
        }

        viewModel.exerciseStatuses.observe(this) { statuses ->
            progressDots.removeAllViews()
            val currentIndex = viewModel.getCurrentIndex()
            
            statuses.forEach { status ->
                val dot = TextView(this).apply {
                    text = "●"
                    textSize = 16f
                    setPadding(8, 0, 8, 0)
                    
                    setTextColor(when {
                        status.index == currentIndex -> Color.parseColor("#2196F3") // blue
                        status.hasSets -> Color.parseColor("#4CAF50") // green
                        else -> Color.parseColor("#9E9E9E") // gray
                    })
                    
                    setOnClickListener {
                        viewModel.goToExercise(status.index)
                        weightInput.text.clear()
                    }
                }
                progressDots.addView(dot)
            }
        }

        viewModel.history.observe(this) { historyList ->
            if (historyList.isEmpty()) {
                historyText.text = getString(R.string.no_history)
            } else {
                historyText.text = historyList.joinToString("\n") { h ->
                    val regularSets = h.sets.filter { !it.isDropdown }
                    val dropdownSets = h.sets.filter { it.isDropdown }
                    val weight = regularSets.firstOrNull()?.weight?.let { "${it.toInt()}kg" } ?: "?"
                    val reps = regularSets.joinToString(", ") { it.reps.toString() }
                    val dropdown = if (dropdownSets.isNotEmpty()) 
                        " + ${dropdownSets.joinToString(", ") { it.reps.toString() }}" else ""
                    val note = if (!h.note.isNullOrEmpty()) " (${h.note})" else ""
                    "${h.date}: $weight × $reps$dropdown$note"
                }
            }
        }

        viewModel.lastWeight.observe(this) { weight ->
            if (weight != null && weightInput.text.isEmpty()) {
                weightInput.setText(weight.toInt().toString())
            }
        }

        viewModel.lastReps.observe(this) { reps ->
            if (reps != null) {
                viewModel.setReps(reps)
            }
        }

        viewModel.todaySets.observe(this) { sets ->
            if (sets.isEmpty()) {
                todaySetsText.text = getString(R.string.no_sets_yet)
            } else {
                val regularSets = sets.filter { !it.isDropdown }
                val dropdownSets = sets.filter { it.isDropdown }
                val reps = regularSets.joinToString(", ") { it.reps.toString() }
                val dropdown = if (dropdownSets.isNotEmpty())
                    " + ${dropdownSets.joinToString(", ") { it.reps.toString() }}" else ""
                todaySetsText.text = getString(R.string.sets_today, "$reps$dropdown")
            }
        }

        viewModel.reps.observe(this) { reps ->
            repsDisplay.text = reps.toString()
        }

        viewModel.canUndo.observe(this) { canUndo ->
            undoButton.isEnabled = canUndo
            undoButton.alpha = if (canUndo) 1f else 0.5f
        }

        viewModel.currentNote.observe(this) { note ->
            if (note.isNullOrEmpty()) {
                noteText.visibility = View.GONE
                addNoteButton.visibility = View.VISIBLE
            } else {
                noteText.visibility = View.VISIBLE
                noteText.text = note
                addNoteButton.visibility = View.GONE
            }
        }

        viewModel.isFinished.observe(this) { finished ->
            if (finished) finish()
        }
    }

    private fun setupClickListeners() {
        findViewById<Button>(R.id.incrementReps).setOnClickListener {
            viewModel.incrementReps()
        }

        findViewById<Button>(R.id.decrementReps).setOnClickListener {
            viewModel.decrementReps()
        }

        findViewById<Button>(R.id.logSetButton).setOnClickListener {
            val weight = weightInput.text.toString().toFloatOrNull() ?: 0f
            viewModel.logSet(weight)
            startRestTimer()
        }

        findViewById<Button>(R.id.logDropdownButton).setOnClickListener {
            viewModel.logDropdown()
            startRestTimer()
        }

        undoButton.setOnClickListener {
            viewModel.undoLastSet()
        }

        addNoteButton.setOnClickListener {
            showNoteDialog()
        }

        noteText.setOnClickListener {
            showNoteDialog()
        }

        skipTimerButton.setOnClickListener {
            stopRestTimer()
        }

        prevButton.setOnClickListener {
            updateWeightIfChanged()
            viewModel.previousExercise()
            weightInput.text.clear()
        }

        nextButton.setOnClickListener {
            updateWeightIfChanged()
            if (viewModel.isLastExercise()) {
                showFinishConfirmation()
            } else {
                viewModel.nextExercise()
                weightInput.text.clear()
            }
        }
    }

    private fun updateWeightIfChanged() {
        val weight = weightInput.text.toString().toFloatOrNull()
        if (weight != null) {
            viewModel.updateWeight(weight)
        }
    }

    private fun showFinishConfirmation() {
        AlertDialog.Builder(this)
            .setTitle(R.string.finish_workout)
            .setMessage(R.string.finish_workout_confirm)
            .setPositiveButton(R.string.finish_workout) { _, _ ->
                stopRestTimer()
                clearWorkoutState()
                finish()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showNoteDialog() {
        val input = EditText(this)
        input.hint = getString(R.string.note_hint)
        input.setText(viewModel.currentNote.value ?: "")

        AlertDialog.Builder(this)
            .setTitle(if (viewModel.currentNote.value.isNullOrEmpty()) R.string.add_note else R.string.edit_note)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                viewModel.setNote(input.text.toString())
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showDaySelectionDialog() {
        val workoutDays = listOf(
            DayOfWeek.MONDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.SATURDAY
        )
        val dayNames = workoutDays.map { it.displayName() }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(R.string.select_day)
            .setItems(dayNames) { _, which ->
                viewModel.startWorkout(workoutDays[which])
            }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Rest Timer",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications when rest timer completes"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    private fun startRestTimer() {
        stopRestTimer()
        
        timerContainer.visibility = View.VISIBLE
        isTimerRunning = true

        countDownTimer = object : CountDownTimer(REST_DURATION_MS, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = ((millisUntilFinished + 999) / 1000).toInt()
                val minutes = seconds / 60
                val secs = seconds % 60
                val timeText = String.format("%d:%02d", minutes, secs)
                timerText.text = timeText
                
                val elapsed = REST_DURATION_MS - millisUntilFinished
                val progress = (elapsed * 100 / REST_DURATION_MS).toInt()
                timerProgress.progress = progress
                
                updateTimerNotification(timeText)
            }

            override fun onFinish() {
                timerText.text = "0:00"
                timerProgress.progress = 100
                isTimerRunning = false
                
                vibrate()
                showTimerCompleteNotification()
                
                // Auto-hide after a moment
                timerContainer.postDelayed({
                    if (!isTimerRunning) {
                        timerContainer.visibility = View.GONE
                    }
                }, 3000)
            }
        }.start()
    }

    private fun stopRestTimer() {
        countDownTimer?.cancel()
        countDownTimer = null
        isTimerRunning = false
        timerContainer.visibility = View.GONE
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
    }

    private fun vibrate() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(500)
            }
        } catch (e: Exception) {
            // Ignore vibration errors
        }
    }

    private fun updateTimerNotification(timeRemaining: String) {
        try {
            val intent = Intent(this, WorkoutActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(getString(R.string.rest_timer))
                .setContentText(getString(R.string.time_remaining, timeRemaining))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setSilent(true)
                .setContentIntent(pendingIntent)
                .build()

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            // Ignore notification errors
        }
    }

    private fun showTimerCompleteNotification() {
        try {
            val intent = Intent(this, WorkoutActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(getString(R.string.rest_complete))
                .setContentText(getString(R.string.rest_timer_done))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            // Ignore notification errors
        }
    }

    override fun onResume() {
        super.onResume()
        isInForeground = true
    }

    override fun onPause() {
        super.onPause()
        isInForeground = false
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
