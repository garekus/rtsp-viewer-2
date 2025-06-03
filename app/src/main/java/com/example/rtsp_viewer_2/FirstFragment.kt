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
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        private const val MOTION_DETECTION_INTERVAL_MS = 500L
        private const val DOWNSCALE_FACTOR = 0.25f // Process at 1/4 resolution
        private const val PIXEL_DIFFERENCE_THRESHOLD = 30 // Grayscale difference
        private const val MOTION_AREA_THRESHOLD_PERCENT = 0.2 // 0.2% of pixels must change
        private const val INITIAL_FRAME_CAPTURE_DELAY_MS = 1000L // Delay before first PixelCopy
        private const val DEBUG_SAVE_BITMAP_FREQUENCY = 0 // Save debug bitmaps every 20 comparisons
    }

    private var bitmapComparisonCounter = 0

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
            // Get the URL, username, and password from SharedPreferences
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
            val rtspUrl = sharedPreferences.getString("rtsp_url", "") ?: ""
            val username = sharedPreferences.getString("rtsp_username", "") ?: ""
            val password = sharedPreferences.getString("rtsp_password", "") ?: ""
            
            if (rtspUrl.isEmpty()) {
                Toast.makeText(context, "Please set a valid RTSP URL in Settings", Toast.LENGTH_SHORT).show()
                return
            }
            
            Log.d(TAG, "Connecting to RTSP: $rtspUrl")
            
            // Parse the URL
            val uri = Uri.parse(rtspUrl)
            
            // Connect the main RTSP view
            connectRtspView(binding.rtspSurfaceView, uri, username, password, true)
            
            // Connect the PiP RTSP view (same stream, but without audio)
            connectRtspView(binding.pipRtspSurfaceView, uri, username, password, false)
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to RTSP stream", e)
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
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
                        val videoWidth = sharedPreferences.getString("video_width", "1280")?.toIntOrNull() ?: 1280
                        val videoHeight = sharedPreferences.getString("video_height", "720")?.toIntOrNull() ?: 720
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
            binding.motionStatusTextview.text = "Motion: No"
            binding.motionStatusTextview.visibility = View.VISIBLE
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
            if (_binding != null) {
                binding.motionStatusTextview.text = "Motion: Off"
                // binding.motionStatusTextview.visibility = View.GONE // Or keep it visible with "Off"
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

        val motionDetected = compareBitmaps(prevBmp, currentBitmap)

        activity?.runOnUiThread {
            if (_binding != null && isAdded) { // Check isAdded for safety
                 binding.motionStatusTextview.text = if (motionDetected) "Motion: Yes!" else "Motion: No"
            }
        }

        // Recycle the old previousBitmap and store the new currentBitmap
        if (!prevBmp.isRecycled) prevBmp.recycle()
        previousBitmap = currentBitmap
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

    private fun compareBitmaps(bmp1: Bitmap, bmp2: Bitmap): Boolean {
        if (bmp1.width != bmp2.width || bmp1.height != bmp2.height) {
            return false // Should not happen if processed correctly
        }

        val width = bmp1.width
        val height = bmp1.height
        var diffPixels = 0
        val pixels1 = IntArray(width * height)
        val pixels2 = IntArray(width * height)

        bmp1.getPixels(pixels1, 0, width, 0, 0, width, height)
        bmp2.getPixels(pixels2, 0, width, 0, 0, width, height)

        for (i in pixels1.indices) {
            // For ARGB_8888, get grayscale value from RGB components
            val p1 = Color.red(pixels1[i]) // Since it's grayscale, R=G=B
            val p2 = Color.red(pixels2[i])
            if (Math.abs(p1 - p2) > PIXEL_DIFFERENCE_THRESHOLD) {
                diffPixels++
            }
        }

        val totalPixels = width * height
        val diffPercentage = (diffPixels.toDouble() / totalPixels) * 100
        Log.d(TAG, "Diff percentage: $diffPercentage%, diffPixels: $diffPixels, totalPixels: $totalPixels")
        
        // Save debug bitmaps periodically
        bitmapComparisonCounter++
        if (DEBUG_SAVE_BITMAP_FREQUENCY > 0 && bitmapComparisonCounter % DEBUG_SAVE_BITMAP_FREQUENCY == 0) {
            saveBitmapPairForDebug(bmp1, bmp2, diffPercentage)
        }
        
        return diffPercentage >= MOTION_AREA_THRESHOLD_PERCENT
    }
    
    /**
     * Saves a pair of bitmaps to external storage for debugging purposes
     */
    private fun saveBitmapPairForDebug(bmp1: Bitmap, bmp2: Bitmap, diffPercentage: Double) {
        try {
            // Create a timestamp for unique filenames
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            
            // Get the app's external files directory
            val storageDir = context?.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            if (storageDir == null) {
                Log.e(TAG, "Failed to get external files directory")
                return
            }
            
            // Create debug directory if it doesn't exist
            val debugDir = File(storageDir, "motion_debug")
            if (!debugDir.exists()) {
                if (!debugDir.mkdirs()) {
                    Log.e(TAG, "Failed to create debug directory")
                    return
                }
            }
            
            // Create copies of the bitmaps to avoid recycling issues
            val bmp1Copy = bmp1.copy(bmp1.config ?: Bitmap.Config.ARGB_8888, true)
            val bmp2Copy = bmp2.copy(bmp2.config ?: Bitmap.Config.ARGB_8888, true)
            
            // Save the first bitmap
            val file1 = File(debugDir, "frame1_${timestamp}_${diffPercentage.toInt()}pct.png")
            FileOutputStream(file1).use { out ->
                bmp1Copy.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            
            // Save the second bitmap
            val file2 = File(debugDir, "frame2_${timestamp}_${diffPercentage.toInt()}pct.png")
            FileOutputStream(file2).use { out ->
                bmp2Copy.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            
            // Recycle the copies
            bmp1Copy.recycle()
            bmp2Copy.recycle()
            
            Log.d(TAG, "Saved debug bitmap pair to ${debugDir.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving debug bitmaps", e)
        }
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