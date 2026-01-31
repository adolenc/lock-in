package com.example.trivialfitnesstracker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.example.trivialfitnesstracker.data.entity.DayOfWeek
import com.example.trivialfitnesstracker.ui.setup.ExerciseListActivity
import com.example.trivialfitnesstracker.ui.workout.WorkoutActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {

    private val workoutDays = listOf(
        DayOfWeek.MONDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.SATURDAY
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val recyclerView = findViewById<RecyclerView>(R.id.daysRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = DayAdapter(workoutDays) { day ->
            val intent = Intent(this, ExerciseListActivity::class.java)
            intent.putExtra(ExerciseListActivity.EXTRA_DAY, day.name)
            startActivity(intent)
        }

        findViewById<FloatingActionButton>(R.id.startWorkoutFab).setOnClickListener {
            startActivity(Intent(this, WorkoutActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        // Check if there's an active workout session and resume it
        val prefs = getSharedPreferences("workout_state", Context.MODE_PRIVATE)
        val savedSessionId = prefs.getLong("session_id", -1)
        if (savedSessionId != -1L) {
            startActivity(Intent(this, WorkoutActivity::class.java))
        }
    }
}

private class DayAdapter(
    private val days: List<DayOfWeek>,
    private val onClick: (DayOfWeek) -> Unit
) : RecyclerView.Adapter<DayAdapter.DayViewHolder>() {

    class DayViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dayName: TextView = view.findViewById(R.id.dayName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_day, parent, false)
        return DayViewHolder(view)
    }

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        val day = days[position]
        holder.dayName.text = day.displayName()
        holder.itemView.setOnClickListener { onClick(day) }
    }

    override fun getItemCount() = days.size
}
