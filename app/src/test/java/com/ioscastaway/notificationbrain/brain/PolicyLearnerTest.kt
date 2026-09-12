package com.ioscastaway.notificationbrain.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PolicyLearnerTest {
    private val learner = PolicyLearner()

    @Test fun `was important adds a keep rule for app and channel at the front`() {
        val current = Policy(rules = listOf(dismissRule("com.example.shop")))
        val next = learner.apply(current, facts(), Feedback(FeedbackChip.WAS_IMPORTANT, "it was my order", 5_000))
        assertEquals(2, next.rules.size)
        assertEquals(Verdict.KEEP, next.rules[0].action)
        assertEquals(Match("com.example.shop", "promo"), next.rules[0].match)
        assertEquals(RuleOrigin.FEEDBACK, next.rules[0].origin)
        assertEquals(current.version + 1, next.version)
        assertEquals(Verdict.KEEP, engine(next).classify(facts()).verdict)
    }

    @Test fun `identical match is replaced, not duplicated`() {
        val current = Policy(rules = listOf(dismissRule("com.example.shop", "promo")))
        val next = learner.apply(current, facts(), Feedback(FeedbackChip.WAS_IMPORTANT, null, 5_000))
        assertEquals(1, next.rules.size)
        assertEquals(Verdict.KEEP, next.rules[0].action)
    }

    @Test fun `good call is a label, not a rule`() {
        val current = Policy.seed()
        assertSame(current, learner.apply(current, facts(), Feedback(FeedbackChip.GOOD_CALL, null, 1)))
    }

    @Test fun `app-wide chips ignore the channel`() {
        val next = learner.apply(Policy.seed(), facts(), Feedback(FeedbackChip.ALWAYS_DISMISS_APP, null, 1))
        assertEquals(Match("com.example.shop"), next.rules.single().match)
        assertEquals(Verdict.DISMISS, engine(next).classify(facts(channel = "anything")).verdict)
    }
}
