package com.example.rtsp_viewer_2

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentFirstBinding.inflate(inflater, container, false)
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
                    }
                }
            }

            override fun onRtspStatusFailedUnauthorized() {
                activity?.runOnUiThread {
                    Log.e(TAG, "RTSP Status: Failed - Unauthorized")
                    // Only show toast for the main view
                    if (enableAudio) {
                        Toast.makeText(context, "Authentication failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onRtspStatusFailed(message: String?) {
                activity?.runOnUiThread {
                    Log.e(TAG, "RTSP Status: Failed - $message")
                    // Only show toast for the main view
                    if (enableAudio) {
                        Toast.makeText(context, "Connection failed: $message", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        // Always try to connect when the fragment resumes
        connectToRtspStream()
    }

    override fun onPause() {
        super.onPause()
        // Stop the RTSP streams when the fragment is paused
        binding.rtspSurfaceView.stop()
        binding.pipRtspSurfaceView.stop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Stop the RTSP streams and clean up resources
        binding.rtspSurfaceView.stop()
        binding.pipRtspSurfaceView.stop()
        _binding = null
    }
}