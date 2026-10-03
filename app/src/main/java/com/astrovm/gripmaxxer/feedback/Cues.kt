package com.astrovm.gripmaxxer.feedback

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Beeps on reps and reads hold times out loud. */
class Cues(context: Context) {

    private val tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }.getOrNull()

    @Volatile
    private var ttsReady = false
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ttsReady = status == TextToSpeech.SUCCESS
    }

    fun beep() {
        tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 100)
    }

    fun say(text: String) {
        if (!ttsReady) return
        tts.language = Locale.getDefault()
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, text)
    }

    fun release() {
        tone?.release()
        tts.shutdown()
    }

    companion object {
        /** How often hold times are read out. */
        const val HOLD_CUE_EVERY_S = 10L

        /** "40 seconds", "1 minute", "1 minute 20 seconds". */
        fun holdText(seconds: Long): String {
            val minutes = seconds / 60
            val rest = seconds % 60
            val minutePart = when (minutes) {
                0L -> null
                1L -> "1 minute"
                else -> "$minutes minutes"
            }
            val secondPart = when (rest) {
                0L -> null
                1L -> "1 second"
                else -> "$rest seconds"
            }
            return listOfNotNull(minutePart, secondPart).joinToString(" ")
        }
    }
}
