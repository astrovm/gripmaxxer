package com.example.hangplaycam.media

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class MediaControlStatus(
    val hasNotificationAccess: Boolean,
    val hasController: Boolean,
    val controllerPackage: String? = null,
)

class MediaControlManager(private val context: Context) {

    private val sessionManager = context.getSystemService(MediaSessionManager::class.java)
    private val listenerComponent = ComponentName(context, HangNotificationListener::class.java)

    private val _status = MutableStateFlow(
        MediaControlStatus(
            hasNotificationAccess = isNotificationAccessEnabled(context),
            hasController = false,
            controllerPackage = null,
        )
    )
    val status: StateFlow<MediaControlStatus> = _status.asStateFlow()

    @Volatile
    private var activeController: MediaController? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        updateActiveController(controllers.orEmpty())
    }

    fun start() {
        val hasAccess = isNotificationAccessEnabled(context)
        if (!hasAccess) {
            _status.value = _status.value.copy(hasNotificationAccess = false, hasController = false, controllerPackage = null)
            return
        }

        try {
            sessionManager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent)
            refresh()
        } catch (_: SecurityException) {
            _status.value = _status.value.copy(hasNotificationAccess = false, hasController = false, controllerPackage = null)
        }
    }

    fun stop() {
        try {
            sessionManager.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (_: Exception) {
            // Listener may already be removed.
        }
        activeController = null
    }

    fun refresh() {
        val hasAccess = isNotificationAccessEnabled(context)
        if (!hasAccess) {
            _status.value = _status.value.copy(hasNotificationAccess = false, hasController = false, controllerPackage = null)
            activeController = null
            return
        }

        try {
            val sessions = sessionManager.getActiveSessions(listenerComponent).orEmpty()
            updateActiveController(sessions)
        } catch (_: SecurityException) {
            _status.value = _status.value.copy(hasNotificationAccess = false, hasController = false, controllerPackage = null)
        }
    }

    suspend fun play() {
        withContext(Dispatchers.Default) {
            activeController?.transportControls?.play()
        }
    }

    suspend fun pause() {
        withContext(Dispatchers.Default) {
            activeController?.transportControls?.pause()
        }
    }

    private fun updateActiveController(controllers: List<MediaController>) {
        val controller = controllers.firstOrNull()
        activeController = controller
        _status.value = MediaControlStatus(
            hasNotificationAccess = isNotificationAccessEnabled(context),
            hasController = controller != null,
            controllerPackage = controller?.packageName,
        )
    }

    companion object {
        fun isNotificationAccessEnabled(context: Context): Boolean {
            val enabledListeners = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners",
            ) ?: return false
            val expected = ComponentName(context, HangNotificationListener::class.java).flattenToString()
            return enabledListeners.split(':').any { it.equals(expected, ignoreCase = true) }
        }
    }
}
