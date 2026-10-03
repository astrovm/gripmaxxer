package com.astrovm.gripmaxxer.media

import android.service.notification.NotificationListenerService

/**
 * Android only lets apps with notification access control other apps' media.
 * This service exists just to hold that access. Its name stays the same as in older
 * versions so access people already granted keeps working.
 */
class HangNotificationListener : NotificationListenerService()
