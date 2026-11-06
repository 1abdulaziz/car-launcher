package dev.carlauncher

import android.service.notification.NotificationListenerService

/**
 * Empty notification listener: Android only lets an app read OTHER apps' media sessions (title, artist, controls) if it
 * holds notification-listener access. Granted once over ADB:
 *   adb shell cmd notification allow_listener dev.carlauncher/dev.carlauncher.MediaListener
 */
class MediaListener : NotificationListenerService()
