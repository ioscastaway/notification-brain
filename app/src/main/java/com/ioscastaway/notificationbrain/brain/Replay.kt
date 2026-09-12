package com.ioscastaway.notificationbrain.brain

/** One archived notification with the label we have for it, ready to be re-judged. */
data class ReplayCase(val facts: NotificationFacts, val label: Label, val recordId: Long = 0)

data class ReplayReport(
    val total: Int,
    val labeled: Int,
    /** Cases the candidate would dismiss although the user said they were important. */
    val falseDismissals: List<ReplayCase>,
    /** Cases the candidate would keep although the user said they were noise. */
    val missedNoise: List<ReplayCase>,
    val wouldDismiss: Int,
) {
    /** The only hard requirement. Missed noise is a coverage number, not a failure. */
    val passes: Boolean get() = falseDismissals.isEmpty()

    fun summary(): String = buildString {
        append("$total cases, $labeled labeled, $wouldDismiss would be dismissed. ")
        append("${falseDismissals.size} false dismissal(s), ${missedNoise.size} missed noise.")
    }
}

/**
 * Re-runs a classifier over everything the brain has seen. This is the in-app test suite: the app
 * cannot run instrumentation tests on itself, so every candidate policy is tried here first.
 */
object Replay {
    fun run(classifier: NotificationClassifier, cases: List<ReplayCase>): ReplayReport {
        val falseDismissals = ArrayList<ReplayCase>()
        val missedNoise = ArrayList<ReplayCase>()
        var wouldDismiss = 0
        var labeled = 0
        for (c in cases) {
            val d = classifier.classify(c.facts)
            if (d.verdict == Verdict.DISMISS) wouldDismiss++
            if (c.label != Label.UNKNOWN) labeled++
            when {
                c.label == Label.IMPORTANT && d.verdict == Verdict.DISMISS -> falseDismissals += c
                c.label == Label.NOISE && d.verdict == Verdict.KEEP && !d.ruleId.startsWith("hard:") -> missedNoise += c
            }
        }
        return ReplayReport(cases.size, labeled, falseDismissals, missedNoise, wouldDismiss)
    }
}

sealed class Adoption {
    data class Adopted(val policy: Policy, val report: ReplayReport) : Adoption()
    data class Rejected(val candidate: Policy, val report: ReplayReport) : Adoption()
    data object Unchanged : Adoption()
}

/**
 * The courtroom. A candidate policy is adopted only if, replayed against the labeled history, it
 * would not have dismissed anything the user marked important. The candidate that came from the
 * feedback being judged is included in the history by the caller, so a wish that contradicts an
 * earlier "was important" on the same channel is refused instead of silently winning.
 */
class PolicyCourt(private val ownPackage: String) {
    fun judge(current: Policy, candidate: Policy, history: List<ReplayCase>): Adoption {
        if (candidate.rules == current.rules) return Adoption.Unchanged
        val engine = PolicyEngine({ candidate }, ownPackage)
        val report = Replay.run(engine, history)
        return if (report.passes) Adoption.Adopted(candidate, report) else Adoption.Rejected(candidate, report)
    }
}
