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
        val scrollView = findViewById<HorizontalScrollView>(R.id.statsScrollView)

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
                // Scroll to end logic removed as we want to see Jan 1st (start of year)
                // scrollView.post { scrollView.fullScroll(HorizontalScrollView.FOCUS_RIGHT) }
            }
        }
    }
}
