package com.ioscastaway.notificationbrain.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyCourtTest {
    private val court = PolicyCourt(OWN)
    private val learner = PolicyLearner()

    @Test fun `a rule that would dismiss something marked important is refused`() {
        val current = Policy.seed()
        val important = ReplayCase(facts(channel = "promo", postedAt = 1), Label.IMPORTANT)
        val swiped = ReplayCase(facts(channel = "promo", postedAt = 2), Label.NOISE)
        val candidate = learner.apply(current, swiped.facts, Feedback(FeedbackChip.DISMISS_LIKE_THIS, null, 3))

        val verdict = court.judge(current, candidate, listOf(important, swiped))
        assertTrue(verdict is Adoption.Rejected)
        val report = (verdict as Adoption.Rejected).report
        assertEquals(listOf(important), report.falseDismissals)
    }

    @Test fun `a rule that only removes noise is adopted`() {
        val current = Policy.seed()
        val history = listOf(
            ReplayCase(facts(channel = "promo", postedAt = 1), Label.NOISE),
            ReplayCase(facts(channel = "orders", postedAt = 2, text = "Order shipped"), Label.IMPORTANT),
            ReplayCase(facts(pkg = "com.example.bank", channel = "x", postedAt = 3), Label.UNKNOWN),
        )
        val candidate = learner.apply(current, history[0].facts, Feedback(FeedbackChip.DISMISS_LIKE_THIS, null, 4))
        val verdict = court.judge(current, candidate, history)
        assertTrue(verdict is Adoption.Adopted)
        val report = (verdict as Adoption.Adopted).report
        assertEquals(3, report.total)
        assertEquals(2, report.labeled)
        assertEquals(1, report.wouldDismiss)
        assertTrue(report.passes)
    }

    @Test fun `unchanged rules are not re-judged`() {
        val p = Policy.seed()
        assertEquals(Adoption.Unchanged, court.judge(p, p.copy(notes = "x"), emptyList()))
    }

    @Test fun `hard-kept noise is not counted as missed`() {
        val report = Replay.run(engine(Policy.seed()), listOf(ReplayCase(facts(category = "call"), Label.NOISE)))
        assertTrue(report.missedNoise.isEmpty())
    }
}
