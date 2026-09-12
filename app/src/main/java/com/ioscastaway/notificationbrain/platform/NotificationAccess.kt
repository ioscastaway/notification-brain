package com.ioscastaway.notificationbrain.platform

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

/** Notification access is granted in Settings, never from code. This only asks and points. */
object NotificationAccess {
    fun component(context: Context) = ComponentName(context, BrainNotificationListener::class.java)

    fun isGranted(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java)
            .isNotificationListenerAccessGranted(component(context))

    /** Deep link to our own row (API 30+); the generic list is the fallback. */
    fun settingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component(context).flattenToString())
}
