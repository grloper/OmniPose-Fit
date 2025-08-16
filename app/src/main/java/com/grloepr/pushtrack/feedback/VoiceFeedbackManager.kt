package com.grloepr.pushtrack.feedback

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*

/**
 * Manages voice feedback functionality for the push-up counter app
 */
class VoiceFeedbackManager(private val context: Context) {
    
    private var textToSpeech: TextToSpeech? = null
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()
    
    // Voice settings
    private var speechRate = 1.0f
    private var pitchRate = 1.0f
    private var volume = 1.0f
    private var isEnabled = true
    
    // Track last announced rep to avoid repetition
    private var lastAnnouncedRep = -1
    
    init {
        initializeTextToSpeech()
    }
    
    private fun initializeTextToSpeech() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.let { tts ->
                    // Set language to default locale
                    val result = tts.setLanguage(Locale.getDefault())
                    
                    if (result == TextToSpeech.LANG_MISSING_DATA || 
                        result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        // Fallback to English if default locale not supported
                        tts.setLanguage(Locale.ENGLISH)
                    }
                    
                    // Configure speech parameters
                    tts.setSpeechRate(speechRate)
                    tts.setPitch(pitchRate)
                    
                    // Set utterance progress listener
                    tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {}
                        override fun onError(utteranceId: String?) {}
                    })
                    
                    _isInitialized.value = true
                }
            }
        }
    }
    
    /**
     * Announce a rep count
     */
    fun announceRepCount(count: Int) {
        if (!isEnabled || count <= lastAnnouncedRep) return
        
        val announcement = when {
            count <= 20 -> getNumberWord(count)
            count % 5 == 0 -> "$count"
            else -> null // Only announce every 5th rep after 20
        }
        
        announcement?.let { text ->
            speak(text, "rep_count_$count")
            lastAnnouncedRep = count
        }
    }
    
    /**
     * Provide posture feedback
     */
    fun announcePostureFeedback(feedback: PostureFeedback) {
        if (!isEnabled) return
        
        val message = when (feedback) {
            PostureFeedback.GOOD_FORM -> "Good form!"
            PostureFeedback.LOWER_BODY -> "Lower your body more"
            PostureFeedback.RAISE_BODY -> "Push up higher"
            PostureFeedback.STRAIGHTEN_BACK -> "Keep your back straight"
            PostureFeedback.ALIGN_HANDS -> "Align your hands"
            PostureFeedback.SLOW_DOWN -> "Slow down"
            PostureFeedback.KEEP_GOING -> "Keep going!"
        }
        
        speak(message, "posture_${feedback.name}")
    }
    
    /**
     * Announce workout completion
     */
    fun announceWorkoutComplete(totalReps: Int) {
        if (!isEnabled) return
        
        val message = when {
            totalReps == 0 -> "Workout complete!"
            totalReps == 1 -> "Workout complete! 1 push-up done."
            else -> "Workout complete! $totalReps push-ups done. Great job!"
        }
        
        speak(message, "workout_complete")
    }
    
    /**
     * Convert number to word (1-20)
     */
    private fun getNumberWord(number: Int): String {
        return when (number) {
            1 -> "One"
            2 -> "Two"
            3 -> "Three"
            4 -> "Four"
            5 -> "Five"
            6 -> "Six"
            7 -> "Seven"
            8 -> "Eight"
            9 -> "Nine"
            10 -> "Ten"
            11 -> "Eleven"
            12 -> "Twelve"
            13 -> "Thirteen"
            14 -> "Fourteen"
            15 -> "Fifteen"
            16 -> "Sixteen"
            17 -> "Seventeen"
            18 -> "Eighteen"
            19 -> "Nineteen"
            20 -> "Twenty"
            else -> "$number"
        }
    }
    
    /**
     * Speak text using TextToSpeech
     */
    private fun speak(text: String, utteranceId: String) {
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }
    
    /**
     * Update voice settings
     */
    fun updateSettings(
        enabled: Boolean = this.isEnabled,
        speechRate: Float = this.speechRate,
        pitchRate: Float = this.pitchRate,
        volume: Float = this.volume
    ) {
        this.isEnabled = enabled
        this.speechRate = speechRate.coerceIn(0.1f, 3.0f)
        this.pitchRate = pitchRate.coerceIn(0.1f, 2.0f)
        this.volume = volume.coerceIn(0.0f, 1.0f)
        
        textToSpeech?.let { tts ->
            tts.setSpeechRate(this.speechRate)
            tts.setPitch(this.pitchRate)
        }
    }
    
    /**
     * Reset rep tracking
     */
    fun reset() {
        lastAnnouncedRep = -1
    }
    
    /**
     * Clean up resources
     */
    fun shutdown() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        _isInitialized.value = false
    }
}