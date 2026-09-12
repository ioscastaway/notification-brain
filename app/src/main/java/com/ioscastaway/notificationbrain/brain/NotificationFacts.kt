package com.ioscastaway.notificationbrain.brain

import kotlinx.serialization.Serializable

/**
 * Everything the brain is allowed to know about one notification. Built once, in `platform/`,
 * from a `StatusBarNotification`; nothing in this package imports Android, so the same facts can
 * be replayed on the JVM and, later, handed to a loaded module (Evolving App stage 2).
 */
@Serializable
data class NotificationFacts(
    val key: String,
    val packageName: String,
    val channelId: String? = null,
    val title: String? = null,
    val text: String? = null,
    /** `Notification.CATEGORY_*` string, e.g. "msg", "call", "alarm". */
    val category: String? = null,
    val postedAt: Long,
    val isOngoing: Boolean = false,
    val isClearable: Boolean = true,
    val isGroupSummary: Boolean = false,
    val hasActions: Boolean = false,
    /** `NotificationManager.IMPORTANCE_*` from the ranking, when the listener had one. */
    val importance: Int? = null,
) {
    val combinedText: String get() = listOfNotNull(title, text).joinToString("\n")
}

enum class Verdict { KEEP, DISMISS }

/** The brain's answer for one notification, with the rule that produced it. */
data class Decision(
    val verdict: Verdict,
    /** `hard:<name>`, `rule:<id>`, or `default`. Stable strings; the archive stores them. */
    val ruleId: String,
    val because: String,
)

/** The seam the Evolving App series swaps out later. Stage 1 implements it with [PolicyEngine]. */
fun interface NotificationClassifier {
    fun classify(facts: NotificationFacts): Decision
}
