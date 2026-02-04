package com.example.trivialfitnesstracker.ui.stats

import android.os.Bundle
import android.widget.HorizontalScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.trivialfitnesstracker.R
import com.example.trivialfitnesstracker.data.AppDatabase
import com.example.trivialfitnesstracker.data.model.DailySetCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

class StatsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)
        title = getString(R.string.stats)

        val graphView = findViewById<ContributionGraphView>(R.id.contributionGraph)
        // val scrollView = findViewById<HorizontalScrollView>(R.id.statsScrollView) // View ID still exists but usage might need checking if used for scrolling logic
        val graphsContainer = findViewById<android.widget.LinearLayout>(R.id.graphsContainer)
        val statsOptionsButton = findViewById<android.widget.ImageButton>(R.id.statsOptionsButton)
        
        // List to hold all created graph views so we can update them when switch toggles
        val allGraphViews = mutableListOf<ExerciseBarGraphView>()
        // Store only the raw data for each graph, not the derived ranges
        val graphData = mutableMapOf<ExerciseBarGraphView, Map<java.time.LocalDate, Float>>()
        
        var showMissingDays = true
        var showAllHistory = false // false = last 3 months, true = all history

        statsOptionsButton.setOnClickListener {
            // Using AlertDialog for a modal window in the center of the screen
            val popupView = layoutInflater.inflate(R.layout.popup_stats_options, null)
            val switch = popupView.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.showMissingDaysSwitch)
            val spinner = popupView.findViewById<android.widget.Spinner>(R.id.timeRangeSpinner)
            
            // Setup Spinner
            val timeOptions = listOf("Last 3 months", "All history")
            val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, timeOptions)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = adapter
            spinner.setSelection(if (showAllHistory) 1 else 0)
            
            switch.isChecked = showMissingDays
            
            val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Stats Options")
                .setView(popupView)
                .setPositiveButton("Close", null)
                .create()
                
            switch.setOnCheckedChangeListener { _, isChecked ->
                showMissingDays = isChecked
                updateAllGraphs(allGraphViews, graphData, showMissingDays, showAllHistory)
            }
            
            spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    val newShowAll = position == 1
                    if (showAllHistory != newShowAll) {
                        showAllHistory = newShowAll
                        updateAllGraphs(allGraphViews, graphData, showMissingDays, showAllHistory)
                    }
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
            
            dialog.show()
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(applicationContext)
            val dao = db.workoutSessionDao()
            val exerciseLogDao = db.exerciseLogDao()

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

            // Exercise Stats - Fetch ALL stats (start date 0)
            val exerciseStats = exerciseLogDao.getExerciseStats(0)
            
            // Group by DayName first
            val statsByDay = exerciseStats.groupBy { it.dayName }

            withContext(Dispatchers.Main) {
                graphView.setData(statsMap)
                
                // Scroll to end (today) after layout
                graphView.post {
                     val scrollView = findViewById<HorizontalScrollView>(R.id.statsScrollView)
                     val targetScrollX = graphView.getInitialScrollX()
                     scrollView.scrollTo(targetScrollX, 0)
                }
                
                graphsContainer.removeAllViews()
                
                // Sort days (MONDAY, TUESDAY...)
                val sortedDays = statsByDay.keys.sortedBy { 
                     try {
                         com.example.trivialfitnesstracker.data.entity.DayOfWeek.valueOf(it).ordinal
                     } catch (e: Exception) {
                         Int.MAX_VALUE
                     }
                }

                for (dayName in sortedDays) {
                    val dayStats = statsByDay[dayName] ?: continue
                    
                    val formattedDayName = try {
                        com.example.trivialfitnesstracker.data.entity.DayOfWeek.valueOf(dayName).displayName()
                    } catch (e: Exception) {
                        dayName
                    }
                    
                    // Add Section Header for Day (Replicating Recent History style)
                    val dayHeaderLayout = android.widget.LinearLayout(this@StatsActivity).apply {
                        orientation = android.widget.LinearLayout.HORIZONTAL
                        layoutParams = android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        val padding = (12 * resources.displayMetrics.density).toInt()
                        setPadding(padding, padding, padding, padding)
                        
                        val outValue = android.util.TypedValue()
                        theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                        setBackgroundResource(outValue.resourceId)
                    }

                    val dayLabel = android.widget.TextView(this@StatsActivity).apply {
                        layoutParams = android.widget.LinearLayout.LayoutParams(
                            0,
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f
                        )
                        text = formattedDayName
                        textSize = 18f // Slightly larger than history (14sp) as it is a main section
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        setTextColor(android.graphics.Color.parseColor("#999999"))
                    }
                    
                    val arrowLabel = android.widget.TextView(this@StatsActivity).apply {
                        layoutParams = android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        text = "▼"
                        textSize = 14f
                        setTextColor(android.graphics.Color.parseColor("#999999"))
                    }

                    dayHeaderLayout.addView(dayLabel)
                    dayHeaderLayout.addView(arrowLabel)
                    
                    graphsContainer.addView(dayHeaderLayout)

                    val dayContentLayout = android.widget.LinearLayout(this@StatsActivity).apply {
                        orientation = android.widget.LinearLayout.VERTICAL
                        layoutParams = android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        visibility = android.view.View.GONE
                    }
                    graphsContainer.addView(dayContentLayout)

                    dayHeaderLayout.setOnClickListener {
                        if (dayContentLayout.visibility == android.view.View.VISIBLE) {
                            dayContentLayout.visibility = android.view.View.GONE
                            arrowLabel.text = "▼"
                        } else {
                            dayContentLayout.visibility = android.view.View.VISIBLE
                            arrowLabel.text = "▲"
                        }
                    }

                    // Group by Exercise within the day and sort by orderIndex
                    val exercisesInDay = dayStats.groupBy { it.exerciseId }
                        .mapValues { (_, list) -> list } // just to keep the list
                        .entries.sortedBy { entry -> 
                            entry.value.firstOrNull()?.orderIndex ?: 0 
                        }

                    for ((_, exerciseStatsList) in exercisesInDay) {
                         val exerciseName = exerciseStatsList.first().exerciseName
                         
                         val dataMap = exerciseStatsList
                            .groupBy { 
                                 Instant.ofEpochMilli(it.date)
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDate()
                            }
                            .mapValues { entry ->
                                entry.value.sumOf { it.totalWeight.toDouble() }.toFloat()
                            }

                        val titleView = android.widget.TextView(this@StatsActivity).apply {
                            text = exerciseName
                            textSize = 14f
                            setTypeface(null, android.graphics.Typeface.BOLD)
                            setPadding(0, 16, 0, 8)
                            gravity = android.view.Gravity.CENTER_HORIZONTAL
                            setTextColor(android.graphics.Color.parseColor("#767676"))
                        }
                        dayContentLayout.addView(titleView)
                        
                        val barGraph = ExerciseBarGraphView(this@StatsActivity).apply {
                            layoutParams = android.widget.LinearLayout.LayoutParams(
                                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                            // Initial setup using defaults (3 months)
                            val today = java.time.LocalDate.now()
                            val threeMonthsAgo = today.minusMonths(3)
                            setData(dataMap, threeMonthsAgo, today, showMissingDays)
                        }
                        allGraphViews.add(barGraph)
                        graphData[barGraph] = dataMap
                        dayContentLayout.addView(barGraph)
                    }
                }
            }
        }
    }

    private fun updateAllGraphs(
        views: List<ExerciseBarGraphView>,
        dataMap: Map<ExerciseBarGraphView, Map<java.time.LocalDate, Float>>,
        showMissingDays: Boolean,
        showAllHistory: Boolean
    ) {
        val today = java.time.LocalDate.now()
        
        views.forEach { graph ->
            val data = dataMap[graph] ?: return@forEach
            
            val startDate = if (showAllHistory) {
                // Find min date in data or today if empty
                val minDate = data.keys.minOrNull() ?: today
                // Maybe add some padding? Or just use exact min date.
                minDate
            } else {
                today.minusMonths(3)
            }
            
            graph.setData(data, startDate, today, showMissingDays)
        }
    }
}
