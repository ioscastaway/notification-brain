package com.ioscastaway.notificationbrain.brain

import com.ioscastaway.notificationbrain.data.LessonRecord
import com.ioscastaway.notificationbrain.data.NotificationRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonTest {
    private val row = NotificationRecord.from(facts(), "Shop", Verdict.DISMISS, "rule:x", "because")

    @Test fun `only rows with a chip become lessons, and they keep the label and the facts`() {
        assertNull(LessonRecord.from(row, 1))
        val lesson = LessonRecord.from(row.copy(feedbackChip = FeedbackChip.WAS_IMPORTANT), 1)!!
        assertEquals(Label.IMPORTANT, lesson.label)
        assertEquals(facts().packageName, lesson.packageName)
        assertEquals(facts().text, lesson.text)
    }

    @Test fun `a lesson still stops a contradicting rule after the row is gone`() {
        val lesson = LessonRecord.from(row.copy(feedbackChip = FeedbackChip.WAS_IMPORTANT), 1)!!
        val candidate = PolicyLearner().apply(Policy.seed(), facts(), Feedback(FeedbackChip.DISMISS_LIKE_THIS, null, 2))
        val verdict = PolicyCourt(OWN).judge(Policy.seed(), candidate, listOf(lesson.asReplayCase()))
        assertTrue(verdict is Adoption.Rejected)
        assertTrue(lesson.asReplayCase().recordId < 0) // never collides with an archive row id
    }
}
