package com.grloepr.pushtrack.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages app settings and preferences
 */
class SettingsManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "pushtrack_settings", 
        Context.MODE_PRIVATE
    )
    
    // Voice feedback settings
    private val _voiceEnabled = MutableStateFlow(prefs.getBoolean(KEY_VOICE_ENABLED, true))
    val voiceEnabled: StateFlow<Boolean> = _voiceEnabled.asStateFlow()
    
    private val _speechRate = MutableStateFlow(prefs.getFloat(KEY_SPEECH_RATE, 1.0f))
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()
    
    private val _voicePitch = MutableStateFlow(prefs.getFloat(KEY_VOICE_PITCH, 1.0f))
    val voicePitch: StateFlow<Float> = _voicePitch.asStateFlow()
    
    private val _voiceVolume = MutableStateFlow(prefs.getFloat(KEY_VOICE_VOLUME, 1.0f))
    val voiceVolume: StateFlow<Float> = _voiceVolume.asStateFlow()
    
    // UI settings
    private val _showDebugInfo = MutableStateFlow(prefs.getBoolean(KEY_SHOW_DEBUG, false))
    val showDebugInfo: StateFlow<Boolean> = _showDebugInfo.asStateFlow()
    
    private val _enhancedUI = MutableStateFlow(prefs.getBoolean(KEY_ENHANCED_UI, true))
    val enhancedUI: StateFlow<Boolean> = _enhancedUI.asStateFlow()
    
    // Feedback settings
    private val _postureAnalysisEnabled = MutableStateFlow(prefs.getBoolean(KEY_POSTURE_ANALYSIS, true))
    val postureAnalysisEnabled: StateFlow<Boolean> = _postureAnalysisEnabled.asStateFlow()
    
    private val _feedbackSensitivity = MutableStateFlow(prefs.getFloat(KEY_FEEDBACK_SENSITIVITY, 0.7f))
    val feedbackSensitivity: StateFlow<Float> = _feedbackSensitivity.asStateFlow()
    
    /**
     * Update voice enabled setting
     */
    fun setVoiceEnabled(enabled: Boolean) {
        _voiceEnabled.value = enabled
        prefs.edit().putBoolean(KEY_VOICE_ENABLED, enabled).apply()
    }
    
    /**
     * Update speech rate (0.1 - 3.0)
     */
    fun setSpeechRate(rate: Float) {
        val clampedRate = rate.coerceIn(0.1f, 3.0f)
        _speechRate.value = clampedRate
        prefs.edit().putFloat(KEY_SPEECH_RATE, clampedRate).apply()
    }
    
    /**
     * Update voice pitch (0.1 - 2.0)
     */
    fun setVoicePitch(pitch: Float) {
        val clampedPitch = pitch.coerceIn(0.1f, 2.0f)
        _voicePitch.value = clampedPitch
        prefs.edit().putFloat(KEY_VOICE_PITCH, clampedPitch).apply()
    }
    
    /**
     * Update voice volume (0.0 - 1.0)
     */
    fun setVoiceVolume(volume: Float) {
        val clampedVolume = volume.coerceIn(0.0f, 1.0f)
        _voiceVolume.value = clampedVolume
        prefs.edit().putFloat(KEY_VOICE_VOLUME, clampedVolume).apply()
    }
    
    /**
     * Update debug info visibility
     */
    fun setShowDebugInfo(show: Boolean) {
        _showDebugInfo.value = show
        prefs.edit().putBoolean(KEY_SHOW_DEBUG, show).apply()
    }
    
    /**
     * Update enhanced UI setting
     */
    fun setEnhancedUI(enhanced: Boolean) {
        _enhancedUI.value = enhanced
        prefs.edit().putBoolean(KEY_ENHANCED_UI, enhanced).apply()
    }
    
    /**
     * Update posture analysis setting
     */
    fun setPostureAnalysisEnabled(enabled: Boolean) {
        _postureAnalysisEnabled.value = enabled
        prefs.edit().putBoolean(KEY_POSTURE_ANALYSIS, enabled).apply()
    }
    
    /**
     * Update feedback sensitivity (0.0 - 1.0)
     */
    fun setFeedbackSensitivity(sensitivity: Float) {
        val clampedSensitivity = sensitivity.coerceIn(0.0f, 1.0f)
        _feedbackSensitivity.value = clampedSensitivity
        prefs.edit().putFloat(KEY_FEEDBACK_SENSITIVITY, clampedSensitivity).apply()
    }
    
    /**
     * Reset all settings to defaults
     */
    fun resetToDefaults() {
        prefs.edit().clear().apply()
        
        _voiceEnabled.value = true
        _speechRate.value = 1.0f
        _voicePitch.value = 1.0f
        _voiceVolume.value = 1.0f
        _showDebugInfo.value = false
        _enhancedUI.value = true
        _postureAnalysisEnabled.value = true
        _feedbackSensitivity.value = 0.7f
    }
    
    /**
     * Get current voice settings as a consolidated object
     */
    fun getVoiceSettings(): VoiceSettings {
        return VoiceSettings(
            enabled = _voiceEnabled.value,
            speechRate = _speechRate.value,
            pitch = _voicePitch.value,
            volume = _voiceVolume.value
        )
    }
    
    companion object {
        private const val KEY_VOICE_ENABLED = "voice_enabled"
        private const val KEY_SPEECH_RATE = "speech_rate"
        private const val KEY_VOICE_PITCH = "voice_pitch"
        private const val KEY_VOICE_VOLUME = "voice_volume"
        private const val KEY_SHOW_DEBUG = "show_debug"
        private const val KEY_ENHANCED_UI = "enhanced_ui"
        private const val KEY_POSTURE_ANALYSIS = "posture_analysis"
        private const val KEY_FEEDBACK_SENSITIVITY = "feedback_sensitivity"
    }
}

/**
 * Voice settings data class
 */
data class VoiceSettings(
    val enabled: Boolean = true,
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val volume: Float = 1.0f
)