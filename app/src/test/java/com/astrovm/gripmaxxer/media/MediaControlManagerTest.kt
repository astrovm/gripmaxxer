package com.astrovm.gripmaxxer.media

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowMediaSessionManager

/** Session manager shadow that can be told to reject callers without notification access. */
@Implements(MediaSessionManager::class)
class DenyingMediaSessionManagerShadow : ShadowMediaSessionManager() {

    @Implementation
    override fun getActiveSessions(ignoredNotificationListener: ComponentName?): List<MediaController> {
        if (denyGetSessions) throw SecurityException("denied")
        return super.getActiveSessions(ignoredNotificationListener)
    }

    @Implementation
    override fun addOnActiveSessionsChangedListener(
        listener: MediaSessionManager.OnActiveSessionsChangedListener?,
        ignoredNotificationListener: ComponentName?,
    ) {
        if (denyAddListener) throw SecurityException("denied")
        super.addOnActiveSessionsChangedListener(listener, ignoredNotificationListener)
    }

    @Implementation
    override fun removeOnActiveSessionsChangedListener(listener: MediaSessionManager.OnActiveSessionsChangedListener?) {
        if (denyRemoveListener) throw IllegalArgumentException("not registered")
        super.removeOnActiveSessionsChangedListener(listener)
    }

    companion object {
        var denyGetSessions = false
        var denyAddListener = false
        var denyRemoveListener = false
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [DenyingMediaSessionManagerShadow::class])
class MediaControlManagerTest {

    private val app: Context = ApplicationProvider.getApplicationContext()
    private val sessionManager: MediaSessionManager = app.getSystemService(MediaSessionManager::class.java)
    private val shadowManager: ShadowMediaSessionManager get() = shadowOf(sessionManager)

    @After
    fun tearDown() {
        DenyingMediaSessionManagerShadow.denyGetSessions = false
        DenyingMediaSessionManagerShadow.denyAddListener = false
        DenyingMediaSessionManagerShadow.denyRemoveListener = false
    }

    private fun grantAccess(granted: Boolean) {
        val value = if (granted) {
            "other/.Service:" + ComponentName(app, HangNotificationListener::class.java).flattenToString()
        } else {
            "other/.Service"
        }
        Settings.Secure.putString(app.contentResolver, "enabled_notification_listeners", value)
    }

    private fun newController(): MediaController {
        val session = MediaSession(app, "test")
        return MediaController(app, session.sessionToken)
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
        shadowManager.addController(newController())
        val manager = MediaControlManager(app)
        manager.start()
        assertFalse(manager.status.value.hasNotificationAccess)
        assertFalse(manager.status.value.hasController)
        manager.refresh()
        assertFalse(manager.status.value.hasController)
    }

    @Test
    fun `start with access tracks the first active controller and follows session changes`() = runBlocking {
        grantAccess(true)
        val manager = MediaControlManager(app)
        manager.start()
        assertTrue(manager.status.value.hasNotificationAccess)
        assertFalse(manager.status.value.hasController)

        // A new session reaches the manager through the registered listener.
        shadowManager.addController(newController())
        val status = manager.status.value
        assertTrue(status.hasController)
        assertEquals(app.packageName, status.controllerPackage)

        // Transport controls are forwarded to the active controller without failing.
        manager.play()
        manager.pause()

        shadowManager.clearControllers()
        assertFalse(manager.status.value.hasController)
        assertNull(manager.status.value.controllerPackage)

        // After stop the listener is gone, so new sessions are ignored.
        manager.stop()
        shadowManager.addController(newController())
        assertFalse(manager.status.value.hasController)

        // An explicit refresh picks up the existing sessions again.
        manager.refresh()
        assertTrue(manager.status.value.hasController)
    }

    @Test
    fun `security exceptions are reported as missing access`() {
        grantAccess(true)
        DenyingMediaSessionManagerShadow.denyAddListener = true
        val manager = MediaControlManager(app)
        manager.start()
        assertFalse(manager.status.value.hasNotificationAccess)

        DenyingMediaSessionManagerShadow.denyGetSessions = true
        shadowManager.addController(newController())
        manager.refresh()
        assertFalse(manager.status.value.hasNotificationAccess)
        assertFalse(manager.status.value.hasController)
    }

    @Test
    fun `stop tolerates an already removed listener and play is a no-op without controller`() = runBlocking {
        DenyingMediaSessionManagerShadow.denyRemoveListener = true
        val manager = MediaControlManager(app)
        manager.stop()
        manager.play()
        manager.pause()
        assertFalse(manager.status.value.hasController)
    }

    @Test
    fun `losing access on refresh clears the controller`() {
        grantAccess(true)
        shadowManager.addController(newController())
        val manager = MediaControlManager(app)
        manager.start()
        assertTrue(manager.status.value.hasController)
        grantAccess(false)
        manager.refresh()
        assertFalse(manager.status.value.hasController)
        assertFalse(manager.status.value.hasNotificationAccess)
    }

    @Test
    fun `notification listener service can be created`() {
        assertNotNull(HangNotificationListener())
    }
}
