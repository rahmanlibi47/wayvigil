package com.saferoute.navigation

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Owns the Android text-to-speech engine for active navigation. */
class VoiceGuidancePlayer(
    context: Context,
    private val onReady: (Boolean) -> Unit
) : AutoCloseable {
    private lateinit var textToSpeech: TextToSpeech

    init {
        textToSpeech = TextToSpeech(context.applicationContext) { status ->
            val languageStatus = if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.ENGLISH)
            } else {
                TextToSpeech.ERROR
            }
            onReady(languageStatus >= TextToSpeech.LANG_AVAILABLE)
        }
    }

    fun speak(message: String) {
        if (message.isNotBlank()) {
            textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        }
    }

    fun stop() {
        textToSpeech.stop()
    }

    override fun close() {
        textToSpeech.stop()
        textToSpeech.shutdown()
    }

    companion object {
        private const val UTTERANCE_ID = "wayvigil-navigation-guidance"
    }
}
