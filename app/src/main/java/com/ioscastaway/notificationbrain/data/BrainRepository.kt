package com.ioscastaway.notificationbrain.data

import com.ioscastaway.notificationbrain.brain.Adoption
import com.ioscastaway.notificationbrain.brain.AutoLearner
import com.ioscastaway.notificationbrain.brain.CompileContext
import com.ioscastaway.notificationbrain.brain.CompiledRules
import com.ioscastaway.notificationbrain.brain.InstructionEditor
import com.ioscastaway.notificationbrain.brain.KnownApp
import com.ioscastaway.notificationbrain.brain.RuleCompiler
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
import com.ioscastaway.notificationbrain.brain.ReplayCase
import com.ioscastaway.notificationbrain.brain.ReplayReport
import com.ioscastaway.notificationbrain.brain.Verdict
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
    private val autoLearner: AutoLearner,
    private val court: PolicyCourt,
    private val ownPackage: String,
    /** Null when the build carries no API key; the Rules tab explains instead of failing. */
    private val compiler: RuleCompiler?,
    private val installedApps: () -> List<KnownApp> = { emptyList() },
) {
    val canCompile: Boolean get() = compiler != null

    private val policyLock = Mutex()

    suspend fun recordPosted(facts: NotificationFacts, appLabel: String, decision: Decision): Long =
        dao.insert(NotificationRecord.from(facts, appLabel, decision.verdict, decision.ruleId, decision.because))

    suspend fun recordRemoved(key: String, outcome: Outcome, at: Long) {
        val pending = dao.pendingByKey(key) ?: return
        dao.update(pending.copy(outcome = outcome, removedAt = at))
    }

    /**
     * Learning from watching: after a targeted swipe, see whether any app + channel has now been
     * swiped away often enough, with no taps and no feedback, to earn a DISMISS rule on its own.
     * Same court as everything else.
     */
    suspend fun learnFromSwipes(): Adoption = policyLock.withLock {
        val current = policyStore.current()
        val history = dao.history()
        val proposals = autoLearner.propose(current, history.map { it.asObservation() })
        if (proposals.isEmpty()) return Adoption.Unchanged
        val candidate = autoLearner.apply(current, proposals, System.currentTimeMillis())
        val verdict = court.judge(current, candidate, history.map { it.asReplayCase() } + dao.lessons().map { it.asReplayCase() })
        if (verdict is Adoption.Adopted) policyStore.save(verdict.policy)
        verdict
    }

    /**
     * The archive rows for what is in the shade right now. Notifications that were posted before
     * the listener connected have no row yet; they get one (verdict KEEP, rule "backfill") so
     * they can be taught like any other.
     */
    suspend fun shadeRows(active: List<Pair<NotificationFacts, String>>): List<NotificationRecord> {
        val existing = dao.pendingByKeys(active.map { it.first.key }).groupBy { it.key }.mapValues { it.value.first() }
        return active.map { (facts, appLabel) ->
            existing[facts.key] ?: run {
                val id = dao.insert(NotificationRecord.from(facts, appLabel, Verdict.KEEP, "backfill", "Already in the shade when the listener connected."))
                dao.byId(id)!!
            }
        }
    }

    /** A pending KEEP row the new policy now dismisses. Caller cancels the notification. */
    suspend fun markDismissedNow(record: NotificationRecord, decision: Decision) {
        dao.update(record.copy(verdict = Verdict.DISMISS, ruleId = decision.ruleId, because = decision.because,
            outcome = Outcome.WE_DISMISSED, removedAt = System.currentTimeMillis()))
    }

    fun dismissed(): Flow<List<NotificationRecord>> = dao.dismissed()
    fun swipedCandidates(): Flow<List<NotificationRecord>> = dao.swipedCandidates()
    fun dismissedSince(since: Long): Flow<Int> = dao.dismissedSince(since)
    fun seenSince(since: Long): Flow<Int> = dao.seenSince(since)
    fun falseDismissals(): Flow<Int> = dao.falseDismissals()
    fun crashes(): Flow<List<CrashRecord>> = dao.crashes()
    fun unreviewedDismissed(): Flow<Int> = dao.unreviewedDismissed()
    fun recordCount(): Flow<Int> = dao.count()
    suspend fun unreviewedDismissedCount() = dao.unreviewedDismissedCount()

    suspend fun markReviewed(ids: List<Long>) = dao.markReviewed(ids, System.currentTimeMillis())
    suspend fun markAllReviewed() = dao.markAllReviewed(System.currentTimeMillis())

    /**
     * Deleting rows is the user's call; the app only prunes at 60 days. Rules are never touched by
     * a deletion, and neither is what the user taught: every row that carried a chip is first
     * copied to `lessons`, which the court reads alongside the archive.
     */
    suspend fun deleteRecords(ids: List<Long>): Int { keepLessons(dao.labeledIn(ids)); return dao.deleteIds(ids) }
    suspend fun deleteReviewed(): Int { keepLessons(dao.labeledReviewed()); return dao.deleteReviewed() }
    suspend fun deleteOlderThan(days: Int): Int {
        val before = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
        keepLessons(dao.labeledBefore(before)); return dao.pruneBefore(before)
    }
    suspend fun deleteAll(): Int { keepLessons(dao.labeledAll()); return dao.deleteAll() }
    fun lessonCount(): Flow<Int> = dao.lessonCount()

    private suspend fun keepLessons(rows: List<NotificationRecord>) {
        val now = System.currentTimeMillis()
        val lessons = rows.mapNotNull { LessonRecord.from(it, now) }
        if (lessons.isNotEmpty()) dao.insertLessons(lessons)
    }
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
        val history = replayHistory().map { if (it.recordId == recordId) updated.asReplayCase() else it }
        val verdict = court.judge(current, candidate, history)
        if (verdict is Adoption.Adopted) policyStore.save(verdict.policy)
        verdict
    }

    /** Archive-derived channels merged with launcher apps. Labels and ids only; no bodies. */
    suspend fun knownApps(): List<KnownApp> {
        val seen = dao.knownChannels().groupBy { it.packageName }.map { (pkg, rows) ->
            KnownApp(pkg, rows.first().appLabel, rows.mapNotNull { it.channelId }.distinct())
        }
        val seenPackages = seen.map { it.packageName }.toSet()
        return (seen + installedApps().filterNot { it.packageName in seenPackages }).sortedBy { it.label.lowercase() }
    }

    /** Step 1 of a typed rule: sentence → compiled rules, shown to the user before anything changes. */
    suspend fun compileInstruction(text: String): CompiledRules {
        val c = compiler ?: throw IllegalStateException("No API key in this build.")
        return c.compile(text, CompileContext(knownApps()))
    }

    /** Step 2: the user accepted the preview. Same court as feedback. */
    suspend fun adoptInstruction(text: String, compiled: CompiledRules): Adoption = policyLock.withLock {
        val current = policyStore.current()
        val candidate = InstructionEditor.adopt(current, text, compiled, System.currentTimeMillis())
        val verdict = court.judge(current, candidate, replayHistory())
        if (verdict is Adoption.Adopted) policyStore.save(verdict.policy)
        verdict
    }

    /** Removing a KEEP exception can expose a DISMISS below it, so removal is judged too. */
    suspend fun removeInstruction(instructionId: String): Adoption = policyLock.withLock {
        val current = policyStore.current()
        val candidate = InstructionEditor.remove(current, instructionId, System.currentTimeMillis())
        val verdict = court.judge(current, candidate, replayHistory())
        if (verdict is Adoption.Adopted) policyStore.save(verdict.policy)
        verdict
    }

    /** Log line for an adoption, or null when nothing happened. */
    fun applyOutcome(a: Adoption): String? = when (a) {
        is Adoption.Adopted -> "policy v${a.policy.version}: " + a.policy.rules.filter { it.origin == com.ioscastaway.notificationbrain.brain.RuleOrigin.OBSERVED }.joinToString { it.match.describe() }
        is Adoption.Rejected -> "rejected: ${a.report.summary()}"
        Adoption.Unchanged -> null
    }

    /** The court's history: every archived row, plus the lessons kept from deleted rows. */
    private suspend fun replayHistory(): List<ReplayCase> =
        dao.history().map { it.asReplayCase() } + dao.lessons().map { it.asReplayCase() }

    /** Replays the current policy against the archive, for the Lab screen. */
    suspend fun replayCurrent(): ReplayReport {
        val engine = PolicyEngine({ policyStore.current() }, ownPackage)
        return Replay.run(engine, replayHistory())
    }

    suspend fun resetPolicy() = policyLock.withLock { policyStore.save(Policy.seed()) }

    suspend fun prune(olderThanMs: Long = 60L * 24 * 60 * 60 * 1000): Int {
        val before = System.currentTimeMillis() - olderThanMs
        keepLessons(dao.labeledBefore(before))
        return dao.pruneBefore(before)
    }

    suspend fun insertCrash(crash: CrashRecord) = dao.insertCrash(crash)
    suspend fun newestExitInfo(): Long? = dao.newestExitInfo()
}
