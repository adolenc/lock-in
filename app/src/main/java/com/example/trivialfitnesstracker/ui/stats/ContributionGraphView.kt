package com.example.trivialfitnesstracker.ui.stats

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import java.time.DayOfWeek
import java.time.LocalDate

class ContributionGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var boxSize = 0f
    private var boxSpacing = 0f
    private val paint = Paint()
    private val textPaint = Paint()
    private var data: Map<LocalDate, Int> = emptyMap()
    
    // Dynamic range logic
    private val today = LocalDate.now()
    private var startDateRange = today
    private var endDateRange = today

    private val colorEmpty = Color.parseColor("#767676") // Same as text color
    private val colorL1 = Color.parseColor("#9BE9A8")
    private val colorL2 = Color.parseColor("#40C463")
    private val colorL3 = Color.parseColor("#30A14E")
    private val colorL4 = Color.parseColor("#216E39")

    private val monthLabels = mutableListOf<Pair<String, Float>>() // Label, X offset
    private var headerHeight = 0f
    
    // Track layout
    private var totalWidth = 0f
    private var initialScrollX = 0

    init {
        val density = context.resources.displayMetrics.density
        boxSize = 12f * density
        boxSpacing = 4f * density
        headerHeight = 20f * density

        textPaint.color = Color.parseColor("#767676")
        textPaint.textSize = 10f * density
        textPaint.isAntiAlias = true
    }

    fun setData(newData: Map<LocalDate, Int>) {
        data = newData
        
        // Determine range
        // 1 month prior to start of workout (min date in data)
        // 1 month after last day of recorded workout (max date in data)
        // Default to today if no data
        
        val validDates = data.keys.filter { data[it] ?: 0 > 0 }
        
        val minDataDate = validDates.minOrNull() ?: today
        val maxDataDate = validDates.maxOrNull() ?: today
        
        // "1 month prior" logic: Start of that month, minus 1 month
        startDateRange = minDataDate.minusMonths(1).withDayOfMonth(1)
        
        // "1 month after" logic: End of that month, plus 1 month
        endDateRange = maxDataDate.plusMonths(1).withDayOfMonth(maxDataDate.plusMonths(1).lengthOfMonth())
        
        calculateLayout()
        requestLayout()
        invalidate()
    }
    
    // New structure to hold layout info
    private data class MonthColumn(val year: Int, val month: Int, val startDate: LocalDate, val x: Float)
    private val layoutColumns = mutableListOf<MonthColumn>()

    private fun calculateLayout() {
        monthLabels.clear()
        layoutColumns.clear()
        
        var currentX = paddingLeft.toFloat()
        
        // Iterate month by month from startDateRange to endDateRange
        var iterDate = startDateRange.withDayOfMonth(1)
        val limitDate = endDateRange.plusMonths(1).withDayOfMonth(1) // Exclusive upper bound for iteration logic
        
        while (iterDate.isBefore(limitDate)) {
            val currentYear = iterDate.year
            val currentMonth = iterDate.monthValue
            val lengthOfMonth = iterDate.lengthOfMonth()
            val lastDayOfMonth = iterDate.plusDays(lengthOfMonth - 1.toLong())
            
            // Add Label
            // Jan should be "Jan YEAR", others just "Feb", "Mar" etc.
            val monthNameShort = iterDate.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
            val label = if (currentMonth == 1) "$monthNameShort $currentYear" else monthNameShort
            
            monthLabels.add(label to currentX)
            
            // Determine the Monday of the first week block for this month
            // Similar to GitHub: Columns represent weeks. 
            // A column belongs to a month if the majority of days in that week are in that month?
            // OR simpler: As implemented before, we strictly separate months visually.
            // Let's stick to the previous implementation style: columns strictly for days of this month.
            
            var weekStartDate = iterDate.with(DayOfWeek.MONDAY)
            // Correction: weekStartDate might be in previous month. 
            // If we strictly draw days belonging to this month, weekStartDate just serves as anchor for the column.
            
            // Loop until we cover all days in the month
            while (!weekStartDate.isAfter(lastDayOfMonth)) {
                // Check if this week column has ANY days in the current month
                val weekEndDate = weekStartDate.plusDays(6)
                // Overlap check: [weekStartDate, weekEndDate] overlaps [iterDate, lastDayOfMonth]
                if (!weekEndDate.isBefore(iterDate) && !weekStartDate.isAfter(lastDayOfMonth)) {
                     layoutColumns.add(MonthColumn(currentYear, currentMonth, weekStartDate, currentX))
                     currentX += boxSize + boxSpacing
                }
                weekStartDate = weekStartDate.plusWeeks(1)
            }
            
            // Gap between months
            currentX += boxSize 
            
            iterDate = iterDate.plusMonths(1)
        }
        totalWidth = currentX + paddingRight
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // calculateLayout() // Already called in setData or on init? Better to call here if not data dependent purely?
        // But layout depends on data range.
        val height = (7 * (boxSize + boxSpacing)).toInt() + paddingTop + paddingBottom + headerHeight.toInt()
        setMeasuredDimension(resolveSize(totalWidth.toInt(), widthMeasureSpec), resolveSize(height, heightMeasureSpec))
        
        // Calculate scroll position to center today
        // Find the column that contains today
        val todayCol = layoutColumns.find { 
             val colEnd = it.startDate.plusDays(6)
             !today.isBefore(it.startDate) && !today.isAfter(colEnd) && today.monthValue == it.month
        }
        
        if (todayCol != null) {
            val screenWidth = MeasureSpec.getSize(widthMeasureSpec)
            initialScrollX = (todayCol.x - screenWidth / 2 + boxSize / 2).toInt()
        }
    }
    
    // Helper to expose desired scroll position
    fun getInitialScrollX(): Int {
        return initialScrollX
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Draw Month Labels
        for ((label, x) in monthLabels) {
            canvas.drawText(label, x, paddingTop + textPaint.textSize, textPaint)
        }
        
        for (col in layoutColumns) {
            val left = col.x
            var dayDate = col.startDate
            
            for (row in 0 until 7) {
                // Check if this day actually belongs to the column's month
                if (dayDate.monthValue == col.month && dayDate.year == col.year) {
                    val count = data[dayDate] ?: 0
                    // If this is today and no contributions, mark it white to highlight current day
                    paint.color = if (dayDate == today && count == 0) Color.WHITE else getColorForCount(count)
                    
                    val top = paddingTop + headerHeight + row * (boxSize + boxSpacing)
                    val radius = 4f
                    canvas.drawRoundRect(left, top, left + boxSize, top + boxSize, radius, radius, paint)
                }
                
                dayDate = dayDate.plusDays(1)
            }
        }
    }

    private fun getColorForCount(count: Int): Int {
        return when {
            count == 0 -> colorEmpty
            count <= 3 -> colorL1
            count <= 6 -> colorL2
            count <= 10 -> colorL3
            else -> colorL4
        }
    }
}
