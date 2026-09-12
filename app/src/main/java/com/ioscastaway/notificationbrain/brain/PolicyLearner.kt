package com.ioscastaway.notificationbrain.brain

/**
 * Turns one piece of feedback into a candidate policy. Pure: the caller decides whether to adopt
 * the result (see [PolicyCourt]). New rules go to the front so the latest, most specific wish
 * wins, and any older rule with the identical match is dropped so the file does not accumulate
 * contradictions.
 */
class PolicyLearner {

    fun apply(current: Policy, facts: NotificationFacts, feedback: Feedback): Policy {
        val (action, match) = when (feedback.chip) {
            FeedbackChip.GOOD_CALL -> return current // a label, not a rule
            FeedbackChip.WAS_IMPORTANT, FeedbackChip.KEEP_LIKE_THIS -> Verdict.KEEP to Match(facts.packageName, facts.channelId)
            FeedbackChip.NEVER_TOUCH_APP -> Verdict.KEEP to Match(facts.packageName)
            FeedbackChip.DISMISS_LIKE_THIS -> Verdict.DISMISS to Match(facts.packageName, facts.channelId)
            FeedbackChip.ALWAYS_DISMISS_APP -> Verdict.DISMISS to Match(facts.packageName)
        }
        val because = buildString {
            append(feedback.chip.title)
            append(" on \"")
            append((facts.title ?: facts.text ?: "").take(40))
            append("\"")
            feedback.note?.takeIf { it.isNotBlank() }?.let { append(": ").append(it.trim()) }
        }
        val rule = Rule(
            id = ruleId(action, match, feedback.at),
            action = action,
            match = match,
            origin = RuleOrigin.FEEDBACK,
            because = because,
            createdAt = feedback.at,
        )
        val kept = current.rules.filterNot { it.match == match }
        return current.withRules(listOf(rule) + kept, feedback.at)
    }

    private fun ruleId(action: Verdict, match: Match, at: Long): String {
        val slug = match.describe().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(48)
        return "${action.name.lowercase()}-$slug-${at % 100000}"
    }
}
