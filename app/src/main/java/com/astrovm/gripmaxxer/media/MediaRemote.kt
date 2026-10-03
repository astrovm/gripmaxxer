package com.astrovm.gripmaxxer.media

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaSessionManager
import android.provider.Settings

/** Plays and pauses whatever media app is active, like a headset button would. */
class MediaRemote(private val context: Context) {

    private val listener = ComponentName(context, HangNotificationListener::class.java)

    fun play() = withController { it.transportControls.play() }

    fun pause() = withController { it.transportControls.pause() }

    private fun withController(action: (android.media.session.MediaController) -> Unit) {
        if (!hasAccess(context)) return
        val sessions = context.getSystemService(MediaSessionManager::class.java)
        // Access can be revoked between the check and the call.
        val controller = runCatching { sessions.getActiveSessions(listener).firstOrNull() }.getOrNull() ?: return
        action(controller)
    }

    companion object {
        fun hasAccess(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                ?: return false
            val expected = ComponentName(context, HangNotificationListener::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
        }
    }
}
