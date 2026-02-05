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
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.trivialfitnesstracker.R
import com.example.trivialfitnesstracker.data.AppDatabase
import com.example.trivialfitnesstracker.data.WorkoutRepository
import com.example.trivialfitnesstracker.data.entity.DayOfWeek
import com.example.trivialfitnesstracker.data.entity.Exercise
import com.example.trivialfitnesstracker.ui.settings.SettingsActivity

class WorkoutActivity : AppCompatActivity() {


    companion object {
        const val EXTRA_DAY = "extra_day"
        private const val PREFS_NAME = "workout_state"
        private const val KEY_SESSION_ID = "session_id"
        private const val KEY_DAY = "day"
        private const val KEY_EXERCISE_INDEX = "exercise_index"
        private const val CHANNEL_ID = "rest_timer"
        private const val NOTIFICATION_ID = 1
        private const val PERMISSION_REQUEST_CODE = 100
    }

    private lateinit var viewModel: WorkoutViewModel
    private lateinit var prefs: android.content.SharedPreferences

    private lateinit var prevButton: Button
    private lateinit var nextButton: Button
    private lateinit var progressDots: LinearLayout
    private lateinit var timerContainer: LinearLayout
    private lateinit var timerText: TextView
    private lateinit var timerProgress: ProgressBar
    private lateinit var skipTimerButton: Button
    private lateinit var finishWorkoutButton: Button
    private lateinit var viewPager: ViewPager2
    
    private var countDownTimer: CountDownTimer? = null
    private var isTimerRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workout)

        // Hide action bar for cleaner look
        supportActionBar?.hide()

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        // Bind views
        prevButton = findViewById(R.id.prevExerciseButton)
        nextButton = findViewById(R.id.nextExerciseButton)
        progressDots = findViewById(R.id.progressDots)
        timerContainer = findViewById(R.id.timerContainer)
        timerText = findViewById(R.id.timerText)
        timerProgress = findViewById(R.id.timerProgress)
        skipTimerButton = findViewById(R.id.skipTimerButton)
        finishWorkoutButton = findViewById(R.id.finishWorkoutButton)
        viewPager = findViewById(R.id.viewPager)

        createNotificationChannel()
        requestNotificationPermission()

        val db = AppDatabase.getDatabase(this)
        val repository = WorkoutRepository(
            db.exerciseDao(),
            db.workoutSessionDao(),
            db.exerciseLogDao(),
            db.setLogDao(),
            db.exerciseVariationDao()
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
            viewModel.resumeWorkout(DayOfWeek.valueOf(savedDay), savedSessionId, savedIndex)
        } else if (dayExtra != null) {
            viewModel.startWorkout(DayOfWeek.valueOf(dayExtra))
        } else {
            finish()
        }
    }

    override fun onBackPressed() {
        // Disable back button - must use Finish
    }

    fun refreshProgress() {
        viewModel.updateExerciseStatuses()
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

    private fun setupObservers() {
        viewModel.exercisesList.observe(this) { exercises ->
            if (exercises.isNotEmpty()) {
                val sessionId = viewModel.getSessionInfo()?.first ?: return@observe
                
                viewPager.adapter = object : FragmentStateAdapter(this) {
                    override fun getItemCount(): Int = exercises.size
                    override fun createFragment(position: Int): Fragment {
                        return WorkoutFragment.newInstance(sessionId, exercises[position].id)
                    }
                }
                
                // Restore index if needed
                viewPager.setCurrentItem(viewModel.getCurrentIndex(), false)
            }
        }

        viewModel.currentIndex.observe(this) { index ->
             if (viewPager.currentItem != index) {
                 viewPager.setCurrentItem(index, true)
             }
             updateNavigationButtons(index)
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
                        viewPager.currentItem = status.index
                    }
                }
                progressDots.addView(dot)
            }
        }

        viewModel.isFinished.observe(this) { finished ->
            if (finished) finish()
        }
    }

    private fun updateNavigationButtons(index: Int) {
        val count = viewPager.adapter?.itemCount ?: 0
        prevButton.isEnabled = index > 0
        prevButton.alpha = if (index > 0) 1f else 0.5f
        nextButton.isEnabled = index < count - 1
        nextButton.alpha = if (index < count - 1) 1f else 0.5f
        
        viewModel.getSessionInfo()?.let { (sessionId, day) ->
            saveWorkoutState(sessionId, day, index)
        }
        
        // Update dots visual state
        refreshProgress()
    }

    private fun setupClickListeners() {
        skipTimerButton.setOnClickListener {
            stopRestTimer()
        }

        prevButton.setOnClickListener {
            val current = viewPager.currentItem
            if (current > 0) viewPager.currentItem = current - 1
        }

        nextButton.setOnClickListener {
            val current = viewPager.currentItem
            val count = viewPager.adapter?.itemCount ?: 0
            if (current < count - 1) viewPager.currentItem = current + 1
        }

        finishWorkoutButton.setOnClickListener {
            showFinishConfirmation()
        }
        
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                viewModel.setCurrentIndex(position)
            }
        })
    }
    
    // Timer functions remain same...
    fun startRestTimer() {
        stopRestTimer()
        
        timerContainer.visibility = View.VISIBLE
        isTimerRunning = true

        val restDurationMs = SettingsActivity.getRestDurationMs(this)
        
        countDownTimer = object : CountDownTimer(restDurationMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = ((millisUntilFinished + 999) / 1000).toInt()
                val minutes = seconds / 60
                val secs = seconds % 60
                val timeText = String.format("%d:%02d", minutes, secs)
                timerText.text = "Rest: $timeText"
                
                val elapsed = restDurationMs - millisUntilFinished
                val progress = (elapsed * 100 / restDurationMs).toInt()
                timerProgress.progress = progress
                
                updateTimerNotification(timeText)
            }

            override fun onFinish() {
                timerText.text = "Rest: 0:00"
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

    // Other helpers (vibrate, notification, confirm dialog)
    // ... (Keep existing implementation of these helper methods)

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
    
    override fun onResume() {
        super.onResume()
    }
    
    override fun onPause() {
        super.onPause()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
