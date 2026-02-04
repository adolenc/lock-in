package com.example.trivialfitnesstracker.ui.stats

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import java.time.LocalDate

class ExerciseBarGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = context.resources.displayMetrics.density
    
    private val barPaint = Paint().apply { color = Color.parseColor("#40C463") }
    private val axisPaint = Paint().apply { 
        // Revert to hardcoded color for axis lines as textColorPrimary might be transparent or white which blends with background
        color = Color.parseColor("#767676") 
        strokeWidth = 2f * density
    }
    private val textPaint = Paint().apply {
        color = Color.parseColor("#767676")
        textSize = 10f * density
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    private val labelPaint = Paint().apply {
        color = Color.parseColor("#767676")
        textSize = 10f * density
        textAlign = Paint.Align.RIGHT
        isAntiAlias = true
    }

    private var data: Map<LocalDate, Float> = emptyMap()
    private var minDate: LocalDate = LocalDate.now().minusMonths(3)
    private var maxDate: LocalDate = LocalDate.now()
    private var showMissingDays: Boolean = true

    fun setData(newData: Map<LocalDate, Float>, rangeStart: LocalDate, rangeEnd: LocalDate, showMissing: Boolean = true) {
        data = newData
        minDate = rangeStart
        maxDate = rangeEnd
        showMissingDays = showMissing
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = (150 * density).toInt() // Fixed height for graph
        setMeasuredDimension(getDefaultSize(suggestedMinimumWidth, widthMeasureSpec), 
            resolveSize(desiredHeight, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val width = width.toFloat()
        val height = height.toFloat()
        val padding = 20f * density
        val leftPadding = 40f * density // Space for Y axis labels
        
        val graphWidth = width - padding - leftPadding
        val graphHeight = height - 2 * padding
        
        // Find min and max values
        val validValues = data.values.filter { it > 0f }
        val actualMin = validValues.minOrNull() ?: 0f
        val actualMax = validValues.maxOrNull() ?: 10f
        
        val minY = if (actualMin > 0) actualMin * 0.9f else 0f
        val maxY = if (actualMax > 0) actualMax * 1.1f else 10f
        val yRange = (maxY - minY).coerceAtLeast(1f) // Ensure non-zero range
        
        // Draw axes
        canvas.drawLine(leftPadding, height - padding, width - padding, height - padding, axisPaint) // X axis
        canvas.drawLine(leftPadding, padding, leftPadding, height - padding, axisPaint) // Y axis

        val sortedData = data.filter { it.value > 0 }.toSortedMap()
        
        val itemsToDraw: List<Pair<LocalDate, Float>>
        val totalBars: Int
        
        if (showMissingDays) {
            totalBars = (maxDate.toEpochDay() - minDate.toEpochDay()).toInt() + 1
            if (totalBars <= 0) return
            
            // Generate full sequence
            itemsToDraw = (0 until totalBars).map { i ->
                val date = minDate.plusDays(i.toLong())
                date to (data[date] ?: 0f)
            }
        } else {
            // Only days with data
            if (sortedData.isEmpty()) return
            totalBars = sortedData.size
            itemsToDraw = sortedData.map { it.key to it.value }
        }

        val barWidth = graphWidth / totalBars.toFloat()
        
        // Draw bars
        itemsToDraw.forEachIndexed { index, (date, value) ->
            if (value > 0) {
                val fraction = (value - minY) / yRange
                val barHeight = fraction * graphHeight
                val left = leftPadding + index * barWidth
                val right = left + barWidth - (1f * density)
                val top = height - padding - barHeight
                val bottom = height - padding
                
                canvas.drawRect(left, top, right, bottom, barPaint)
            }
            
            // Draw Month labels on X axis
            // If showing missing days, stick to 1st of month logic
            if (showMissingDays) {
                if (date.dayOfMonth == 1) { 
                     canvas.drawText(date.month.name.take(3), leftPadding + index * barWidth + barWidth/2, height - padding + 12f * density, textPaint)
                }
            } else {
                // If compressed (missing days hidden), hide labels as requested
            }
        }
        
        // Draw max/min value labels
        canvas.drawText(maxY.toInt().toString(), leftPadding - 4f * density, padding + 10f * density, labelPaint)
        canvas.drawText(minY.toInt().toString(), leftPadding - 4f * density, height - padding, labelPaint)
    }
}
