package com.astrovm.gripmaxxer.feedback

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowTextToSpeech

@RunWith(RobolectricTestRunner::class)
class CuesTest {

    @Test
    fun holdTextReadsNaturally() {
        assertEquals("10 seconds", Cues.holdText(10))
        assertEquals("1 second", Cues.holdText(1))
        assertEquals("1 minute", Cues.holdText(60))
        assertEquals("2 minutes", Cues.holdText(120))
        assertEquals("1 minute 20 seconds", Cues.holdText(80))
        assertEquals("2 minutes 1 second", Cues.holdText(121))
    }

    @Test
    fun speaksOnlyOnceTheVoiceIsReady() {
        val cues = Cues(ApplicationProvider.getApplicationContext<Context>())
        val tts = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance())

        cues.say("too early")
        assertNull(tts.lastSpokenText)

        tts.onInitListener.onInit(TextToSpeech.SUCCESS)
        cues.say("10 seconds")
        assertEquals("10 seconds", tts.lastSpokenText)

        tts.onInitListener.onInit(TextToSpeech.ERROR)
        cues.say("20 seconds")
        assertEquals("10 seconds", tts.lastSpokenText)

        cues.beep()
        cues.release()
        assertTrue(tts.isShutdown)
    }
}
