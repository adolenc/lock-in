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

            // Exercise Stats
            val threeMonthsAgo = java.time.LocalDate.now().minusMonths(3)
            val startDate = threeMonthsAgo.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val exerciseStats = exerciseLogDao.getExerciseStats(startDate)
            
            // Group by DayName first
            val statsByDay = exerciseStats.groupBy { it.dayName }

            withContext(Dispatchers.Main) {
                graphView.setData(statsMap)
                
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
                            textSize = 18f
                            setTypeface(null, android.graphics.Typeface.BOLD)
                            setPadding(0, 16, 0, 8)
                        }
                        dayContentLayout.addView(titleView)
                        
                        val barGraph = ExerciseBarGraphView(this@StatsActivity).apply {
                            layoutParams = android.widget.LinearLayout.LayoutParams(
                                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                            setData(dataMap, threeMonthsAgo, java.time.LocalDate.now())
                        }
                        dayContentLayout.addView(barGraph)
                    }
                }
            }
        }
    }
}
