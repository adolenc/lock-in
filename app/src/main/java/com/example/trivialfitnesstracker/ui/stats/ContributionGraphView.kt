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
    
    // We want to show the current calendar year (Jan 1 to Dec 31)
    private val today = LocalDate.now()
    private val currentYear = today.year
    // Start from the first week of the year. 
    // We align to the Monday of the week containing Jan 1st.
    private val firstDayOfYear = LocalDate.of(currentYear, 1, 1)
    private val startDate = firstDayOfYear.with(DayOfWeek.MONDAY)
    
    // End at the last week of the year
    private val lastDayOfYear = LocalDate.of(currentYear, 12, 31)
    // We want the grid to cover the full year. The number of weeks depends on the year.
    // Roughly 53 weeks.

    private val colorEmpty = Color.parseColor("#767676") // Same as text color
    private val colorL1 = Color.parseColor("#9BE9A8")
    private val colorL2 = Color.parseColor("#40C463")
    private val colorL3 = Color.parseColor("#30A14E")
    private val colorL4 = Color.parseColor("#216E39")

    private val monthLabels = mutableListOf<Pair<String, Float>>() // Label, X offset
    private var headerHeight = 0f
    
    // Track horizontal offsets for each column to create gaps between months
    // private val columnOffsets = FloatArray(54) 
    private var totalWidth = 0f

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
        calculateLayout()
        invalidate()
    }
    
    // New structure to hold layout info
    private data class MonthColumn(val month: Int, val startDate: LocalDate, val x: Float)
    private val layoutColumns = mutableListOf<MonthColumn>()

    private fun calculateLayout() {
        monthLabels.clear()
        layoutColumns.clear()
        
        var currentX = paddingLeft.toFloat()
        
        for (month in 1..12) {
            val firstDayOfMonth = LocalDate.of(currentYear, month, 1)
            val lengthOfMonth = firstDayOfMonth.lengthOfMonth()
            val lastDayOfMonth = firstDayOfMonth.plusDays(lengthOfMonth - 1.toLong())
            
            // Add Label
            val monthName = firstDayOfMonth.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
            monthLabels.add(monthName to currentX)
            
            // Determine the Monday of the first week block for this month
            var weekStartDate = firstDayOfMonth.with(DayOfWeek.MONDAY)
            
            // Loop until we cover all days in the month
            while (!weekStartDate.isAfter(lastDayOfMonth)) {
                layoutColumns.add(MonthColumn(month, weekStartDate, currentX))
                
                currentX += boxSize + boxSpacing
                weekStartDate = weekStartDate.plusWeeks(1)
            }
            
            // Gap between months
            currentX += boxSize 
        }
        totalWidth = currentX + paddingRight
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        calculateLayout()
        val height = (7 * (boxSize + boxSpacing)).toInt() + paddingTop + paddingBottom + headerHeight.toInt()
        setMeasuredDimension(resolveSize(totalWidth.toInt(), widthMeasureSpec), resolveSize(height, heightMeasureSpec))
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
                if (dayDate.monthValue == col.month && dayDate.year == currentYear) {
                    val count = data[dayDate] ?: 0
                    paint.color = getColorForCount(count)
                    
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
