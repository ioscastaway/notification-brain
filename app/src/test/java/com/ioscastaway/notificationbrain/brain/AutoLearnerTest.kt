package com.ioscastaway.notificationbrain.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoLearnerTest {
    private val learner = AutoLearner(minSwipes = 3)
    private fun swipe(pkg: String = "com.example.shop", ch: String? = "promo", ms: Long = 30_000) =
        AutoLearner.Observation(pkg, ch, Outcome.USER_SWIPED, ms, false, Labeler.label(null, Outcome.USER_SWIPED, ms))
    private fun opened(pkg: String = "com.example.shop", ch: String? = "promo") =
        AutoLearner.Observation(pkg, ch, Outcome.USER_OPENED, 5_000, false, Label.IMPORTANT)

    @Test fun `three quick swipes and no taps propose a dismiss for that channel`() {
        val p = learner.propose(Policy.seed(), listOf(swipe(), swipe(), swipe(), swipe(ch = "orders")))
        assertEquals(listOf(AutoLearner.Proposal(Match("com.example.shop", "promo"), 3)), p)
    }

    @Test fun `a single tap vetoes the channel`() {
        assertTrue(learner.propose(Policy.seed(), listOf(swipe(), swipe(), swipe(), opened())).isEmpty())
    }

    @Test fun `slow swipes and clear-all do not count`() {
        val slow = swipe(ms = Labeler.QUICK_SWIPE_MS + 1)
        val all = AutoLearner.Observation("com.example.shop", "promo", Outcome.USER_CLEARED_ALL, 1_000, false, Label.UNKNOWN)
        assertTrue(learner.propose(Policy.seed(), listOf(swipe(), swipe(), slow, all)).isEmpty())
    }

    @Test fun `explicit feedback or an existing rule means the user already spoke`() {
        val withFeedback = swipe().copy(hadFeedback = true)
        assertTrue(learner.propose(Policy.seed(), listOf(swipe(), swipe(), withFeedback)).isEmpty())
        val ruled = Policy(rules = listOf(keepRule("com.example.shop", "promo")))
        assertTrue(learner.propose(ruled, listOf(swipe(), swipe(), swipe())).isEmpty())
    }

    @Test fun `observed rules go after explicit ones and the court still applies`() {
        val current = Policy(rules = listOf(keepRule("com.other")))
        val next = learner.apply(current, listOf(AutoLearner.Proposal(Match("com.example.shop", "promo"), 3)), 9)
        assertEquals(RuleOrigin.OBSERVED, next.rules.last().origin)
        assertEquals(Verdict.KEEP, next.rules.first().action)
        assertEquals(Verdict.DISMISS, engine(next).classify(facts()).verdict)

        val contradicted = listOf(ReplayCase(facts(channel = "promo", postedAt = 1), Label.IMPORTANT))
        assertTrue(PolicyCourt(OWN).judge(current, next, contradicted) is Adoption.Rejected)
    }
}
