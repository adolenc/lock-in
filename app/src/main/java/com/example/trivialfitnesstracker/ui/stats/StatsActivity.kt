package com.example.trivialfitnesstracker.ui.stats

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.trivialfitnesstracker.R

class StatsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)
        title = getString(R.string.stats)
    }
}
