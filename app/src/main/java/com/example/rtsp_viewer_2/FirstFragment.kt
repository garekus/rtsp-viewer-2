package com.example.rtsp_viewer_2

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.alexvas.rtsp.codec.VideoDecodeThread
import com.alexvas.rtsp.widget.RtspStatusListener
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

        // Set up the connect button click listener
        binding.connectButton.setOnClickListener {
            connectToRtspStream()
        }
        
        // Set up the settings button click listener
        binding.settingsButton.setOnClickListener {
            findNavController().navigate(R.id.action_FirstFragment_to_SettingsFragment)
        }
        
        // Configure RtspSurfaceView for better compatibility
        binding.rtspSurfaceView.debug = true // Enable debug logging
        binding.rtspSurfaceView.videoDecoderType = VideoDecodeThread.DecoderType.HARDWARE // Use hardware decoder
        
        // Set default aspect ratio (16:9) until we get the actual video dimensions
        binding.aspectRatioLayout.setAspectRatio(16f, 9f)
        
        // Set the aspect ratio mode from preferences
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val aspectRatioMode = sharedPreferences.getString("aspect_ratio_mode", "0")?.toIntOrNull() ?: 0
        binding.aspectRatioLayout.setAspectRatioMode(aspectRatioMode)
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
                // Navigate to settings if URL is not set
                findNavController().navigate(R.id.action_FirstFragment_to_SettingsFragment)
                return
            }
            
            Log.d(TAG, "Connecting to RTSP: $rtspUrl")
            
            // Parse the URL
            val uri = Uri.parse(rtspUrl)
            
            // Stop any existing stream
            binding.rtspSurfaceView.stop()
            
            // Initialize the RTSP SurfaceView with the URI, username, and password
            binding.rtspSurfaceView.init(uri, username, password, "RTSP Viewer App")
            
            // Start the stream with video only first (try to simplify for troubleshooting)
            binding.rtspSurfaceView.start(
                requestVideo = true,
                requestAudio = false, // Disable audio to see if that helps
                requestApplication = false
            )
            
            Toast.makeText(context, "Connecting to RTSP stream...", Toast.LENGTH_SHORT).show()
            
            // Set up a listener for connection events
            binding.rtspSurfaceView.setStatusListener(object : RtspStatusListener {
                override fun onRtspStatusConnecting() {
                    activity?.runOnUiThread {
                        Log.d(TAG, "RTSP Status: Connecting")
                    }
                }

                override fun onRtspStatusConnected() {
                    activity?.runOnUiThread {
                        Log.d(TAG, "RTSP Status: Connected")
                        Toast.makeText(context, "Connected to RTSP stream", Toast.LENGTH_SHORT).show()
                        
                        // Try to get video dimensions from the SurfaceView
                        // This is a workaround since there's no direct callback for video dimensions
                        binding.rtspSurfaceView.post {
                            // Get the video size from preferences or use a default aspect ratio
                            val videoWidth = sharedPreferences.getString("video_width", "1280")?.toIntOrNull() ?: 1280
                            val videoHeight = sharedPreferences.getString("video_height", "720")?.toIntOrNull() ?: 720
                            if (videoWidth > 0 && videoHeight > 0) {
                                Log.d(TAG, "Setting aspect ratio: ${videoWidth}x${videoHeight}")
                                binding.aspectRatioLayout.setAspectRatio(videoWidth.toFloat(), videoHeight.toFloat())
                            }
                            
                            // Apply the aspect ratio mode
                            val aspectRatioMode = sharedPreferences.getString("aspect_ratio_mode", "0")?.toIntOrNull() ?: 0
                            binding.aspectRatioLayout.setAspectRatioMode(aspectRatioMode)
                        }
                    }
                }

                override fun onRtspStatusDisconnected() {
                    activity?.runOnUiThread {
                        Log.d(TAG, "RTSP Status: Disconnected")
                        Toast.makeText(context, "Disconnected from RTSP stream", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onRtspStatusFailedUnauthorized() {
                    activity?.runOnUiThread {
                        Log.e(TAG, "RTSP Status: Failed - Unauthorized")
                        Toast.makeText(context, "Authentication failed", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onRtspStatusFailed(message: String?) {
                    activity?.runOnUiThread {
                        Log.e(TAG, "RTSP Status: Failed - $message")
                        Toast.makeText(context, "Connection failed: $message", Toast.LENGTH_SHORT).show()
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to RTSP stream", e)
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        // Check if we should automatically connect on resume
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val rtspUrl = sharedPreferences.getString("rtsp_url", "") ?: ""
        if (rtspUrl.isNotEmpty()) {
            connectToRtspStream()
        }
    }

    override fun onPause() {
        super.onPause()
        // Stop the RTSP stream when the fragment is paused
        binding.rtspSurfaceView.stop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Stop the RTSP stream and clean up resources
        binding.rtspSurfaceView.stop()
        _binding = null
    }
}