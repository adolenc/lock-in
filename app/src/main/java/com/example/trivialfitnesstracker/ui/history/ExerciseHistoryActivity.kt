package com.example.trivialfitnesstracker.ui.history

import android.os.Bundle
import android.widget.TextView
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.trivialfitnesstracker.R
import com.example.trivialfitnesstracker.data.AppDatabase
import com.example.trivialfitnesstracker.data.WorkoutRepository
import com.example.trivialfitnesstracker.data.entity.ExerciseLog
import com.example.trivialfitnesstracker.data.entity.SetLog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExerciseHistoryActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_EXERCISE_NAME = "extra_exercise_name"
    }

    private lateinit var repository: WorkoutRepository
    private lateinit var exerciseName: String
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exercise_history)

        exerciseName = intent.getStringExtra(EXTRA_EXERCISE_NAME) ?: run {
            finish()
            return
        }

        title = exerciseName

        val db = AppDatabase.getDatabase(this)
        repository = WorkoutRepository(
            db.exerciseDao(),
            db.workoutSessionDao(),
            db.exerciseLogDao(),
            db.setLogDao()
        )

        recyclerView = findViewById<RecyclerView>(R.id.historyRecyclerView)
        emptyView = findViewById<TextView>(R.id.emptyView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        loadHistory()
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            // Get all exercises with this name
            val exercises = repository.getExercisesByName(exerciseName)
            if (exercises.isEmpty()) {
                emptyView.visibility = View.VISIBLE
                return@launch
            }

            // Get all logs for these exercises
            val exerciseIds = exercises.map { it.id }
            val logs = repository.getAllLogsForExercises(exerciseIds)
            
            if (logs.isEmpty()) {
                emptyView.visibility = View.VISIBLE
                recyclerView.adapter = null
                return@launch
            }

            // Load sets for each log
            val historyItems = logs.map { log ->
                val sets = repository.getSetsForExerciseLog(log.id)
                HistoryItem(log, sets)
            }

            recyclerView.adapter = HistoryAdapter(historyItems) { log ->
                AlertDialog.Builder(this@ExerciseHistoryActivity)
                    .setTitle("Delete Log")
                    .setMessage("Are you sure you want to delete this log?")
                    .setPositiveButton("Delete") { _, _ ->
                        lifecycleScope.launch {
                            repository.deleteExerciseLog(log)
                            loadHistory()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            emptyView.visibility = View.GONE
        }
    }
}

private data class HistoryItem(
    val log: ExerciseLog,
    val sets: List<SetLog>
)

private class HistoryAdapter(
    private val items: List<HistoryItem>,
    private val onDeleteClick: (ExerciseLog) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    class HistoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dateText: TextView = view.findViewById(R.id.dateText)
        val setsText: TextView = view.findViewById(R.id.setsText)
        val noteText: TextView = view.findViewById(R.id.noteText)
        val deleteButton: ImageButton = view.findViewById(R.id.deleteButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val item = items[position]
        
        holder.dateText.text = dateFormat.format(Date(item.log.completedAt))
        holder.deleteButton.setOnClickListener { onDeleteClick(item.log) }
        
        val regularSets = item.sets.filter { !it.isDropdown }
        val dropdownSets = item.sets.filter { it.isDropdown }
        
        if (item.sets.isEmpty()) {
            holder.setsText.text = "No sets logged"
        } else {
            val weight = regularSets.firstOrNull()?.weight?.let { "${it.toInt()}kg" } ?: "?"
            val reps = regularSets.joinToString(", ") { it.reps.toString() }
            val dropdown = if (dropdownSets.isNotEmpty()) 
                " + ${dropdownSets.joinToString(", ") { it.reps.toString() }}" else ""
            holder.setsText.text = "$weight × $reps$dropdown"
        }

        if (!item.log.note.isNullOrEmpty()) {
            holder.noteText.text = item.log.note
            holder.noteText.visibility = View.VISIBLE
        } else {
            holder.noteText.visibility = View.GONE
        }
    }

    override fun getItemCount() = items.size
}
