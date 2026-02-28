package com.example.trivialfitnesstracker.ui.stats

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.MotionEvent
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.floor

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
    private val valuePaint = Paint().apply {
        color = Color.parseColor("#40C463") // Green, same as bars
        textSize = 12f * density
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private var data: Map<LocalDate, Float> = emptyMap()
    private var minDate: LocalDate = LocalDate.now().minusMonths(3)
    private var maxDate: LocalDate = LocalDate.now()
    private var showMissingDays: Boolean = false
    
    private var itemsToDraw: List<Pair<LocalDate, Float>> = emptyList()
    private var selectedIndex: Int = -1

    fun setData(newData: Map<LocalDate, Float>, rangeStart: LocalDate, rangeEnd: LocalDate, showMissing: Boolean = false) {
        data = newData
        minDate = rangeStart
        maxDate = rangeEnd
        showMissingDays = showMissing
        prepareData()
        selectedIndex = -1
        invalidate()
    }
    
    private fun prepareData() {
        val sortedData = data.filter { it.value > 0 }.toSortedMap()
        
        if (showMissingDays) {
            val totalBars = (maxDate.toEpochDay() - minDate.toEpochDay()).toInt() + 1
            if (totalBars <= 0) {
                itemsToDraw = emptyList()
                return
            }
            
            // Generate full sequence
            itemsToDraw = (0 until totalBars).map { i ->
                val date = minDate.plusDays(i.toLong())
                date to (data[date] ?: 0f)
            }
        } else {
            // Only days with data
            if (sortedData.isEmpty()) {
                itemsToDraw = emptyList()
                return
            }
            itemsToDraw = sortedData.map { it.key to it.value }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = (150 * density).toInt() // Fixed height for graph
        setMeasuredDimension(getDefaultSize(suggestedMinimumWidth, widthMeasureSpec), 
            resolveSize(desiredHeight, heightMeasureSpec))
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val width = width.toFloat()
            val height = height.toFloat()
            val padding = 20f * density
            val leftPadding = 40f * density
            
            val graphWidth = width - padding - leftPadding
            val totalBars = itemsToDraw.size
            if (totalBars == 0) return false
            
            val barWidth = graphWidth / totalBars.toFloat()
            val x = event.x
            val y = event.y
            
            // Check bounds (roughly)
            if (x >= leftPadding && x <= width - padding && y >= padding && y <= height - padding) {
                val index = ((x - leftPadding) / barWidth).toInt()
                if (index in itemsToDraw.indices) {
                    if (selectedIndex == index) {
                        selectedIndex = -1 // Toggle off
                    } else {
                        selectedIndex = index
                    }
                    performClick()
                    invalidate()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
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
        
        var minY = floor(if (actualMin > 0) actualMin * 0.9f else 0f)
        var maxY = ceil(if (actualMax > 0) actualMax * 1.1f else 10f)
        var yRange = (maxY - minY).coerceAtLeast(1f)
        
        // Ensure non-zero range if min equals max
        if (yRange <= 1f && maxY == minY) {
             maxY += 1f
             yRange = 1f
        }
        
        // Draw axes
        canvas.drawLine(leftPadding, height - padding, width - padding, height - padding, axisPaint) // X axis
        canvas.drawLine(leftPadding, padding, leftPadding, height - padding, axisPaint) // Y axis

        if (itemsToDraw.isEmpty()) return
        val totalBars = itemsToDraw.size
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
                
                // Draw value if selected
                if (index == selectedIndex) {
                    val text = if (value % 1.0f == 0f) value.toInt().toString() else String.format("%.1f", value)
                    canvas.drawText(text, left + barWidth/2, top - 4f * density, valuePaint)
                }
            }
            
            // Draw Month labels on X axis
            // If showing missing days, stick to 1st of month logic
            if (showMissingDays) {
                if (date.dayOfMonth == 1) { 
                     val monthName = date.month.name.take(3)
                     val formattedMonth = monthName.first().uppercase() + monthName.drop(1).lowercase()
                     canvas.drawText(formattedMonth, leftPadding + index * barWidth + barWidth/2, height - padding + 12f * density, textPaint)
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
