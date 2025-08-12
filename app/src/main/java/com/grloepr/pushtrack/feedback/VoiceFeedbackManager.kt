package com.grloepr.pushtrack.feedback

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.os.Bundle // Added import
import java.util.*

/**
 * Provides voice feedback for push-up exercises
 */
class VoiceFeedbackManager(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var lastAnnouncedCount = 0
    private var lastPostureFeedback: PostureFeedback? = null
    private var postureFeedbackThrottleMs = 5000L // Only give feedback every 5 seconds
    private var lastPostureFeedbackTime = 0L

    // Settings
    private var isMuted = false
    private var speechRate = 1.0f
    private var pitchRate = 1.0f
    private var volume = 1.0f

    init {
        initTTS()
    }

    private fun initTTS() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TTS", "Language not supported")
                } else {
                    isInitialized = true
                    tts?.setSpeechRate(speechRate)
                    tts?.setPitch(pitchRate)
                    Log.d("TTS", "TTS initialized successfully")
                }
            } else {
                Log.e("TTS", "TTS initialization failed")
            }
        }

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                // Speech started
            }

            override fun onDone(utteranceId: String?) {
                // Speech completed
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                // Error occurred
                Log.e("TTS", "Error in TTS utterance")
            }
        })
    }

    /**
     * Announce the current rep count
     */
    fun announceRepCount(count: Int) {
        if (!isInitialized || isMuted || count <= lastAnnouncedCount) return

        val message = when {
            count % 10 == 0 -> "$count! Great job, keep pushing!"
            count % 5 == 0 -> "$count! You're doing well!"
            else -> "$count"
        }

        speak(message)
        lastAnnouncedCount = count
    }

    /**
     * Reset the counter for voice feedback
     */
    fun reset() {
        lastAnnouncedCount = 0
        lastPostureFeedback = null
        lastPostureFeedbackTime = 0L
    }

    /**
     * Provide feedback about posture issues
     */
    fun providePostureFeedback(feedback: PostureFeedback?, formQuality: Float) {
        if (!isInitialized || isMuted || feedback == null) return
        
        // Don't repeat the same feedback too frequently
        if (feedback == lastPostureFeedback) {
            val now = System.currentTimeMillis()
            if (now - lastPostureFeedbackTime < postureFeedbackThrottleMs) {
                return
            }
        }

        val message = when (feedback) {
            PostureFeedback.STRAIGHTEN_BACK -> "Keep your back straight"
            PostureFeedback.LOWER_BODY -> "Go lower"
            PostureFeedback.RAISE_BODY -> "Push all the way up"
            PostureFeedback.ALIGN_HANDS -> "Keep your hands aligned"
            PostureFeedback.GOOD_FORM -> {
                if (formQuality >= 95) "Perfect form!" else null
            }
            else -> null
        }

        message?.let {
            speak(it)
            lastPostureFeedback = feedback
            lastPostureFeedbackTime = System.currentTimeMillis()
        }
    }

    /**
     * Update voice feedback settings
     */
    fun updateSettings(
        enabled: Boolean,
        speechRate: Float = this.speechRate,
        pitchRate: Float = this.pitchRate,
        volume: Float = this.volume
    ) {
        this.isMuted = !enabled
        this.speechRate = speechRate.coerceIn(0.5f, 3.0f)
        this.pitchRate = pitchRate.coerceIn(0.1f, 2.0f)
        this.volume = volume.coerceIn(0.0f, 1.0f)

        tts?.setSpeechRate(this.speechRate)
        tts?.setPitch(this.pitchRate)
    }

    /**
     * Speak a message with configured settings
     */
    private fun speak(message: String) {
        if (!isInitialized || isMuted) return

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
        }
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, params, "FeedbackMsg_${System.currentTimeMillis()}")
    }

    /**
     * Cleanup TTS resources
     */
    fun shutdown() {
        isInitialized = false
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}

/**
 * Types of posture feedback
 */
enum class PostureFeedback {
    GOOD_FORM,
    LOWER_BODY,
    RAISE_BODY,
    STRAIGHTEN_BACK,
    ALIGN_HANDS,
    SLOW_DOWN,
    KEEP_GOING
}