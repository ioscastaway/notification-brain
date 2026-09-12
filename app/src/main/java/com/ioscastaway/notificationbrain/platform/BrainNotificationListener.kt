package com.ioscastaway.notificationbrain.platform

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.ioscastaway.notificationbrain.App
import com.ioscastaway.notificationbrain.brain.Outcome
import com.ioscastaway.notificationbrain.brain.Verdict
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * The core of the experiment. The system hands every notification to this service after it has
 * been posted; we turn it into facts, ask the brain, archive it, and cancel it if the brain says
 * so. Removal callbacks come with a reason, which is where the free labels come from.
 */
class BrainNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val graph get() = App.graph

    override fun onListenerConnected() {
        Log.i(TAG, "listener connected")
        scope.launch { graph.summaryNotifier.update(graph.repository.dismissedCountSince(startOfToday())) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        if (sbn.packageName == packageName) return
        val facts = FactsExtractor.from(sbn, rankingMap)
        val decision = graph.classifier.classify(facts)
        val appLabel = appLabel(sbn.packageName)
        if (decision.verdict == Verdict.DISMISS) {
            // Archive first, then cancel. The contentIntent is cached before the system drops it.
            graph.contentIntentCache.put(sbn.key, sbn.notification.contentIntent)
        }
        scope.launch {
            graph.repository.recordPosted(facts, appLabel, decision)
            if (decision.verdict == Verdict.DISMISS) {
                runCatching { cancelNotification(sbn.key) }
                    .onFailure { Log.w(TAG, "cancel failed for ${sbn.key}", it) }
                graph.summaryNotifier.update(graph.repository.dismissedCountSince(startOfToday()))
            }
        }
        Log.d(TAG, "${decision.verdict} ${sbn.packageName}/${facts.channelId} via ${decision.ruleId}")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap?, reason: Int) {
        if (sbn.packageName == packageName) return
        val outcome = when (reason) {
            REASON_CANCEL -> Outcome.USER_SWIPED
            REASON_CANCEL_ALL -> Outcome.USER_CLEARED_ALL
            REASON_CLICK -> Outcome.USER_OPENED
            REASON_APP_CANCEL, REASON_APP_CANCEL_ALL -> Outcome.APP_REMOVED
            REASON_LISTENER_CANCEL, REASON_LISTENER_CANCEL_ALL -> Outcome.WE_DISMISSED
            else -> Outcome.OTHER
        }
        val at = System.currentTimeMillis()
        scope.launch { graph.repository.recordRemoved(sbn.key, outcome, at) }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun appLabel(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    companion object {
        private const val TAG = "BrainListener"

        fun startOfToday(): Long = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
