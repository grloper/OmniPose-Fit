package com.grloepr.pushtrack.feedback

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.*

/**
 * Manages voice feedback for exercises
 */
class VoiceFeedbackManager(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private var isInitialized = false
    private var lastVisibilityMessage: String? = null
    private var lastVisibilityMessageTime: Long = 0
    private val visibilityThrottleMs = 5000L // 5 seconds

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            isInitialized = true
        }
    }

    fun announceRepCount(count: Int) {
        if (isInitialized) {
            speak("Rep $count")
        }
    }

    fun providePostureFeedback(feedback: PostureFeedback) {
        if (isInitialized) {
            val message = when (feedback) {
                PostureFeedback.GOOD_FORM -> "Good form!"
                PostureFeedback.LOWER_BODY -> "Lower your body."
                PostureFeedback.RAISE_BODY -> "Raise your body."
                PostureFeedback.STRAIGHTEN_BACK -> "Straighten your back."
                PostureFeedback.ALIGN_HANDS -> "Align your hands."
            }
            speak(message)
        }
    }

    fun announceVisibility(isVisible: Boolean) {
        if (!isInitialized) return

        val message = if (isVisible) "Visible" else "Not visible"
        val currentTime = System.currentTimeMillis()

        if (message != lastVisibilityMessage || currentTime - lastVisibilityMessageTime > visibilityThrottleMs) {
            speak(message)
            lastVisibilityMessage = message
            lastVisibilityMessageTime = currentTime
        }
    }

    fun announceExerciseChange(exerciseName: String) {
        if (isInitialized) {
            speak("Starting $exerciseName")
        }
    }

    fun announceWorkoutComplete(totalReps: Int) {
        if (isInitialized) {
            speak("Workout complete. Total reps: $totalReps")
        }
    }

    private fun speak(text: String) {
        if (isInitialized) {
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, null)
        }
    }

    fun shutdown() {
        if (tts != null) {
            tts?.stop()
            tts?.shutdown()
        }
    }
}

