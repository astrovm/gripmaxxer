package com.astrovm.gripmaxxer.media

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowTransportControls

@RunWith(RobolectricTestRunner::class)
class MediaRemoteTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val listener = ComponentName(context, HangNotificationListener::class.java)
    private var controller: MediaController? = null

    /** The last transport command the player received, or 0. */
    private fun lastAction(): Long =
        Shadow.extract<ShadowTransportControls>(controller!!.transportControls).lastPerformedAction

    private fun grantAccess() {
        Settings.Secure.putString(
            context.contentResolver,
            "enabled_notification_listeners",
            "other/.X:${listener.flattenToString()}",
        )
    }

    /** A media app with an active session, like a video player. */
    private fun playerSession() {
        val session = MediaSession(context, "player")
        controller = session.controller
        shadowOf(context.getSystemService(MediaSessionManager::class.java)).addController(session.controller)
    }

    @Test
    fun accessFollowsTheSystemSetting() {
        assertFalse(MediaRemote.hasAccess(context))
        Settings.Secure.putString(context.contentResolver, "enabled_notification_listeners", "other/.X")
        assertFalse(MediaRemote.hasAccess(context))
        grantAccess()
        assertTrue(MediaRemote.hasAccess(context))
    }

    @Test
    fun playsAndPausesTheActiveSession() {
        grantAccess()
        playerSession()
        val remote = MediaRemote(context)
        remote.play()
        assertEquals(PlaybackState.ACTION_PLAY, lastAction())
        remote.pause()
        assertEquals(PlaybackState.ACTION_PAUSE, lastAction())
    }

    @Test
    fun doesNothingWithoutAccessOrMedia() {
        playerSession()
        MediaRemote(context).play()
        assertEquals(0L, lastAction())
    }

    @Test
    fun noMediaAppIsFine() {
        grantAccess()
        MediaRemote(context).pause()
        assertTrue(MediaRemote.hasAccess(context))
    }

    @Test
    fun listenerServiceExists() {
        HangNotificationListener()
    }
}
