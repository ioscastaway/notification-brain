package com.ioscastaway.notificationbrain.platform

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.ioscastaway.notificationbrain.App
import com.ioscastaway.notificationbrain.brain.HardKeep
import com.ioscastaway.notificationbrain.brain.Outcome
import com.ioscastaway.notificationbrain.brain.Verdict
import com.ioscastaway.notificationbrain.data.NotificationRecord
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
        instance = this
        scope.launch { graph.summaryNotifier.update(graph.repository.dismissedCountSince(startOfToday()), graph.repository.unreviewedDismissedCount()) }
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        super.onListenerDisconnected()
    }

    /**
     * What is in the shade right now, as archive rows the Teach tab can act on. Hard-kept entries
     * (group summaries, ongoing, system, calls) are left out: nothing the user says can change them.
     */
    suspend fun shade(): List<NotificationRecord> {
        val active = runCatching { activeNotifications?.toList() }.getOrNull().orEmpty()
            .filter { it.packageName != packageName }
            .map { FactsExtractor.from(it, currentRanking) to appLabel(it.packageName) }
            .filter { (facts, _) -> HardKeep.check(facts, packageName) == null }
        return graph.repository.shadeRows(active)
    }

    /**
     * Re-judges the shade with the current policy and cancels what it now says to dismiss. Called
     * after a policy is adopted, so teaching "dismiss ones like this" acts on the one you pointed at.
     */
    suspend fun applyPolicyToShade(): Int {
        val active = runCatching { activeNotifications?.toList() }.getOrNull().orEmpty()
            .filter { it.packageName != packageName }
        val byKey = active.associateBy { it.key }
        var dismissed = 0
        for (row in graph.repository.shadeRows(active.map { FactsExtractor.from(it, currentRanking) to appLabel(it.packageName) })) {
            if (row.verdict != Verdict.KEEP) continue
            val decision = graph.classifier.classify(row.facts())
            if (decision.verdict != Verdict.DISMISS) continue
            val sbn = byKey[row.key] ?: continue
            graph.contentIntentCache.put(sbn.key, sbn.notification.contentIntent)
            graph.repository.markDismissedNow(row, decision)
            runCatching { cancelNotification(sbn.key) }.onFailure { Log.w(TAG, "cancel failed for ${sbn.key}", it) }
            dismissed++
        }
        if (dismissed > 0) graph.summaryNotifier.update(graph.repository.dismissedCountSince(startOfToday()), graph.repository.unreviewedDismissedCount())
        return dismissed
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
                graph.summaryNotifier.update(graph.repository.dismissedCountSince(startOfToday()), graph.repository.unreviewedDismissedCount())
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
        scope.launch {
            graph.repository.recordRemoved(sbn.key, outcome, at)
            if (outcome == Outcome.USER_SWIPED) {
                val learned = graph.repository.applyOutcome(graph.repository.learnFromSwipes())
                if (learned != null) Log.i(TAG, "learned from swipes: $learned")
            }
        }
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        scope.cancel()
        super.onDestroy()
    }

    private fun appLabel(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    companion object {
        private const val TAG = "BrainListener"

        /** The bound service, while the system keeps it bound. Null means "not connected". */
        @Volatile var instance: BrainNotificationListener? = null
            private set

        fun startOfToday(): Long = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
