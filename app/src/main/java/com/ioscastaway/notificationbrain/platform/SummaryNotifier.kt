package com.ioscastaway.notificationbrain.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.ioscastaway.notificationbrain.R
import com.ioscastaway.notificationbrain.ui.MainActivity

/**
 * The one notification we post ourselves: a silent "N tidied today" that opens the archive.
 * Low importance (so the channel itself is silent), and hard-kept by package so the brain never eats its own receipt.
 */
class SummaryNotifier(private val context: Context) {
    private val nm = context.getSystemService(NotificationManager::class.java)

    init {
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Tidy summary", NotificationManager.IMPORTANCE_LOW).apply {
                description = "How many notifications the brain dismissed today. Tap to review them."
                setShowBadge(false)
            },
        )
    }

    fun update(dismissedToday: Int, unreviewed: Int = 0) {
        if (!canPost()) return
        if (dismissedToday <= 0 && unreviewed <= 0) {
            nm.cancel(ID)
            return
        }
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_ARCHIVE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(if (dismissedToday > 0) "$dismissedToday tidied today" else "$unreviewed to review")
            .setContentText(if (unreviewed > 0) "$unreviewed not yet reviewed. Tap to catch up, undo, or teach the brain." else "All reviewed. Tap to undo or teach the brain.")
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .build()
        nm.notify(ID, n)
    }

    private fun canPost(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val CHANNEL = "tidy-summary"
        const val ID = 1
    }
}
