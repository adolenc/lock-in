package com.example.trivialfitnesstracker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.NumberPicker
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.example.trivialfitnesstracker.data.entity.DayOfWeek
import com.example.trivialfitnesstracker.ui.setup.ExerciseListActivity
import com.example.trivialfitnesstracker.ui.settings.SettingsActivity
import com.example.trivialfitnesstracker.ui.stats.StatsActivity
import com.example.trivialfitnesstracker.ui.workout.WorkoutActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.util.Calendar

import com.example.trivialfitnesstracker.ui.stats.ContributionGraphView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import androidx.lifecycle.lifecycleScope
import com.example.trivialfitnesstracker.data.AppDatabase

class MainActivity : AppCompatActivity() {

    private val workoutDays = listOf(
        DayOfWeek.MONDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.SATURDAY
    )

    private lateinit var dayAdapter: DayAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        title = getString(R.string.app_name)

        val recyclerView = findViewById<RecyclerView>(R.id.daysRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        dayAdapter = DayAdapter(workoutDays) { day ->
            val intent = Intent(this, ExerciseListActivity::class.java)
            intent.putExtra(ExerciseListActivity.EXTRA_DAY, day.name)
            startActivity(intent)
        }
        recyclerView.adapter = dayAdapter
        
        val graphView = findViewById<ContributionGraphView>(R.id.mainContributionGraph)
        loadGraphData(graphView)
    }

    private fun loadGraphData(graphView: ContributionGraphView) {
        lifecycleScope.launch(Dispatchers.IO) {
            val dao = AppDatabase.getDatabase(applicationContext).workoutSessionDao()
            val rawStats = dao.getDailySetCounts()

            // Aggregate by LocalDate
            val statsMap = rawStats
                .groupBy { 
                    Instant.ofEpochMilli(it.date)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                }
                .mapValues { entry ->
                    entry.value.sumOf { it.setCount }
                }

            withContext(Dispatchers.Main) {
                graphView.setData(statsMap)
                
                // Scroll to center on today
                graphView.post {
                     val scrollView = findViewById<android.widget.HorizontalScrollView>(R.id.mainGraphScrollView)
                     val targetScrollX = graphView.getInitialScrollX()
                     scrollView.scrollTo(targetScrollX, 0)
                }
            }
        }
    }

    private fun loadCompletedDays() {
        lifecycleScope.launch(Dispatchers.IO) {
            val dao = AppDatabase.getDatabase(applicationContext).workoutSessionDao()
            val startOfWeek = getStartOfWeek()
            val completedDays = dao.getDaysWithCompletedExercisesSince(startOfWeek).toSet()
            
            withContext(Dispatchers.Main) {
                dayAdapter.completedDays = completedDays
            }
        }
    }

    private fun getStartOfWeek(): Long {
        val calendar = Calendar.getInstance()
        // If today is Sunday (first day of week in US), setting DAY_OF_WEEK to MONDAY goes forward to tomorrow.
        // We want the previous Monday.
        // If today is Monday, we want today 00:00.
        
        // Ensure we are at start of day
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        // Adjust to Monday
        var dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        // Convert to Monday=0, Sunday=6 relative to Monday
        // Calendar.MONDAY is 2.
        // If today is Monday (2), diff should be 0.
        // If today is Tuesday (3), diff should be -1.
        // If today is Sunday (1), diff should be -6.
        
        // Let's iterate backwards until we hit Monday.
        while (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            calendar.add(Calendar.DAY_OF_MONTH, -1)
        }
        
        return calendar.timeInMillis
    }

    private fun getTodayWorkoutDayIndex(): Int {
        val calendar = Calendar.getInstance()
        val todayCalendarDay = calendar.get(Calendar.DAY_OF_WEEK)
        
        val todayDayOfWeek = when (todayCalendarDay) {
            Calendar.MONDAY -> DayOfWeek.MONDAY
            Calendar.TUESDAY -> DayOfWeek.TUESDAY
            Calendar.WEDNESDAY -> DayOfWeek.WEDNESDAY
            Calendar.THURSDAY -> DayOfWeek.THURSDAY
            Calendar.FRIDAY -> DayOfWeek.FRIDAY
            Calendar.SATURDAY -> DayOfWeek.SATURDAY
            Calendar.SUNDAY -> DayOfWeek.SUNDAY
            else -> null
        }
        
        val index = workoutDays.indexOf(todayDayOfWeek)
        return if (index >= 0) index else 0
    }

    override fun onResume() {
        super.onResume()
        // Check if there's an active workout session and resume it
        val prefs = getSharedPreferences("workout_state", Context.MODE_PRIVATE)
        val savedSessionId = prefs.getLong("session_id", -1)
        if (savedSessionId != -1L) {
            startActivity(Intent(this, WorkoutActivity::class.java))
        }
        
        // Refresh graph data when returning to main activity (in case a workout was just finished)
        val graphView = findViewById<ContributionGraphView>(R.id.mainContributionGraph)
        loadGraphData(graphView)
        loadCompletedDays()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_stats -> {
                startActivity(Intent(this, StatsActivity::class.java))
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

    private class DayAdapter(
    private val days: List<DayOfWeek>,
    private val onClick: (DayOfWeek) -> Unit
) : RecyclerView.Adapter<DayAdapter.DayViewHolder>() {

    var completedDays: Set<DayOfWeek> = emptySet()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    class DayViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dayName: TextView = view.findViewById(R.id.dayName)
        val indicator: View = view.findViewById(R.id.completionIndicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_day, parent, false)
        return DayViewHolder(view)
    }

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        val day = days[position]
        holder.dayName.text = day.displayName()
        holder.indicator.visibility = if (day in completedDays) View.VISIBLE else View.GONE
        holder.itemView.setOnClickListener { onClick(day) }
    }

    override fun getItemCount() = days.size
}
