package com.ioscastaway.notificationbrain.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ioscastaway.notificationbrain.brain.AutoLearner
import com.ioscastaway.notificationbrain.brain.FeedbackChip
import com.ioscastaway.notificationbrain.brain.Label
import com.ioscastaway.notificationbrain.brain.Labeler
import com.ioscastaway.notificationbrain.brain.NotificationFacts
import com.ioscastaway.notificationbrain.brain.Outcome
import com.ioscastaway.notificationbrain.brain.ReplayCase
import com.ioscastaway.notificationbrain.brain.Verdict

/**
 * One notification the listener saw, what the brain decided, what happened to it afterwards, and
 * what the user said about it. Kept for every notification, not only dismissed ones: the kept ones
 * the user swiped away are the learning candidates.
 */
@Entity(tableName = "notifications", indices = [Index("key"), Index("postedAt")])
data class NotificationRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val packageName: String,
    val appLabel: String,
    val channelId: String?,
    val title: String?,
    val text: String?,
    val category: String?,
    val postedAt: Long,
    val isOngoing: Boolean,
    val isClearable: Boolean,
    val isGroupSummary: Boolean,
    val hasActions: Boolean,
    val importance: Int?,
    val verdict: Verdict,
    val ruleId: String,
    val because: String,
    val outcome: Outcome = Outcome.PENDING,
    val removedAt: Long? = null,
    val feedbackChip: FeedbackChip? = null,
    val feedbackNote: String? = null,
    val feedbackAt: Long? = null,
) {
    fun facts() = NotificationFacts(
        key, packageName, channelId, title, text, category, postedAt,
        isOngoing, isClearable, isGroupSummary, hasActions, importance,
    )

    val msInShade: Long? get() = removedAt?.let { it - postedAt }

    val label: Label get() = Labeler.label(feedbackChip, outcome, msInShade)

    fun asReplayCase() = ReplayCase(facts(), label, id)

    fun asObservation() = AutoLearner.Observation(packageName, channelId, outcome, msInShade, feedbackChip != null, label)

    companion object {
        fun from(facts: NotificationFacts, appLabel: String, verdict: Verdict, ruleId: String, because: String) =
            NotificationRecord(
                key = facts.key,
                packageName = facts.packageName,
                appLabel = appLabel,
                channelId = facts.channelId,
                title = facts.title,
                text = facts.text,
                category = facts.category,
                postedAt = facts.postedAt,
                isOngoing = facts.isOngoing,
                isClearable = facts.isClearable,
                isGroupSummary = facts.isGroupSummary,
                hasActions = facts.hasActions,
                importance = facts.importance,
                verdict = verdict,
                ruleId = ruleId,
                because = because,
                outcome = if (verdict == Verdict.DISMISS) Outcome.WE_DISMISSED else Outcome.PENDING,
                removedAt = if (verdict == Verdict.DISMISS) facts.postedAt else null,
            )
    }
}

/**
 * A crash or ANR of this app. Nothing acts on these yet; they are the input of Evolving App
 * stage 3 (self-diagnosis) and are collected from the first commit so the history exists by then.
 */
@Entity(tableName = "crashes", indices = [Index("timestamp")])
data class CrashRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    /** "uncaught" (our handler) or "exit-info" (ApplicationExitInfo after the fact). */
    val source: String,
    /** e.g. "CRASH", "ANR", "CRASH_NATIVE". */
    val reason: String,
    val description: String,
    val trace: String,
    val versionName: String,
    val gitSha: String,
)
