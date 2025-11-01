package com.grloepr.pushtrack.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * Manages Text-to-Speech for rep counting announcements
 */
class TextToSpeechManager(context: Context) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TTS", "Language not supported")
                } else {
                    isInitialized = true
                    Log.d("TTS", "Text-to-Speech initialized successfully")
                }
            } else {
                Log.e("TTS", "Text-to-Speech initialization failed")
            }
        }
    }

    /**
     * Speaks the given text
     */
    fun speak(text: String) {
        if (isInitialized) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        } else {
            Log.w("TTS", "TTS not initialized yet, cannot speak: $text")
        }
    }

    /**
     * Announces the rep count
     */
    fun announceRepCount(count: Int) {
        speak(count.toString())
    }

    /**
     * Cleanup resources
     */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        isInitialized = false
    }
}
