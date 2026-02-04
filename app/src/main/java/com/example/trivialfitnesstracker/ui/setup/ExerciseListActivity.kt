package com.example.trivialfitnesstracker.ui.setup

import android.annotation.SuppressLint
import android.content.Intent
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
import com.example.trivialfitnesstracker.ui.history.ExerciseHistoryActivity
import com.example.trivialfitnesstracker.ui.workout.WorkoutActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import android.widget.Button

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
            onClick = { exercise -> openExerciseHistory(exercise) },
            onAddExercise = { showAddExerciseDialog() }
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
            // Don't show empty view, as we always have the footer button now
            // Or better, show empty view only if exercises is empty AND keep button visible?
            // With current adapter implementation, button is part of RecyclerView, so if exercises is empty, we have 1 item (button).
            // So recycler view is never empty.
            // If we want empty view text "No exercises" to appear when list is empty, we should position it carefully.
            // But user requirement is just about button position.
            emptyView.visibility = if (exercises.isEmpty()) View.VISIBLE else View.GONE
        }

        findViewById<FloatingActionButton>(R.id.startDayWorkoutFab).setOnClickListener {
            val intent = Intent(this, WorkoutActivity::class.java)
            intent.putExtra(WorkoutActivity.EXTRA_DAY, day.name)
            startActivity(intent)
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

    private fun openExerciseHistory(exercise: Exercise) {
        val intent = Intent(this, ExerciseHistoryActivity::class.java)
        intent.putExtra(ExerciseHistoryActivity.EXTRA_EXERCISE_NAME, exercise.name)
        intent.putExtra(ExerciseHistoryActivity.EXTRA_EXERCISE_ID, exercise.id)
        startActivity(intent)
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
        // Prevent moving items to or past the footer (last item)
        val adapter = recyclerView.adapter as ExerciseAdapter
        if (target.adapterPosition == adapter.itemCount - 1) {
            return false
        }
        
        onMove(viewHolder.adapterPosition, target.adapterPosition)
        return true
    }
    
    override fun canDropOver(recyclerView: RecyclerView, current: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
        val adapter = recyclerView.adapter as ExerciseAdapter
        // Prevent dropping over the footer
        return target.adapterPosition != adapter.itemCount - 1
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        onDrop()
    }
    
    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        val adapter = recyclerView.adapter as ExerciseAdapter
        // Disable drag for the footer
        if (viewHolder.adapterPosition == adapter.itemCount - 1) {
            return makeMovementFlags(0, 0)
        }
        return super.getMovementFlags(recyclerView, viewHolder)
    }
}

private class ExerciseAdapter(
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
    private val onClick: (Exercise) -> Unit,
    private val onAddExercise: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_ITEM = 0
        private const val VIEW_TYPE_FOOTER = 1
    }

    private val exercises: MutableList<Exercise> = mutableListOf()

    fun submitList(list: List<Exercise>) {
        exercises.clear()
        exercises.addAll(list)
        notifyDataSetChanged()
    }

    fun moveItem(from: Int, to: Int) {
        if (to >= exercises.size) return // Should be prevented by DragCallback, but safe check
        val item = exercises.removeAt(from)
        exercises.add(to, item)
        notifyItemMoved(from, to)
    }

    fun getExercises(): List<Exercise> = exercises.toList()

    class ExerciseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dragHandle: TextView = view.findViewById(R.id.dragHandle)
        val name: TextView = view.findViewById(R.id.exerciseName)
    }
    
    class FooterViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val addButton: Button = view.findViewById(R.id.addExerciseButton)
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == exercises.size) VIEW_TYPE_FOOTER else VIEW_TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_FOOTER) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_add_exercise_footer, parent, false)
            FooterViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_exercise, parent, false)
            ExerciseViewHolder(view)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is FooterViewHolder) {
            holder.addButton.setOnClickListener { onAddExercise() }
        } else if (holder is ExerciseViewHolder) {
            val exercise = exercises[position]
            holder.name.text = exercise.name
            holder.itemView.setOnClickListener { onClick(exercise) }
            holder.dragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    onStartDrag(holder)
                }
                false
            }
        }
    }

    override fun getItemCount() = exercises.size + 1
}
