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

    fun setData(newData: Map<LocalDate, Float>, rangeStart: LocalDate, rangeEnd: LocalDate) {
        data = newData
        minDate = rangeStart
        maxDate = rangeEnd
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
        
        // Find max value
        val maxValue = (data.values.maxOrNull() ?: 10f).coerceAtLeast(10f)
        
        // Draw axes
        canvas.drawLine(leftPadding, height - padding, width - padding, height - padding, axisPaint) // X axis
        canvas.drawLine(leftPadding, padding, leftPadding, height - padding, axisPaint) // Y axis

        val daysCount = (maxDate.toEpochDay() - minDate.toEpochDay()).toInt() + 1
        if (daysCount <= 0) return

        val barWidth = graphWidth / daysCount.toFloat()
        
        // Draw bars
        for (i in 0 until daysCount) {
            val date = minDate.plusDays(i.toLong())
            val value = data[date] ?: 0f
            
            if (value > 0) {
                val barHeight = (value / maxValue) * graphHeight
                val left = leftPadding + i * barWidth
                val right = left + barWidth - (1f * density)
                val top = height - padding - barHeight
                val bottom = height - padding
                
                canvas.drawRect(left, top, right, bottom, barPaint)
            }
            
            // Draw Month labels on X axis
            if (date.dayOfMonth == 1) { 
                 canvas.drawText(date.month.name.take(3), leftPadding + i * barWidth + barWidth/2, height - padding + 12f * density, textPaint)
            }
        }
        
        // Draw max value label
        canvas.drawText(maxValue.toInt().toString(), leftPadding - 4f * density, padding + 10f * density, labelPaint)
        canvas.drawText("0", leftPadding - 4f * density, height - padding, labelPaint)
    }
}
