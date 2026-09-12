package com.ioscastaway.notificationbrain.platform

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * "Reopen" for an archived notification. A `PendingIntent` cannot be persisted, so this works only
 * while the listener process is alive; afterwards we fall back to launching the app. This limit is
 * documented in the README and is the honest answer to "can I get the notification back": you get
 * the app, and the content if we are still running.
 */
class ContentIntentCache(private val context: Context, private val capacity: Int = 500) {
    private val map = object : LinkedHashMap<String, PendingIntent>(64, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PendingIntent>?) = size > capacity
    }

    @Synchronized
    fun put(key: String, intent: PendingIntent?) {
        if (intent != null) map[key] = intent
    }

    sealed class Reopened {
        data object Content : Reopened()
        data object AppOnly : Reopened()
        data object Nothing : Reopened()
    }

    @Synchronized
    fun reopen(key: String, packageName: String): Reopened {
        map[key]?.let { pi ->
            runCatching { pi.send(); return Reopened.Content }
                .onFailure { Log.i(TAG, "contentIntent for $key is stale", it) }
            map.remove(key)
        }
        val launch = context.packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return Reopened.Nothing
        context.startActivity(launch)
        return Reopened.AppOnly
    }

    private companion object { const val TAG = "ContentIntentCache" }
}
