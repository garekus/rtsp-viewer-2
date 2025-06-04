package com.example.rtsp_viewer_2

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View

/**
 * Overlay view that draws rectangles around motion detection regions
 */
class MotionOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val motionRegions = ArrayList<Rect>()
    private val paint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }
    
    // Source dimensions
    private var sourceWidth = 1
    private var sourceHeight = 1
    
    // Video content dimensions and position (accounting for aspect ratio)
    private var contentRect = Rect(0, 0, 1, 1)
    
    /**
     * Update the motion regions to be displayed
     * @param regions List of rectangles representing motion regions
     * @param sourceWidth Width of the source bitmap used for detection
     * @param sourceHeight Height of the source bitmap used for detection
     */
    fun updateMotionRegions(regions: List<Rect>, sourceWidth: Int, sourceHeight: Int) {
        motionRegions.clear()
        motionRegions.addAll(regions)
        
        // Store source dimensions
        this.sourceWidth = sourceWidth
        this.sourceHeight = sourceHeight
        
        // Request redraw
        invalidate()
    }
    
    /**
     * Clear all motion regions
     */
    fun clearMotionRegions() {
        motionRegions.clear()
        invalidate()
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateContentRect()
    }
    
    /**
     * Calculate the actual content rectangle based on aspect ratio
     */
    private fun updateContentRect() {
        val viewWidth = width
        val viewHeight = height
        
        if (viewWidth <= 0 || viewHeight <= 0 || sourceWidth <= 0 || sourceHeight <= 0) {
            contentRect.set(0, 0, viewWidth, viewHeight)
            return
        }
        
        // Calculate aspect ratios
        val sourceAspectRatio = sourceWidth.toFloat() / sourceHeight.toFloat()
        val viewAspectRatio = viewWidth.toFloat() / viewHeight.toFloat()
        
        // Calculate content dimensions that maintain the source aspect ratio
        if (viewAspectRatio > sourceAspectRatio) {
            // View is wider than content - center horizontally
            val contentWidth = (viewHeight * sourceAspectRatio).toInt()
            val horizontalPadding = (viewWidth - contentWidth) / 2
            contentRect.set(horizontalPadding, 0, viewWidth - horizontalPadding, viewHeight)
        } else {
            // View is taller than content - center vertically
            val contentHeight = (viewWidth / sourceAspectRatio).toInt()
            val verticalPadding = (viewHeight - contentHeight) / 2
            contentRect.set(0, verticalPadding, viewWidth, viewHeight - verticalPadding)
        }
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (motionRegions.isEmpty() || sourceWidth <= 0 || sourceHeight <= 0) {
            return
        }
        
        // Ensure content rect is updated
        if (contentRect.width() <= 1 || contentRect.height() <= 1) {
            updateContentRect()
        }
        
        // Calculate scale factors from source to content area
        val scaleX = contentRect.width().toFloat() / sourceWidth
        val scaleY = contentRect.height().toFloat() / sourceHeight
        
        // Draw each motion region
        for (region in motionRegions) {
            // Scale the rectangle to match the content dimensions and position
            val scaledRect = Rect(
                contentRect.left + (region.left * scaleX).toInt(),
                contentRect.top + (region.top * scaleY).toInt(),
                contentRect.left + (region.right * scaleX).toInt(),
                contentRect.top + (region.bottom * scaleY).toInt()
            )
            canvas.drawRect(scaledRect, paint)
        }
    }
}
