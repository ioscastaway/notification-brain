package com.ioscastaway.notificationbrain.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicySerializationTest {
    @Test fun `round trip keeps rules and order`() {
        val p = Policy(
            version = 7, revisedAt = 42,
            rules = listOf(keepRule("a", "b"), dismissRule("c"), Rule("re", Verdict.DISMISS, Match(textRegex = "^x"), RuleOrigin.REVISER, "why", 9)),
            notes = "hello",
        )
        assertEquals(p, Policy.parse(p.encode()))
    }

    @Test fun `unknown keys are ignored so older builds can read newer files`() {
        val text = Policy.seed().encode().replaceFirst("{", "{\"futureField\": 1,")
        assertEquals(Policy.seed(), Policy.parse(text))
    }

    @Test fun `labeler prefers explicit feedback and reads a quick swipe as noise`() {
        assertEquals(Label.IMPORTANT, Labeler.label(FeedbackChip.WAS_IMPORTANT, Outcome.USER_SWIPED, 1))
        assertEquals(Label.NOISE, Labeler.label(null, Outcome.USER_SWIPED, 1000))
        assertEquals(Label.UNKNOWN, Labeler.label(null, Outcome.USER_SWIPED, Labeler.QUICK_SWIPE_MS + 1))
        assertEquals(Label.IMPORTANT, Labeler.label(null, Outcome.USER_OPENED, null))
        assertEquals(Label.UNKNOWN, Labeler.label(null, Outcome.USER_CLEARED_ALL, 1))
        assertTrue(FeedbackChip.entries.all { it.title.isNotBlank() })
    }
}
