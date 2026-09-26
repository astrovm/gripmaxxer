package com.astrovm.gripmaxxer.media

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaControlManagerTest {

    private val app: Context = ApplicationProvider.getApplicationContext()
    private val sessionManager = mockk<MediaSessionManager>(relaxed = true)
    private val transport = mockk<MediaController.TransportControls>(relaxed = true)
    private val controller = mockk<MediaController> {
        every { packageName } returns "com.example.player"
        every { transportControls } returns transport
    }
    private val listenerSlot = slot<MediaSessionManager.OnActiveSessionsChangedListener>()

    private val context = object : ContextWrapper(app) {
        override fun getApplicationContext(): Context = this
        override fun getSystemService(name: String): Any? =
            if (name == Context.MEDIA_SESSION_SERVICE) sessionManager else super.getSystemService(name)
    }

    private fun grantAccess(granted: Boolean) {
        val value = if (granted) {
            "other/.Service:" + ComponentName(app, HangNotificationListener::class.java).flattenToString()
        } else {
            "other/.Service"
        }
        Settings.Secure.putString(app.contentResolver, "enabled_notification_listeners", value)
    }

    @Before
    fun setUp() {
        every { sessionManager.addOnActiveSessionsChangedListener(capture(listenerSlot), any()) } returns Unit
    }

    @Test
    fun `access detection reads enabled listeners`() {
        assertFalse(MediaControlManager.isNotificationAccessEnabled(app))
        grantAccess(false)
        assertFalse(MediaControlManager.isNotificationAccessEnabled(app))
        grantAccess(true)
        assertTrue(MediaControlManager.isNotificationAccessEnabled(app))
    }

    @Test
    fun `start without access reports no controller`() {
        grantAccess(false)
        val manager = MediaControlManager(context)
        manager.start()
        assertFalse(manager.status.value.hasNotificationAccess)
        assertFalse(manager.status.value.hasController)
        manager.refresh()
        assertFalse(manager.status.value.hasController)
    }

    @Test
    fun `start with access tracks the first active controller and controls playback`() = runBlocking {
        grantAccess(true)
        every { sessionManager.getActiveSessions(any()) } returns listOf(controller)
        val manager = MediaControlManager(context)
        manager.start()

        val status = manager.status.value
        assertTrue(status.hasNotificationAccess)
        assertTrue(status.hasController)
        assertEquals("com.example.player", status.controllerPackage)

        manager.play()
        manager.pause()
        verify { transport.play() }
        verify { transport.pause() }

        listenerSlot.captured.onActiveSessionsChanged(null)
        assertFalse(manager.status.value.hasController)
        assertNull(manager.status.value.controllerPackage)

        manager.stop()
        verify { sessionManager.removeOnActiveSessionsChangedListener(any()) }
    }

    @Test
    fun `security exceptions are reported as missing access`() {
        grantAccess(true)
        every { sessionManager.addOnActiveSessionsChangedListener(any(), any()) } throws SecurityException("no")
        val manager = MediaControlManager(context)
        manager.start()
        assertFalse(manager.status.value.hasNotificationAccess)

        every { sessionManager.getActiveSessions(any()) } throws SecurityException("no")
        manager.refresh()
        assertFalse(manager.status.value.hasNotificationAccess)
    }

    @Test
    fun `stop tolerates an already removed listener and play is a no-op without controller`() = runBlocking {
        every { sessionManager.removeOnActiveSessionsChangedListener(any()) } throws IllegalArgumentException("gone")
        val manager = MediaControlManager(context)
        manager.stop()
        manager.play()
        manager.pause()
        verify(exactly = 0) { transport.play() }
    }

    @Test
    fun `losing access on refresh clears the controller`() {
        grantAccess(true)
        every { sessionManager.getActiveSessions(any()) } returns listOf(controller)
        val manager = MediaControlManager(context)
        manager.start()
        assertTrue(manager.status.value.hasController)
        grantAccess(false)
        manager.refresh()
        assertFalse(manager.status.value.hasController)
        assertFalse(manager.status.value.hasNotificationAccess)
    }

    @Test
    fun `notification listener service can be created`() {
        HangNotificationListener()
    }
}
