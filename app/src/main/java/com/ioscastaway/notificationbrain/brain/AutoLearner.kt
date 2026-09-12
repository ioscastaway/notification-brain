package com.ioscastaway.notificationbrain.brain

/**
 * Learning from watching, no chip required. When the user has swiped the same app + channel away
 * quickly, several times, and never opened one, that channel is noise by revealed preference and
 * the learner proposes a DISMISS rule for it. The proposal goes through [PolicyCourt] like every
 * other change, so a channel that once earned "was important" is never auto-dismissed.
 *
 * Deliberately conservative: only quick targeted swipes count ([Labeler.QUICK_SWIPE_MS]), a single
 * tap on the channel vetoes it, and explicit feedback of any kind means the user already spoke.
 */
class AutoLearner(
    private val minSwipes: Int = DEFAULT_MIN_SWIPES,
) {
    /** One archived row reduced to what this learner needs. */
    data class Observation(
        val packageName: String,
        val channelId: String?,
        val outcome: Outcome,
        val msInShade: Long?,
        val hadFeedback: Boolean,
        val label: Label,
    )

    data class Proposal(val match: Match, val swipes: Int)

    fun propose(current: Policy, history: List<Observation>): List<Proposal> {
        val alreadyRuled = current.rules.map { it.match }.toSet()
        return history
            .groupBy { Match(it.packageName, it.channelId) }
            .filterKeys { it !in alreadyRuled }
            .mapNotNull { (match, rows) ->
                if (rows.any { it.outcome == Outcome.USER_OPENED || it.label == Label.IMPORTANT }) return@mapNotNull null
                if (rows.any { it.hadFeedback }) return@mapNotNull null
                val quickSwipes = rows.count { it.outcome == Outcome.USER_SWIPED && (it.msInShade ?: Long.MAX_VALUE) <= Labeler.QUICK_SWIPE_MS }
                if (quickSwipes < minSwipes) null else Proposal(match, quickSwipes)
            }
            .sortedByDescending { it.swipes }
    }

    fun apply(current: Policy, proposals: List<Proposal>, at: Long): Policy {
        if (proposals.isEmpty()) return current
        val rules = proposals.map { p ->
            Rule(
                id = "dismiss-observed-${p.match.describe().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40)}-${at % 100000}",
                action = Verdict.DISMISS,
                match = p.match,
                origin = RuleOrigin.OBSERVED,
                because = "You swiped ${p.swipes} of these away within minutes and never opened one.",
                createdAt = at,
            )
        }
        // Observed rules go after everything the user said explicitly, so a typed or tapped rule wins.
        return current.withRules(current.rules + rules, at)
    }

    companion object {
        const val DEFAULT_MIN_SWIPES = 3
    }
}
