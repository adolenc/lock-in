package com.example.trivialfitnesstracker.ui.workout

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.NumberPicker
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.trivialfitnesstracker.R
import com.example.trivialfitnesstracker.data.AppDatabase
import com.example.trivialfitnesstracker.data.WorkoutRepository
import com.example.trivialfitnesstracker.data.entity.SetLog

class WorkoutFragment : Fragment() {

    companion object {
        private const val ARG_SESSION_ID = "session_id"
        private const val ARG_EXERCISE_ID = "exercise_id"

        fun newInstance(sessionId: Long, exerciseId: Long): WorkoutFragment {
            return WorkoutFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_SESSION_ID, sessionId)
                    putLong(ARG_EXERCISE_ID, exerciseId)
                }
            }
        }
    }

    private lateinit var viewModel: WorkoutPageViewModel
    private lateinit var exerciseName: TextView
    private lateinit var historyText: TextView
    private lateinit var weightPicker: NumberPicker
    private lateinit var repsPicker: NumberPicker
    private lateinit var todaySetsText: TextView
    private lateinit var undoButton: Button
    private lateinit var noteText: TextView
    private lateinit var addNoteButton: Button
    private lateinit var weightColorIndicator: View
    private lateinit var variationSelector: TextView

    private val weightValues: List<Float?> = listOf(
        null,                    // No weight
        7f, 8f, 9f,              // Black
        11.5f, 12.5f, 13.5f,     // White
        16f, 17f, 18f,           // Purple
        20.5f, 21.5f, 22.5f      // Green
    )
    private val weightDisplayValues = weightValues.map {
        if (it == null) "/" 
        else if (it == it.toInt().toFloat()) it.toInt().toString() 
        else String.format("%.1f", it) 
    }.toTypedArray()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_workout_page, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sessionId = arguments?.getLong(ARG_SESSION_ID) ?: return
        val exerciseId = arguments?.getLong(ARG_EXERCISE_ID) ?: return

        val db = AppDatabase.getDatabase(requireContext())
        val repository = WorkoutRepository(
            db.exerciseDao(),
            db.workoutSessionDao(),
            db.exerciseLogDao(),
            db.setLogDao(),
            db.exerciseVariationDao()
        )

        viewModel = ViewModelProvider(
            this,
            WorkoutPageViewModelFactory(repository, sessionId, exerciseId)
        ).get(WorkoutPageViewModel::class.java)

        bindViews(view)
        setupPickers()
        setupClickListeners()
        setupObservers()
    }

    private fun bindViews(view: View) {
        exerciseName = view.findViewById(R.id.exerciseName)
        historyText = view.findViewById(R.id.historyText)
        weightPicker = view.findViewById(R.id.weightPicker)
        repsPicker = view.findViewById(R.id.repsPicker)
        todaySetsText = view.findViewById(R.id.todaySetsText)
        undoButton = view.findViewById(R.id.undoButton)
        noteText = view.findViewById(R.id.noteText)
        addNoteButton = view.findViewById(R.id.addNoteButton)
        weightColorIndicator = view.findViewById(R.id.weightColorIndicator)
        variationSelector = view.findViewById(R.id.variationSelector)
    }

    private fun setupPickers() {
        weightPicker.minValue = 0
        weightPicker.maxValue = weightValues.size - 1
        weightPicker.displayedValues = weightDisplayValues
        weightPicker.wrapSelectorWheel = false
        weightPicker.value = 0
        weightPicker.setOnValueChangedListener { _, _, newVal ->
            updateWeightColor(weightValues[newVal])
            viewModel.updateWeight(weightValues[newVal])
        }
        updateWeightColor(weightValues[weightPicker.value])

        repsPicker.minValue = 1
        repsPicker.maxValue = 200
        repsPicker.wrapSelectorWheel = false
        repsPicker.value = 10
    }

    private fun setupClickListeners() {
        view?.findViewById<Button>(R.id.logSetButton)?.setOnClickListener {
            val weight = weightValues[weightPicker.value]
            val reps = repsPicker.value
            viewModel.logSet(weight, reps)
            (activity as? WorkoutActivity)?.startRestTimer()
        }

        view?.findViewById<Button>(R.id.logDropdownButton)?.setOnClickListener {
            val reps = repsPicker.value
            viewModel.logDropdown(reps)
            (activity as? WorkoutActivity)?.startRestTimer()
        }

        undoButton.setOnClickListener {
            viewModel.undoLastSet()
        }

        addNoteButton.setOnClickListener {
            showNoteDialog()
        }

        noteText.setOnClickListener {
            showNoteDialog()
        }

        variationSelector.setOnClickListener {
            showVariationDialog()
        }
    }

    private fun setupObservers() {
        viewModel.exercise.observe(viewLifecycleOwner) { exercise ->
            exerciseName.text = exercise.name
        }

        viewModel.selectedVariation.observe(viewLifecycleOwner) { variation ->
            val name = variation?.name ?: "/"
            variationSelector.text = getString(R.string.variation_prefix, name)
        }

        viewModel.history.observe(viewLifecycleOwner) { historyList ->
            val lastHistory = historyList.firstOrNull()
            if (lastHistory == null) {
                historyText.text = ""
                historyText.visibility = View.GONE
            } else {
                val regularSets = lastHistory.sets.filter { !it.isDropdown }
                val dropdownSets = lastHistory.sets.filter { it.isDropdown }
                val weightVal = regularSets.firstOrNull()?.weight
                val weight = weightVal?.let {
                    if (it == it.toInt().toFloat()) "${it.toInt()}kg" else "${it}kg"
                } ?: "/"
                val reps = regularSets.joinToString(", ") { it.reps.toString() }
                val dropdown = if (dropdownSets.isNotEmpty())
                    " + ${dropdownSets.joinToString(", ") { it.reps.toString() }}" else ""
                val variation = if (!lastHistory.variation.isNullOrEmpty()) "${lastHistory.variation} " else ""
                historyText.text = "$variation$weight × $reps$dropdown"
                historyText.visibility = View.VISIBLE
            }
        }

        viewModel.lastWeight.observe(viewLifecycleOwner) { weight ->
            // If weight is null, it might mean "no weight" (/) or no history.
            // In both cases, index 0 (which is null) is appropriate.
            val index = if (weight == null) 0 else {
                val i = weightValues.indexOfFirst { it != null && it >= weight }
                if (i >= 0) i else 0
            }
            
            if (index >= 0 && index < weightValues.size) {
                weightPicker.value = index
                updateWeightColor(weightValues[index])
            }
        }

        viewModel.lastReps.observe(viewLifecycleOwner) { reps ->
            if (reps != null) {
                repsPicker.value = reps.coerceIn(1, 200)
            }
        }

        viewModel.todaySets.observe(viewLifecycleOwner) { sets: List<SetLog> ->
            if (sets.isEmpty()) {
                todaySetsText.text = getString(R.string.no_sets_yet)
            } else {
                val regularSets = sets.filter { !it.isDropdown }
                val dropdownSets = sets.filter { it.isDropdown }
                val reps = regularSets.joinToString(", ") { it.reps.toString() }
                val dropdown = if (dropdownSets.isNotEmpty())
                    " + ${dropdownSets.joinToString(", ") { it.reps.toString() }}" else ""
                todaySetsText.text = getString(R.string.sets_today, "$reps$dropdown")
            }
            // Update Activity progress dots via shared ViewModel or Callback?
            // For now, let Activity handle global progress via its own VM
            (activity as? WorkoutActivity)?.refreshProgress()
        }

        viewModel.canUndo.observe(viewLifecycleOwner) { canUndo ->
            undoButton.isEnabled = canUndo
            undoButton.alpha = if (canUndo) 1f else 0.5f
        }

        viewModel.currentNote.observe(viewLifecycleOwner) { note ->
            if (note.isNullOrEmpty()) {
                noteText.visibility = View.GONE
                addNoteButton.visibility = View.VISIBLE
            } else {
                noteText.visibility = View.VISIBLE
                noteText.text = note
                addNoteButton.visibility = View.GONE
            }
        }
    }

    private fun updateWeightColor(weight: Float?) {
        val color = if (weight == null) {
            Color.TRANSPARENT
        } else {
            when (weight) {
                in 7f..9f -> Color.BLACK
                in 11.5f..13.5f -> Color.WHITE
                in 16f..18f -> Color.parseColor("#9C27B0") // Purple
                in 20.5f..22.5f -> Color.parseColor("#4CAF50") // Green
                else -> Color.TRANSPARENT
            }
        }
        weightColorIndicator.setBackgroundColor(color)
    }

    private fun showVariationDialog() {
        val variations = viewModel.variations.value ?: emptyList()
        val options = mutableListOf<String>()
        options.add(getString(R.string.variation_none_option))
        options.addAll(variations.map { it.name })
        options.add(getString(R.string.add_variation)) // Last option is to add new

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.select_variation)
            .setItems(options.toTypedArray()) { _, which ->
                when (which) {
                    0 -> viewModel.setVariation(null) // None
                    options.lastIndex -> showAddVariationDialog() // Add new
                    else -> viewModel.setVariation(variations[which - 1]) // Select existing
                }
            }
            .show()
    }

    private fun showAddVariationDialog() {
        val input = EditText(requireContext())
        input.hint = getString(R.string.new_variation_hint)

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.add_variation)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    viewModel.addVariation(name)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showNoteDialog() {
        val input = EditText(requireContext())
        input.hint = getString(R.string.note_hint)
        input.setText(viewModel.currentNote.value ?: "")

        AlertDialog.Builder(requireContext())
            .setTitle(if (viewModel.currentNote.value.isNullOrEmpty()) R.string.add_note else R.string.edit_note)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                viewModel.setNote(input.text.toString())
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
