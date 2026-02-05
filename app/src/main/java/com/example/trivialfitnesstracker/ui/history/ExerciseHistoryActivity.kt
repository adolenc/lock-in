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
        const val EXTRA_EXERCISE_ID = "extra_exercise_id"
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
            db.setLogDao(),
            db.exerciseVariationDao()
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

            // Load sets and variations for each log
            // We'll fetch variations one by one or bulk. Since we don't have bulk get, one by one is fine for now.
            val historyItems = logs.map { log ->
                val sets = repository.getSetsForExerciseLog(log.id)
                val variation = if (log.variationId != null) repository.getVariationById(log.variationId)?.name else null
                HistoryItem(log, sets, variation)
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

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.menu_exercise_history, menu)
        
        // Tint delete icon red
        val deleteItem = menu.findItem(R.id.action_delete_exercise)
        deleteItem?.icon?.setTint(android.graphics.Color.parseColor("#CC0000"))
        
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_rename_exercise -> {
                showRenameExerciseDialog()
                true
            }
            R.id.action_delete_exercise -> {
                showDeleteExerciseConfirmation()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showRenameExerciseDialog() {
        val exerciseId = intent.getLongExtra(EXTRA_EXERCISE_ID, -1)
        
        lifecycleScope.launch {
            val exercise = if (exerciseId != -1L) repository.getExerciseById(exerciseId) else null
            
            // If viewing all history (no specific exercise ID), we can't easily rename just "one" context.
            // But we can rename ALL exercises with that name.
            
            val input = android.widget.EditText(this@ExerciseHistoryActivity).apply {
                setText(exerciseName)
                setSelection(exerciseName.length)
            }
            
            AlertDialog.Builder(this@ExerciseHistoryActivity)
                .setTitle("Rename Exercise")
                .setView(input)
                .setPositiveButton("Save") { _, _ ->
                    val newName = input.text.toString().trim()
                    if (newName.isNotEmpty() && newName != exerciseName) {
                        lifecycleScope.launch {
                            if (exercise != null) {
                                // Rename specific exercise
                                val updated = exercise.copy(name = newName)
                                repository.updateExercise(updated)
                            } else {
                                // Rename ALL exercises with this name
                                val exercises = repository.getExercisesByName(exerciseName)
                                exercises.forEach { 
                                    repository.updateExercise(it.copy(name = newName))
                                }
                            }
                            
                            // Update local state and UI
                            exerciseName = newName
                            title = exerciseName
                            // Reload history? Actually history is loaded by name, so if we change name
                            // we need to make sure we load history for new name if we stay on screen.
                            // But wait, history loading uses `getExercisesByName(exerciseName)`.
                            // So if we update the DB, we just need to refresh.
                            
                            loadHistory()
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun showDeleteExerciseConfirmation() {
        val exerciseId = intent.getLongExtra(EXTRA_EXERCISE_ID, -1)

        lifecycleScope.launch {
            val exercise = if (exerciseId != -1L) repository.getExerciseById(exerciseId) else null

            val message = if (exercise != null) {
                // Try to format day name nicely
                val dayName = try {
                    exercise.dayOfWeek.displayName()
                } catch (e: Exception) {
                    exercise.dayOfWeek.name
                }
                "Delete \"$exerciseName\" from $dayName?"
            } else {
                "Delete \"$exerciseName\" from all days?"
            }

            AlertDialog.Builder(this@ExerciseHistoryActivity)
                .setTitle(R.string.delete)
                .setMessage(message)
                .setPositiveButton(R.string.delete) { _, _ ->
                    lifecycleScope.launch {
                        if (exercise != null) {
                            repository.deleteExercise(exercise)
                        } else {
                            val exercises = repository.getExercisesByName(exerciseName)
                            exercises.forEach { repository.deleteExercise(it) }
                        }
                        finish()
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

private data class HistoryItem(
    val log: ExerciseLog,
    val sets: List<SetLog>,
    val variationName: String?
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
            val weight = regularSets.firstOrNull()?.weight?.let { "${it.toInt()}kg" } ?: "/"
            val reps = regularSets.joinToString(", ") { it.reps.toString() }
            val dropdown = if (dropdownSets.isNotEmpty()) 
                " + ${dropdownSets.joinToString(", ") { it.reps.toString() }}" else ""
            
            val variationPrefix = if (!item.variationName.isNullOrEmpty()) "${item.variationName} " else ""
            holder.setsText.text = "$variationPrefix$weight × $reps$dropdown"
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
