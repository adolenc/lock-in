package com.example.trivialfitnesstracker.ui.setup

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.trivialfitnesstracker.R
import com.example.trivialfitnesstracker.data.AppDatabase
import com.example.trivialfitnesstracker.data.WorkoutRepository
import com.example.trivialfitnesstracker.data.entity.DayOfWeek
import com.example.trivialfitnesstracker.data.entity.Exercise
import com.google.android.material.floatingactionbutton.FloatingActionButton

class ExerciseListActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DAY = "extra_day"
    }

    private lateinit var viewModel: ExerciseListViewModel
    private lateinit var adapter: ExerciseAdapter
    private lateinit var day: DayOfWeek
    private lateinit var itemTouchHelper: ItemTouchHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exercise_list)

        day = DayOfWeek.valueOf(intent.getStringExtra(EXTRA_DAY) ?: DayOfWeek.MONDAY.name)
        title = getString(R.string.exercises_for_day, day.displayName())

        val db = AppDatabase.getDatabase(this)
        val repository = WorkoutRepository(
            db.exerciseDao(),
            db.workoutSessionDao(),
            db.exerciseLogDao(),
            db.setLogDao()
        )
        viewModel = ViewModelProvider(this, ExerciseListViewModelFactory(repository, day))
            .get(ExerciseListViewModel::class.java)

        val recyclerView = findViewById<RecyclerView>(R.id.exercisesRecyclerView)
        val emptyView = findViewById<TextView>(R.id.emptyView)

        adapter = ExerciseAdapter(
            onStartDrag = { holder -> itemTouchHelper.startDrag(holder) },
            onDelete = { exercise -> showDeleteConfirmation(exercise) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        val callback = DragCallback(
            onMove = { from, to -> adapter.moveItem(from, to) },
            onDrop = { viewModel.saveOrder(adapter.getExercises()) }
        )
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(recyclerView)

        viewModel.exercises.observe(this) { exercises ->
            adapter.submitList(exercises)
            emptyView.visibility = if (exercises.isEmpty()) View.VISIBLE else View.GONE
        }

        findViewById<FloatingActionButton>(R.id.addExerciseFab).setOnClickListener {
            showAddExerciseDialog()
        }
    }

    private fun showAddExerciseDialog() {
        val input = EditText(this)
        input.hint = getString(R.string.exercise_name)

        AlertDialog.Builder(this)
            .setTitle(R.string.add_exercise)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    viewModel.addExercise(name)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showDeleteConfirmation(exercise: Exercise) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete)
            .setMessage("Delete \"${exercise.name}\"?")
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteExercise(exercise)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}

private class DragCallback(
    private val onMove: (Int, Int) -> Unit,
    private val onDrop: () -> Unit
) : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        onMove(viewHolder.adapterPosition, target.adapterPosition)
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        onDrop()
    }
}

private class ExerciseAdapter(
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
    private val onDelete: (Exercise) -> Unit
) : RecyclerView.Adapter<ExerciseAdapter.ExerciseViewHolder>() {

    private val exercises: MutableList<Exercise> = mutableListOf()

    fun submitList(list: List<Exercise>) {
        exercises.clear()
        exercises.addAll(list)
        notifyDataSetChanged()
    }

    fun moveItem(from: Int, to: Int) {
        val item = exercises.removeAt(from)
        exercises.add(to, item)
        notifyItemMoved(from, to)
    }

    fun getExercises(): List<Exercise> = exercises.toList()

    class ExerciseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dragHandle: TextView = view.findViewById(R.id.dragHandle)
        val name: TextView = view.findViewById(R.id.exerciseName)
        val delete: TextView = view.findViewById(R.id.deleteButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExerciseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_exercise, parent, false)
        return ExerciseViewHolder(view)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: ExerciseViewHolder, position: Int) {
        val exercise = exercises[position]
        holder.name.text = exercise.name
        holder.delete.setOnClickListener { onDelete(exercise) }
        holder.dragHandle.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                onStartDrag(holder)
            }
            false
        }
    }

    override fun getItemCount() = exercises.size
}
