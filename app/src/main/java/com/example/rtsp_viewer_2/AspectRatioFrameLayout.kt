package com.example.rtsp_viewer_2

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/**
 * A FrameLayout that maintains a specific aspect ratio (width/height).
 * This is used to properly display video content without stretching.
 */
class AspectRatioFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var videoAspectRatio: Float = 16f / 9f // Default 16:9 aspect ratio
    private var aspectRatioMode: Int = MODE_FIT

    companion object {
        const val MODE_FIT = 0 // Fit within the view, maintaining aspect ratio
        const val MODE_FILL = 1 // Fill the view, potentially cropping content
    }

    /**
     * Set the aspect ratio that this view should maintain.
     *
     * @param widthRatio The width component of the ratio
     * @param heightRatio The height component of the ratio
     */
    fun setAspectRatio(widthRatio: Float, heightRatio: Float) {
        if (heightRatio <= 0 || widthRatio <= 0) {
            return
        }
        videoAspectRatio = widthRatio / heightRatio
        requestLayout()
    }

    /**
     * Set the aspect ratio mode.
     *
     * @param mode Either MODE_FIT or MODE_FILL
     */
    fun setAspectRatioMode(mode: Int) {
        aspectRatioMode = mode
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var newWidth: Int
        var newHeight: Int

        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        if (widthMode == MeasureSpec.EXACTLY && heightMode == MeasureSpec.EXACTLY) {
            // Both dimensions are fixed
            newWidth = widthSize
            newHeight = heightSize

            // Calculate the actual aspect ratio of the available space
            val actualRatio = newWidth.toFloat() / newHeight.toFloat()

            // Adjust dimensions to maintain aspect ratio
            if (aspectRatioMode == MODE_FIT) {
                if (actualRatio > videoAspectRatio) {
                    // Too wide, need to reduce width
                    newWidth = (newHeight * videoAspectRatio).toInt()
                } else if (actualRatio < videoAspectRatio) {
                    // Too tall, need to reduce height
                    newHeight = (newWidth / videoAspectRatio).toInt()
                }
            } else { // MODE_FILL
                if (actualRatio > videoAspectRatio) {
                    // Too wide, need to increase height
                    newHeight = (newWidth / videoAspectRatio).toInt()
                } else if (actualRatio < videoAspectRatio) {
                    // Too tall, need to increase width
                    newWidth = (newHeight * videoAspectRatio).toInt()
                }
            }
        } else if (widthMode == MeasureSpec.EXACTLY) {
            // Width is fixed, adjust height
            newWidth = widthSize
            newHeight = (newWidth / videoAspectRatio).toInt()
            if (heightMode == MeasureSpec.AT_MOST && newHeight > heightSize) {
                newHeight = heightSize
                newWidth = (newHeight * videoAspectRatio).toInt()
            }
        } else if (heightMode == MeasureSpec.EXACTLY) {
            // Height is fixed, adjust width
            newHeight = heightSize
            newWidth = (newHeight * videoAspectRatio).toInt()
            if (widthMode == MeasureSpec.AT_MOST && newWidth > widthSize) {
                newWidth = widthSize
                newHeight = (newWidth / videoAspectRatio).toInt()
            }
        } else {
            // Neither dimension is fixed
            if (widthMode == MeasureSpec.AT_MOST && heightMode == MeasureSpec.AT_MOST) {
                if (widthSize / videoAspectRatio <= heightSize) {
                    newWidth = widthSize
                    newHeight = (newWidth / videoAspectRatio).toInt()
                } else {
                    newHeight = heightSize
                    newWidth = (newHeight * videoAspectRatio).toInt()
                }
            } else if (widthMode == MeasureSpec.AT_MOST) {
                newWidth = widthSize
                newHeight = (newWidth / videoAspectRatio).toInt()
            } else if (heightMode == MeasureSpec.AT_MOST) {
                newHeight = heightSize
                newWidth = (newHeight * videoAspectRatio).toInt()
            } else {
                // Both dimensions are unspecified, use default size
                newWidth = 480
                newHeight = (newWidth / videoAspectRatio).toInt()
            }
        }

        // Measure child views with the new dimensions
        val childWidthSpec = MeasureSpec.makeMeasureSpec(newWidth, MeasureSpec.EXACTLY)
        val childHeightSpec = MeasureSpec.makeMeasureSpec(newHeight, MeasureSpec.EXACTLY)
        super.onMeasure(childWidthSpec, childHeightSpec)
    }
}
