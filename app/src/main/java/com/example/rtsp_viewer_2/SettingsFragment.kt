package com.example.rtsp_viewer_2

import android.os.Bundle
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager

class SettingsFragment : PreferenceFragmentCompat(), Preference.OnPreferenceChangeListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
        
        // Register preference change listeners for Stream 1 preferences
        val rtspUrl1 = findPreference<Preference>("rtsp_url_1")
        val rtspUsername1 = findPreference<Preference>("rtsp_username_1")
        val rtspPassword1 = findPreference<Preference>("rtsp_password_1")
        val videoWidth1 = findPreference<Preference>("video_width_1")
        val videoHeight1 = findPreference<Preference>("video_height_1")
        
        // Register preference change listeners for Stream 2 preferences
        val rtspUrl2 = findPreference<Preference>("rtsp_url_2")
        val rtspUsername2 = findPreference<Preference>("rtsp_username_2")
        val rtspPassword2 = findPreference<Preference>("rtsp_password_2")
        val videoWidth2 = findPreference<Preference>("video_width_2")
        val videoHeight2 = findPreference<Preference>("video_height_2")
        
        // Display settings
        val aspectRatioMode = findPreference<Preference>("aspect_ratio_mode")
        
        // Set listeners for Stream 1
        rtspUrl1?.onPreferenceChangeListener = this
        rtspUsername1?.onPreferenceChangeListener = this
        rtspPassword1?.onPreferenceChangeListener = this
        videoWidth1?.onPreferenceChangeListener = this
        videoHeight1?.onPreferenceChangeListener = this
        
        // Set listeners for Stream 2
        rtspUrl2?.onPreferenceChangeListener = this
        rtspUsername2?.onPreferenceChangeListener = this
        rtspPassword2?.onPreferenceChangeListener = this
        videoWidth2?.onPreferenceChangeListener = this
        videoHeight2?.onPreferenceChangeListener = this
        
        // Set listener for display settings
        aspectRatioMode?.onPreferenceChangeListener = this
        
        // Migrate old preferences if they exist
        migrateOldPreferences()
    }
    
    /**
     * Migrate old preference keys to new ones for backward compatibility
     */
    private fun migrateOldPreferences() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val editor = sharedPreferences.edit()
        
        // Check if we need to migrate (if old keys exist and new ones don't)
        val oldRtspUrl = sharedPreferences.getString("rtsp_url", null)
        val oldRtspUsername = sharedPreferences.getString("rtsp_username", null)
        val oldRtspPassword = sharedPreferences.getString("rtsp_password", null)
        val oldVideoWidth = sharedPreferences.getString("video_width", null)
        val oldVideoHeight = sharedPreferences.getString("video_height", null)
        
        val hasNewStream1 = sharedPreferences.contains("rtsp_url_1")
        
        // If old preferences exist and new ones don't, copy values to both streams
        if (oldRtspUrl != null && !hasNewStream1) {
            // Copy to Stream 1
            editor.putString("rtsp_url_1", oldRtspUrl)
            if (oldRtspUsername != null) editor.putString("rtsp_username_1", oldRtspUsername)
            if (oldRtspPassword != null) editor.putString("rtsp_password_1", oldRtspPassword)
            if (oldVideoWidth != null) editor.putString("video_width_1", oldVideoWidth)
            if (oldVideoHeight != null) editor.putString("video_height_1", oldVideoHeight)
            
            // Copy to Stream 2 as well
            editor.putString("rtsp_url_2", oldRtspUrl)
            if (oldRtspUsername != null) editor.putString("rtsp_username_2", oldRtspUsername)
            if (oldRtspPassword != null) editor.putString("rtsp_password_2", oldRtspPassword)
            if (oldVideoWidth != null) editor.putString("video_width_2", oldVideoWidth)
            if (oldVideoHeight != null) editor.putString("video_height_2", oldVideoHeight)
            
            // Apply changes
            editor.apply()
        }
    }
    
    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        // Allow the preference to be updated
        return true
    }
    
    override fun onStop() {
        super.onStop()
        // Navigate back to FirstFragment when leaving settings
        // This will trigger reconnection in FirstFragment.onResume()
        findNavController().popBackStack()
    }
}
