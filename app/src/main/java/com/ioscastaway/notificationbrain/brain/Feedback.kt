package com.ioscastaway.notificationbrain.brain

/**
 * What the user can say about one archived notification. Chips are cheap on purpose: a single tap
 * is enough to change the policy. The free-text note is stored and, in stage 1.5, read by the
 * nightly reviser; the learner ignores it.
 */
enum class FeedbackChip(val title: String, val appliesTo: Verdict) {
    /** "Yes, that was noise." Confirms a dismissal; no rule change, but a strong label for replay. */
    GOOD_CALL("Good call", Verdict.DISMISS),
    /** "I needed that." Adds a KEEP rule for this app + channel. */
    WAS_IMPORTANT("Was important", Verdict.DISMISS),
    /** Adds a KEEP rule for the whole app. */
    NEVER_TOUCH_APP("Never touch this app", Verdict.DISMISS),
    /** On something the brain kept: adds a DISMISS rule for this app + channel. */
    DISMISS_LIKE_THIS("Dismiss ones like this", Verdict.KEEP),
    /** Adds a DISMISS rule for the whole app. */
    ALWAYS_DISMISS_APP("Always dismiss this app", Verdict.KEEP),
    /** On something the brain kept: confirms it, adds a KEEP rule for this app + channel. */
    KEEP_LIKE_THIS("Keep ones like this", Verdict.KEEP),
    ;

    /** The label this chip implies for replay: was the notification important or noise? */
    val label: Label
        get() = when (this) {
            GOOD_CALL, DISMISS_LIKE_THIS, ALWAYS_DISMISS_APP -> Label.NOISE
            WAS_IMPORTANT, NEVER_TOUCH_APP, KEEP_LIKE_THIS -> Label.IMPORTANT
        }
}

data class Feedback(val chip: FeedbackChip, val note: String? = null, val at: Long)

/** Ground truth for replay, derived from explicit feedback first and observed behaviour second. */
enum class Label { IMPORTANT, NOISE, UNKNOWN }

/** How a notification left the shade. Mirrors `NotificationListenerService.REASON_*`. */
enum class Outcome {
    /** Still in the shade, or we never heard. */
    PENDING,
    /** We cancelled it. */
    WE_DISMISSED,
    /** The user swiped this one away (REASON_CANCEL). Strong implicit "noise". */
    USER_SWIPED,
    /** "Clear all" (REASON_CANCEL_ALL). Weak signal; not used as a label. */
    USER_CLEARED_ALL,
    /** The user tapped it (REASON_CLICK). Implicit "important". */
    USER_OPENED,
    /** The posting app removed it itself. */
    APP_REMOVED,
    /** Timeout, channel banned, package changed, and so on. */
    OTHER,
}

object Labeler {
    /** Explicit feedback wins. Otherwise a quick, targeted swipe is noise and a tap is important. */
    fun label(feedback: FeedbackChip?, outcome: Outcome, msInShade: Long?): Label {
        feedback?.let { return it.label }
        return when (outcome) {
            Outcome.USER_OPENED -> Label.IMPORTANT
            Outcome.USER_SWIPED -> if (msInShade != null && msInShade <= QUICK_SWIPE_MS) Label.NOISE else Label.UNKNOWN
            else -> Label.UNKNOWN
        }
    }

    /** A swipe within this window is read as "I did not even want to see that". */
    const val QUICK_SWIPE_MS: Long = 10 * 60 * 1000
}
