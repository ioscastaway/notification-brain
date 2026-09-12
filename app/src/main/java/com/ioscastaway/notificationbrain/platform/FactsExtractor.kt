package com.ioscastaway.notificationbrain.platform

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.ioscastaway.notificationbrain.brain.NotificationFacts

/** The only place that reads a `StatusBarNotification`. Everything past here is plain data. */
object FactsExtractor {
    fun from(sbn: StatusBarNotification, ranking: NotificationListenerService.RankingMap?): NotificationFacts {
        val n = sbn.notification
        val extras = n.extras
        val importance = ranking?.let {
            val r = NotificationListenerService.Ranking()
            if (it.getRanking(sbn.key, r)) r.importance else null
        }
        return NotificationFacts(
            key = sbn.key,
            packageName = sbn.packageName,
            channelId = n.channelId,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.takeIf { it.isNotBlank() },
            text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()?.takeIf { it.isNotBlank() },
            category = n.category,
            postedAt = sbn.postTime,
            isOngoing = sbn.isOngoing,
            isClearable = sbn.isClearable,
            isGroupSummary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            hasActions = !n.actions.isNullOrEmpty(),
            importance = importance,
        )
    }
}
