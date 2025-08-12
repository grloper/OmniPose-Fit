package com.grloepr.pushtrack.feedback

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

    // Add handler for timed sequences
    private val handler = Handler(Looper.getMainLooper())

    // Add throttling for visibility messages
    private var lastVisibilityMessage: String? = null
    private var lastVisibilityMessageTime = 0L
    private val visibilityThrottleMs = 3000L

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

    // Internal speak with queue mode
    private fun speakInternal(message: String, flush: Boolean = false) {
        if (!isInitialized || isMuted) return
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
        }
        tts?.speak(
            message,
            if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
            params,
            "FeedbackMsg_${System.currentTimeMillis()}"
        )
    }

    // Unified speak (flush optional)
    private fun speak(message: String, flush: Boolean = true) {
        if (!isInitialized || isMuted) return
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
        }
        tts?.speak(
            message,
            if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
            params,
            "Feedback_${System.currentTimeMillis()}"
        )
    }

    /**
     * Countdown + detected exercise announcement (Smart Mode)
     */
    fun announceExerciseDetectionCountdown(exerciseName: String) {
        if (!isInitialized || isMuted) return
        speak("Three", flush = true)
        speak("Two", flush = false)
        speak("One", flush = false)
        speak("Detected $exerciseName workout", flush = false)
    }

    /**
     * Announce exercise introduction with tips
     */
    fun announceExerciseIntro(exerciseName: String) {
        if (!isInitialized || isMuted) return
        val tip = when (exerciseName.lowercase()) {
            "push up" -> "Keep a straight line from shoulders to heels."
            "pull up" -> "Engage your core, no swinging."
            "squat" -> "Chest up, knees tracking over toes."
            else -> ""
        }
        speak("$exerciseName mode. $tip", flush = false)
    }

    /**
     * Announce the current rep count
     */
    fun announceRepCount(count: Int) { // override earlier version
        if (!isInitialized || isMuted || count <= lastAnnouncedCount) return
        val message = count.toString()
        speak(message, flush = false)
        lastAnnouncedCount = count
    }

    /**
     * Encourage every 5 reps (called externally after announceRepCount if desired)
     */
    fun maybeEncourage(count: Int) {
        if (!isInitialized || isMuted) return
        if (count > 0 && count % 5 == 0) {
            speak(when (count) {
                5 -> "Good start"
                10 -> "Nice pace"
                15 -> "Keep it up"
                20 -> "Great work"
                else -> "Strong"
            }, flush = false)
        }
    }

    // Posture feedback (positive / corrective)
    /**
     * Provide feedback about posture issues
     */
    fun providePostureFeedback(feedback: PostureFeedback?, formQuality: Float) {
        if (!isInitialized || isMuted || feedback == null) return
        val now = System.currentTimeMillis()
        if (feedback == lastPostureFeedback && now - lastPostureFeedbackTime < postureFeedbackThrottleMs) return
        val msg = when (feedback) {
            PostureFeedback.STRAIGHTEN_BACK -> "Straighten your back"
            PostureFeedback.LOWER_BODY -> "Lower more"
            PostureFeedback.RAISE_BODY -> "Extend fully"
            PostureFeedback.ALIGN_HANDS -> "Align your hands"
            PostureFeedback.GOOD_FORM -> if (formQuality >= 90) "Great form" else null
            PostureFeedback.SLOW_DOWN -> "Slow controlled reps"
            PostureFeedback.KEEP_GOING -> "Keep going"
        }
        msg?.let {
            speak(it, flush = false)
            lastPostureFeedback = feedback
            lastPostureFeedbackTime = now
        }
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
     * Cleanup TTS resources
     */
    fun shutdown() {
        isInitialized = false
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    /**
     * Safe announce rep count (no-op if not tracking)
     */
    fun safeAnnounceRep(count: Int, tracking: Boolean) {
        if (!tracking) return
        announceRepCount(count)
    }

    /**
     * Announce calibration completion
     */
    fun announceCalibrationComplete(exerciseName: String) {
        speak("$exerciseName calibrated", flush = false)
    }

    /**
     * Announce visibility/positioning guidance
     */
    fun announceVisibilityGuidance(message: String) {
        if (!isInitialized || isMuted) return
        val now = System.currentTimeMillis()
        if (message == lastVisibilityMessage && now - lastVisibilityMessageTime < visibilityThrottleMs) return

        speak(message, flush = true)
        lastVisibilityMessage = message
        lastVisibilityMessageTime = now
    }

    /**
     * Announce positioning guidance for specific exercises
     */
    fun announcePositioningGuidance(exerciseType: String) {
        if (!isInitialized || isMuted) return
        val guidance = when (exerciseType.lowercase()) {
            "push_up", "push up" -> "Position camera above you. Look at the ground during push-ups."
            "pull_up", "pull up" -> "Position camera in front. Hang from bar with arms extended."
            "squat" -> "Position camera in front. Stand upright to begin."
            else -> "Position yourself in the camera frame."
        }
        speak(guidance, flush = false)
    }

    /**
     * Announce rep rejection reasons
     */
    fun announceRepRejected(reason: String? = null) {
        if (!isInitialized || isMuted) return
        val msg = when (reason) {
            "depth" -> "Rep not counted. Go deeper."
            "lockout" -> "Rep not counted. Extend fully."
            else -> "Rep not counted."
        }
        speak(msg, flush = false)
    }

    /**
     * Announce uncertainty in rep counting
     */
    fun announceRepUncertain() {
        if (!isInitialized || isMuted) return
        speak("Uncertain rep. Improve form.", flush = false)
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