package com.woody.booklauncher

import android.service.notification.NotificationListenerService

/**
 * A deliberately quiet notification listener.
 *
 * Android only hands out `MediaSessionManager.getActiveSessions()` to a
 * notification listener. We never read notification content: the service exists
 * purely to identify the media sessions that are playing, so audiobook and TTS
 * sessions can be controlled directly instead of swallowing each other's media
 * keys. The user turns it on in Settings › Widgets › Notification access.
 */
class MediaWatchService : NotificationListenerService() {
    override fun onListenerConnected() = Unit
    override fun onListenerDisconnected() = Unit
}
