package com.ioscastaway.notificationbrain.data

import com.ioscastaway.notificationbrain.brain.Adoption
import com.ioscastaway.notificationbrain.brain.Decision
import com.ioscastaway.notificationbrain.brain.Feedback
import com.ioscastaway.notificationbrain.brain.FeedbackChip
import com.ioscastaway.notificationbrain.brain.NotificationFacts
import com.ioscastaway.notificationbrain.brain.Outcome
import com.ioscastaway.notificationbrain.brain.Policy
import com.ioscastaway.notificationbrain.brain.PolicyCourt
import com.ioscastaway.notificationbrain.brain.PolicyEngine
import com.ioscastaway.notificationbrain.brain.PolicyLearner
import com.ioscastaway.notificationbrain.brain.Replay
import com.ioscastaway.notificationbrain.brain.ReplayReport
import com.ioscastaway.notificationbrain.platform.PolicyStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The one place that writes the archive and the policy. The listener calls [recordPosted] and
 * [recordRemoved]; the UI calls [giveFeedback], which runs learner → court → store.
 */
class BrainRepository(
    private val dao: BrainDao,
    private val policyStore: PolicyStore,
    private val learner: PolicyLearner,
    private val court: PolicyCourt,
    private val ownPackage: String,
) {
    private val policyLock = Mutex()

    suspend fun recordPosted(facts: NotificationFacts, appLabel: String, decision: Decision): Long =
        dao.insert(NotificationRecord.from(facts, appLabel, decision.verdict, decision.ruleId, decision.because))

    suspend fun recordRemoved(key: String, outcome: Outcome, at: Long) {
        val pending = dao.pendingByKey(key) ?: return
        dao.update(pending.copy(outcome = outcome, removedAt = at))
    }

    fun dismissed(): Flow<List<NotificationRecord>> = dao.dismissed()
    fun swipedCandidates(): Flow<List<NotificationRecord>> = dao.swipedCandidates()
    fun dismissedSince(since: Long): Flow<Int> = dao.dismissedSince(since)
    fun seenSince(since: Long): Flow<Int> = dao.seenSince(since)
    fun falseDismissals(): Flow<Int> = dao.falseDismissals()
    fun crashes(): Flow<List<CrashRecord>> = dao.crashes()
    suspend fun dismissedCountSince(since: Long) = dao.dismissedCountSince(since)
    suspend fun record(id: Long) = dao.byId(id)

    /**
     * Stores the feedback on the record, derives a candidate policy, and lets the court decide.
     * The record being judged is part of the history with its new label, so "dismiss ones like
     * this" on a channel that earlier earned "was important" is refused.
     */
    suspend fun giveFeedback(recordId: Long, chip: FeedbackChip, note: String?): Adoption = policyLock.withLock {
        val record = dao.byId(recordId) ?: return Adoption.Unchanged
        val now = System.currentTimeMillis()
        val updated = record.copy(feedbackChip = chip, feedbackNote = note?.takeIf { it.isNotBlank() }, feedbackAt = now)
        dao.update(updated)

        val current = policyStore.current()
        val candidate = learner.apply(current, record.facts(), Feedback(chip, note, now))
        val history = dao.history().map { if (it.id == recordId) updated.asReplayCase() else it.asReplayCase() }
        val verdict = court.judge(current, candidate, history)
        if (verdict is Adoption.Adopted) policyStore.save(verdict.policy)
        verdict
    }

    /** Replays the current policy against the archive, for the Lab screen. */
    suspend fun replayCurrent(): ReplayReport {
        val engine = PolicyEngine({ policyStore.current() }, ownPackage)
        return Replay.run(engine, dao.history().map { it.asReplayCase() })
    }

    suspend fun resetPolicy() = policyLock.withLock { policyStore.save(Policy.seed()) }

    suspend fun prune(olderThanMs: Long = 60L * 24 * 60 * 60 * 1000): Int =
        dao.pruneBefore(System.currentTimeMillis() - olderThanMs)

    suspend fun insertCrash(crash: CrashRecord) = dao.insertCrash(crash)
    suspend fun newestExitInfo(): Long? = dao.newestExitInfo()
}
