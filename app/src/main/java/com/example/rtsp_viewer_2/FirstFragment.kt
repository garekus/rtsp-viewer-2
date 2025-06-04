package com.example.rtsp_viewer_2

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.preference.PreferenceManager
import com.alexvas.rtsp.codec.VideoDecodeThread
import com.alexvas.rtsp.widget.RtspStatusListener
import com.alexvas.rtsp.widget.RtspSurfaceView
import com.example.rtsp_viewer_2.databinding.FragmentFirstBinding


/**
 * A Fragment that displays an RTSP stream from a camera.
 * Uses RtspSurfaceView for better compatibility and performance.
 */
class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    
    private val TAG = "FirstFragment"
    
    // Motion Detection related variables
    private lateinit var motionDetectionHandler: Handler
    private var previousBitmap: Bitmap? = null
    private var isMotionDetectionActive = false
    
    // Advanced motion detection variables
    private val motionHistoryMap = HashMap<String, Int>() // Tracks motion persistence in regions
    private var motionRegions = ArrayList<android.graphics.Rect>() // Current motion regions

    private val motionDetectionRunnable = object : Runnable {
        override fun run() {
            val surfaceView = _binding?.rtspSurfaceView ?: return
            val outerRunnable = this // For rescheduling

            // Basic readiness checks
            if (!isMotionDetectionActive || !isAdded || view == null || _binding == null || 
                !surfaceView.isAttachedToWindow || surfaceView.visibility != View.VISIBLE ||
                !surfaceView.holder.surface.isValid || surfaceView.width <= 0 || surfaceView.height <= 0) {
                Log.d(TAG, "Motion detection: Pre-conditions not met. Rescheduling.")
                if (isMotionDetectionActive && _binding != null) { // Check _binding again before posting
                    motionDetectionHandler.postDelayed(outerRunnable, MOTION_DETECTION_INTERVAL_MS)
                }
                return
            }

            // For the first frame capture, we'll rely on the surface being valid and a delay
            // instead of checking videoFramesRendered which is not available
            if (previousBitmap == null) {
                // If this is our first attempt, we'll just check if the surface is ready
                if (!surfaceView.holder.surface.isValid) {
                    Log.d(TAG, "Motion detection: Surface not yet valid for first frame. Rescheduling check.")
                    if (isMotionDetectionActive && _binding != null) {
                        motionDetectionHandler.postDelayed(outerRunnable, MOTION_DETECTION_INTERVAL_MS)
                    }
                    return
                }
            }

            Log.d(TAG, "Attempting PixelCopy. SurfaceView dimensions: ${surfaceView.width}x${surfaceView.height}")
            val bitmap: Bitmap
            try {
                bitmap = Bitmap.createBitmap(surfaceView.width, surfaceView.height, Bitmap.Config.ARGB_8888)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create Bitmap for PixelCopy", e)
                if (isMotionDetectionActive && _binding != null) {
                    motionDetectionHandler.postDelayed(outerRunnable, MOTION_DETECTION_INTERVAL_MS)
                }
                return
            }

            PixelCopy.request(surfaceView, bitmap, {
                copyResult ->
                if (copyResult == PixelCopy.SUCCESS) {
                    processFrame(bitmap) // processFrame is responsible for recycling this bitmap if it stores it
                } else {
                    Log.e(TAG, "PixelCopy failed: $copyResult. Bitmap isMutable: ${bitmap.isMutable}, recycled: ${bitmap.isRecycled}, Config: ${bitmap.config}, Dims: ${bitmap.width}x${bitmap.height}")
                    if (!bitmap.isRecycled) {
                        bitmap.recycle()
                    }
                }
                // Schedule next check if still active, regardless of PixelCopy success/failure for this attempt
                if (isMotionDetectionActive && _binding != null && isAdded) { // isAdded check before scheduling next
                    motionDetectionHandler.postDelayed(outerRunnable, MOTION_DETECTION_INTERVAL_MS)
                }
            }, motionDetectionHandler)
        }
    }

    companion object {
        private const val MOTION_DETECTION_INTERVAL_MS = 300L
        private const val DOWNSCALE_FACTOR = 0.5f // Process at 1/4 resolution
        private const val PIXEL_DIFFERENCE_THRESHOLD = 30 // Grayscale difference
        private const val MOTION_AREA_THRESHOLD_PERCENT = 0.4 // 0.2% of pixels must change
        private const val INITIAL_FRAME_CAPTURE_DELAY_MS = 1000L // Delay before first PixelCopy
        
        // Advanced motion detection parameters
        private const val REGION_SIZE_THRESHOLD = 10 // Minimum width/height of a motion region
        private const val MOTION_PERSISTENCE_THRESHOLD = 2 // Number of consecutive detections needed
        private const val GRID_CELLS_X = 8 // Number of grid cells horizontally
        private const val GRID_CELLS_Y = 6 // Number of grid cells vertically
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        motionDetectionHandler = Handler(Looper.getMainLooper())
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Configure main RtspSurfaceView
        configureRtspView(binding.rtspSurfaceView, true) // Main view with audio
        
        // Configure PiP RtspSurfaceView
        configureRtspView(binding.pipRtspSurfaceView, false) // PiP view without audio
        
        // Set default aspect ratio (16:9) until we get the actual video dimensions
        binding.aspectRatioLayout.setAspectRatio(16f, 9f)
        binding.pipAspectRatioLayout.setAspectRatio(16f, 9f)
        
        // Set the aspect ratio mode from preferences
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val aspectRatioMode = sharedPreferences.getString("aspect_ratio_mode", "0")?.toIntOrNull() ?: 0
        binding.aspectRatioLayout.setAspectRatioMode(aspectRatioMode)
        binding.pipAspectRatioLayout.setAspectRatioMode(aspectRatioMode)
    }
    
    /**
     * Configure an RTSP view with common settings
     */
    private fun configureRtspView(rtspView: RtspSurfaceView, enableAudio: Boolean) {
        rtspView.debug = true // Enable debug logging
        rtspView.videoDecoderType = VideoDecodeThread.DecoderType.HARDWARE // Use hardware decoder
    }
    
    private fun connectToRtspStream() {
        try {
            // Get the shared preferences
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
            
            // Connect the main RTSP view (Stream 1)
            connectMainRtspView(sharedPreferences)
            
            // Connect the PiP RTSP view (Stream 2)
            connectPipRtspView(sharedPreferences)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to RTSP streams", e)
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Connect the main RTSP view using Stream 1 settings
     */
    private fun connectMainRtspView(sharedPreferences: android.content.SharedPreferences) {
        // Get Stream 1 settings
        val rtspUrl = sharedPreferences.getString("rtsp_url_1", "") ?: ""
        val username = sharedPreferences.getString("rtsp_username_1", "") ?: ""
        val password = sharedPreferences.getString("rtsp_password_1", "") ?: ""
        
        if (rtspUrl.isEmpty()) {
            Toast.makeText(context, "Please set a valid RTSP URL for Stream 1 in Settings", Toast.LENGTH_SHORT).show()
            return
        }
        
        Log.d(TAG, "Connecting main view to RTSP Stream 1: $rtspUrl")
        
        // Parse the URL
        val uri = Uri.parse(rtspUrl)
        
        // Connect the main RTSP view with audio enabled
        connectRtspView(binding.rtspSurfaceView, uri, username, password, true)
    }
    
    /**
     * Connect the PiP RTSP view using Stream 2 settings
     */
    private fun connectPipRtspView(sharedPreferences: android.content.SharedPreferences) {
        // Get Stream 2 settings
        val rtspUrl = sharedPreferences.getString("rtsp_url_2", "") ?: ""
        val username = sharedPreferences.getString("rtsp_username_2", "") ?: ""
        val password = sharedPreferences.getString("rtsp_password_2", "") ?: ""
        
        if (rtspUrl.isEmpty()) {
            // Don't show a toast for this, just log it
            Log.d(TAG, "No RTSP URL set for Stream 2, PiP view will not be connected")
            return
        }
        
        Log.d(TAG, "Connecting PiP view to RTSP Stream 2: $rtspUrl")
        
        // Parse the URL
        val uri = Uri.parse(rtspUrl)
        
        // Connect the PiP RTSP view without audio
        connectRtspView(binding.pipRtspSurfaceView, uri, username, password, false)
    }
    
    /**
     * Connect an RTSP view to the stream
     */
    private fun connectRtspView(rtspView: RtspSurfaceView, uri: Uri, username: String, password: String, enableAudio: Boolean) {
        // Stop any existing stream
        rtspView.stop()
        
        // Initialize the RTSP SurfaceView with the URI, username, and password
        rtspView.init(uri, username, password, "RTSP Viewer App")
        
        // Start the stream
        rtspView.start(
            requestVideo = true,
            requestAudio = enableAudio, // Only enable audio for the main view
            requestApplication = false
        )
        
        // Only show toast for the main view to avoid duplicate messages
        if (enableAudio) {
            Toast.makeText(context, "Connecting to RTSP stream...", Toast.LENGTH_SHORT).show()
        }
        
        // Set up a listener for connection events
        rtspView.setStatusListener(object : RtspStatusListener {
            override fun onRtspStatusConnecting() {
                activity?.runOnUiThread {
                    Log.d(TAG, "RTSP Status: Connecting")
                }
            }

            override fun onRtspStatusConnected() {
                activity?.runOnUiThread {
                    Log.d(TAG, "RTSP Status: Connected")
                    
                    // Only show toast for the main view
                    if (enableAudio) {
                        Toast.makeText(context, "Connected to RTSP stream", Toast.LENGTH_SHORT).show()
                        startMotionDetection()
                    }
                    
                    // Try to get video dimensions from the SurfaceView
                    rtspView.post {
                        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
                        // Get the video size from preferences or use a default aspect ratio
                        val streamSuffix = if (rtspView == binding.rtspSurfaceView) "_1" else "_2"
                        val videoWidth = sharedPreferences.getString("video_width$streamSuffix", "1280")?.toIntOrNull() ?: 1280
                        val videoHeight = sharedPreferences.getString("video_height$streamSuffix", "720")?.toIntOrNull() ?: 720
                        
                        if (videoWidth > 0 && videoHeight > 0) {
                            Log.d(TAG, "Setting aspect ratio: ${videoWidth}x${videoHeight}")
                            if (rtspView == binding.rtspSurfaceView) {
                                binding.aspectRatioLayout.setAspectRatio(videoWidth.toFloat(), videoHeight.toFloat())
                            } else {
                                binding.pipAspectRatioLayout.setAspectRatio(videoWidth.toFloat(), videoHeight.toFloat())
                            }
                        }
                        
                        // Apply the aspect ratio mode
                        val aspectRatioMode = sharedPreferences.getString("aspect_ratio_mode", "0")?.toIntOrNull() ?: 0
                        if (rtspView == binding.rtspSurfaceView) {
                            binding.aspectRatioLayout.setAspectRatioMode(aspectRatioMode)
                        } else {
                            binding.pipAspectRatioLayout.setAspectRatioMode(aspectRatioMode)
                        }
                    }
                }
            }

            override fun onRtspStatusDisconnected() {
                activity?.runOnUiThread {
                    Log.d(TAG, "RTSP Status: Disconnected")
                    // Only show toast for the main view
                    if (enableAudio) {
                        Toast.makeText(context, "Disconnected from RTSP stream", Toast.LENGTH_SHORT).show()
                        stopMotionDetection()
                    }
                }
            }

            override fun onRtspStatusFailedUnauthorized() {
                activity?.runOnUiThread {
                    Log.e(TAG, "RTSP Status: Failed - Unauthorized")
                    // Only show toast for the main view
                    if (enableAudio) {
                        Toast.makeText(context, "Authentication failed", Toast.LENGTH_SHORT).show()
                        stopMotionDetection()
                    }
                }
            }

            override fun onRtspStatusFailed(message: String?) {
                activity?.runOnUiThread {
                    Log.e(TAG, "RTSP Status: Failed - $message")
                    // Only show toast for the main view
                    if (enableAudio) {
                        Toast.makeText(context, "Connection failed: $message", Toast.LENGTH_SHORT).show()
                        stopMotionDetection()
                    }
                }
            }
        })
    }

    private fun startMotionDetection() {
        if (!isMotionDetectionActive && _binding != null) {
            Log.d(TAG, "Starting motion detection with initial delay...")
            isMotionDetectionActive = true
            previousBitmap = null // Reset previous frame
            motionHistoryMap.clear() // Reset motion history
            motionRegions.clear() // Reset motion regions
            binding.motionStatusTextview.text = "Motion: No"
            binding.motionStatusTextview.visibility = View.VISIBLE
            binding.motionOverlayView.clearMotionRegions() // Clear any existing motion regions
            // Add an initial delay before the first attempt
            motionDetectionHandler.postDelayed(motionDetectionRunnable, INITIAL_FRAME_CAPTURE_DELAY_MS)
        }
    }

    private fun stopMotionDetection() {
        if (isMotionDetectionActive) {
            Log.d(TAG, "Stopping motion detection")
            isMotionDetectionActive = false
            motionDetectionHandler.removeCallbacks(motionDetectionRunnable)
            previousBitmap?.let {
                if (!it.isRecycled) {
                    it.recycle()
                }
            }
            previousBitmap = null
            motionHistoryMap.clear() // Clear motion history
            motionRegions.clear() // Clear motion regions
            if (_binding != null) {
                binding.motionStatusTextview.text = "Motion: Off"
                // binding.motionStatusTextview.visibility = View.GONE // Or keep it visible with "Off"
                binding.motionOverlayView.clearMotionRegions() // Clear motion regions from overlay
            }
        }
    }

    private fun processFrame(currentFrameFull: Bitmap) {
        // Ensure we don't process if motion detection was stopped or fragment is not added
        if (!isMotionDetectionActive || !isAdded || _binding == null) {
            if (!currentFrameFull.isRecycled) currentFrameFull.recycle()
            return
        }

        val currentBitmap: Bitmap
        try {
            // Downscale and convert to grayscale
            val grayscaleBitmap = toGrayscale(currentFrameFull) // currentFrameFull is ARGB_8888
            currentBitmap = downscaleBitmap(grayscaleBitmap, DOWNSCALE_FACTOR)
            // Create a copy of the bitmap to preserve it after recycling the original
            if (!grayscaleBitmap.isRecycled) grayscaleBitmap.recycle() // Recycle intermediate grayscale bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame (grayscale/downscale)", e)
            if (!currentFrameFull.isRecycled) currentFrameFull.recycle() // Recycle original if processing fails
            return
        }
        
        // Recycle the original frame as we no longer need it
        if (!currentFrameFull.isRecycled) currentFrameFull.recycle()

        if (previousBitmap == null) {
            previousBitmap = currentBitmap // Store the processed (downscaled, grayscale) bitmap
            return // First frame, nothing to compare yet
        }

        val prevBmp = previousBitmap!!
        if (prevBmp.width != currentBitmap.width || prevBmp.height != currentBitmap.height) {
            Log.w(TAG, "Bitmap dimensions mismatch, resetting previousBitmap.")
            if (!prevBmp.isRecycled) prevBmp.recycle()
            previousBitmap = currentBitmap
            return
        }

        // Use the advanced motion detection algorithm instead of simple comparison
        val motionDetected = detectAdvancedMotion(prevBmp, currentBitmap)

        activity?.runOnUiThread {
            if (_binding != null && isAdded) { // Check isAdded for safety
                 binding.motionStatusTextview.text = if (motionDetected) "Motion: Yes!" else "Motion: No"
            }
        }

        // Recycle the old previousBitmap and store the new currentBitmap
        if (!prevBmp.isRecycled) prevBmp.recycle()
        previousBitmap = currentBitmap
    }
    
    /**
     * Advanced motion detection that filters out small random movements
     * like tree leaves by using region-based analysis and motion persistence
     */
    private fun detectAdvancedMotion(bmp1: Bitmap, bmp2: Bitmap): Boolean {
        if (bmp1.width != bmp2.width || bmp1.height != bmp2.height) {
            return false // Should not happen if processed correctly
        }

        val width = bmp1.width
        val height = bmp1.height
        
        // Create a difference bitmap to track motion pixels
        val diffBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val diffPixels = IntArray(width * height)
        val pixels1 = IntArray(width * height)
        val pixels2 = IntArray(width * height)

        bmp1.getPixels(pixels1, 0, width, 0, 0, width, height)
        bmp2.getPixels(pixels2, 0, width, 0, 0, width, height)

        var totalDiffPixels = 0
        
        // Calculate pixel differences and mark them in diffPixels
        for (i in pixels1.indices) {
            val p1 = Color.red(pixels1[i]) // Since it's grayscale, R=G=B
            val p2 = Color.red(pixels2[i])
            
            if (Math.abs(p1 - p2) > PIXEL_DIFFERENCE_THRESHOLD) {
                diffPixels[i] = Color.WHITE // Mark as motion pixel
                totalDiffPixels++
            } else {
                diffPixels[i] = Color.BLACK // No motion
            }
        }
        
        // Set the difference pixels to the bitmap
        diffBitmap.setPixels(diffPixels, 0, width, 0, 0, width, height)
        
        // Calculate the total percentage of changed pixels
        val totalPixels = width * height
        val diffPercentage = (totalDiffPixels.toDouble() / totalPixels) * 100
        
        // Step 1: Basic motion check (similar to original algorithm)
        val basicMotionDetected = diffPercentage >= MOTION_AREA_THRESHOLD_PERCENT
        
        // If no basic motion detected, we can skip the advanced processing
        if (!basicMotionDetected) {
            diffBitmap.recycle()
            // Clear motion history for regions with no motion
            decrementMotionHistory()
            
            // Clear motion regions on the overlay
            activity?.runOnUiThread {
                if (_binding != null && isAdded) {
                    binding.motionOverlayView.clearMotionRegions()
                }
            }
            
            return false
        }
        
        // Step 2: Identify motion regions by dividing the image into a grid
        val cellWidth = width / GRID_CELLS_X
        val cellHeight = height / GRID_CELLS_Y
        
        // Reset motion regions
        motionRegions.clear()
        
        // Check each grid cell for motion
        for (gridY in 0 until GRID_CELLS_Y) {
            for (gridX in 0 until GRID_CELLS_X) {
                val startX = gridX * cellWidth
                val startY = gridY * cellHeight
                val endX = Math.min((gridX + 1) * cellWidth, width)
                val endY = Math.min((gridY + 1) * cellHeight, height)
                
                // Count motion pixels in this cell
                var cellMotionPixels = 0
                for (y in startY until endY) {
                    for (x in startX until endX) {
                        val index = y * width + x
                        if (index < diffPixels.size && diffPixels[index] == Color.WHITE) {
                            cellMotionPixels++
                        }
                    }
                }
                
                // Calculate percentage of motion in this cell
                val cellTotalPixels = (endX - startX) * (endY - startY)
                val cellMotionPercentage = (cellMotionPixels.toDouble() / cellTotalPixels) * 100
                
                // If enough motion in this cell, mark it as a motion region
                if (cellMotionPercentage >= MOTION_AREA_THRESHOLD_PERCENT * 2) { // Higher threshold for individual cells
                    val region = android.graphics.Rect(startX, startY, endX, endY)
                    motionRegions.add(region)
                    
                    // Update motion history for this region
                    val regionKey = "${gridX}_${gridY}"
                    val currentCount = motionHistoryMap[regionKey] ?: 0
                    motionHistoryMap[regionKey] = currentCount + 1
                } else {
                    // Decrement history for this region since no motion detected
                    val regionKey = "${gridX}_${gridY}"
                    val currentCount = motionHistoryMap[regionKey] ?: 0
                    if (currentCount > 0) {
                        motionHistoryMap[regionKey] = currentCount - 1
                    }
                }
            }
        }
        
        // Clean up
        diffBitmap.recycle()
        
        // Step 3: Check if any region has persistent motion
        var persistentMotionDetected = false
        val activeMotionRegions = ArrayList<android.graphics.Rect>()
        
        for ((regionKey, count) in motionHistoryMap) {
            if (count >= MOTION_PERSISTENCE_THRESHOLD) {
                persistentMotionDetected = true
                
                // Extract grid coordinates from the region key
                val parts = regionKey.split("_")
                if (parts.size == 2) {
                    try {
                        val gridX = parts[0].toInt()
                        val gridY = parts[1].toInt()
                        
                        // Calculate the rectangle for this grid cell
                        val startX = gridX * cellWidth
                        val startY = gridY * cellHeight
                        val endX = Math.min((gridX + 1) * cellWidth, width)
                        val endY = Math.min((gridY + 1) * cellHeight, height)
                        
                        // Add to active motion regions
                        activeMotionRegions.add(android.graphics.Rect(startX, startY, endX, endY))
                    } catch (e: NumberFormatException) {
                        Log.e(TAG, "Error parsing region key: $regionKey", e)
                    }
                }
            }
        }
        
        // Update the motion overlay with active regions
        activity?.runOnUiThread {
            if (_binding != null && isAdded) {
                binding.motionOverlayView.updateMotionRegions(activeMotionRegions, width, height)
            }
        }
        
        Log.d(TAG, "Motion detection: basic=$basicMotionDetected, persistent=$persistentMotionDetected, " +
              "regions=${motionRegions.size}, activeRegions=${activeMotionRegions.size}, diffPercentage=$diffPercentage%")
        
        return persistentMotionDetected
    }
    
    /**
     * Decrement motion history for all regions to gradually forget old motion
     */
    private fun decrementMotionHistory() {
        val keysToRemove = ArrayList<String>()
        
        for ((key, count) in motionHistoryMap) {
            if (count > 0) {
                motionHistoryMap[key] = count - 1
            }
            if (motionHistoryMap[key] == 0) {
                keysToRemove.add(key)
            }
        }
        
        // Clean up regions with no motion
        for (key in keysToRemove) {
            motionHistoryMap.remove(key)
        }
    }

    private fun downscaleBitmap(bitmap: Bitmap, scaleFactor: Float): Bitmap {
        val newWidth = (bitmap.width * scaleFactor).toInt().coerceAtLeast(1)
        val newHeight = (bitmap.height * scaleFactor).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun toGrayscale(bmpOriginal: Bitmap): Bitmap {
        val width = bmpOriginal.width
        val height = bmpOriginal.height
        
        // Create a grayscale bitmap using RGB_565 instead of ALPHA_8
        val bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmpGrayscale)
        val paint = android.graphics.Paint()
        val colorMatrix = android.graphics.ColorMatrix()
        colorMatrix.setSaturation(0f)
        val filter = android.graphics.ColorMatrixColorFilter(colorMatrix)
        paint.colorFilter = filter
        canvas.drawBitmap(bmpOriginal, 0f, 0f, paint)
        
        return bmpGrayscale
    }

    override fun onResume() {
        super.onResume()
        // Always try to connect when the fragment resumes
        connectToRtspStream()
        // Motion detection will be started by onRtspStatusConnected
    }

    override fun onPause() {
        super.onPause()
        // Stop the RTSP streams when the fragment is paused
        binding.rtspSurfaceView.stop()
        binding.pipRtspSurfaceView.stop()
        stopMotionDetection() // Stop motion detection when fragment is paused
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopMotionDetection() // Ensure motion detection is stopped
        // previousBitmap is recycled in stopMotionDetection
        _binding = null
    }
}