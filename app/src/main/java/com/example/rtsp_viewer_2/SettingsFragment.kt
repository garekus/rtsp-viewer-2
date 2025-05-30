package com.example.rtsp_viewer_2

import android.os.Bundle
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager

class SettingsFragment : PreferenceFragmentCompat(), Preference.OnPreferenceChangeListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
        
        // Register preference change listeners for all preferences
        val rtspUrl = findPreference<Preference>("rtsp_url")
        val rtspUsername = findPreference<Preference>("rtsp_username")
        val rtspPassword = findPreference<Preference>("rtsp_password")
        val videoWidth = findPreference<Preference>("video_width")
        val videoHeight = findPreference<Preference>("video_height")
        val aspectRatioMode = findPreference<Preference>("aspect_ratio_mode")
        
        rtspUrl?.onPreferenceChangeListener = this
        rtspUsername?.onPreferenceChangeListener = this
        rtspPassword?.onPreferenceChangeListener = this
        videoWidth?.onPreferenceChangeListener = this
        videoHeight?.onPreferenceChangeListener = this
        aspectRatioMode?.onPreferenceChangeListener = this
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
