package com.ioscastaway.notificationbrain.brain

/**
 * Stage-1 classifier: a fixed hard-keep guard, then the ordered rules of the current [Policy],
 * then keep. [policy] is a function so the engine always sees the latest adopted policy without
 * being rebuilt.
 */
class PolicyEngine(
    private val policy: () -> Policy,
    private val ownPackage: String,
) : NotificationClassifier {

    override fun classify(facts: NotificationFacts): Decision {
        HardKeep.check(facts, ownPackage)?.let { return it }
        for (rule in policy().rules) {
            if (rule.match.matches(facts)) {
                return Decision(rule.action, "rule:${rule.id}", rule.because)
            }
        }
        return Decision(Verdict.KEEP, "default", "No rule matched; the brain keeps what it does not understand.")
    }
}

/**
 * Things the brain must never dismiss, no matter what it has learned. The single fatal failure
 * mode of this experiment is "it learned well and then swallowed a one-time code", so this list
 * is code, not policy, and feedback cannot touch it.
 */
object HardKeep {
    private val keepCategories = setOf("call", "alarm", "navigation", "missed_call", "sys", "err")
    private val keepPackages = setOf(
        "android",
        "com.android.systemui",
        "com.android.phone",
        "com.android.server.telecom",
        "com.samsung.android.dialer",
        "com.samsung.android.incallui",
        "com.google.android.dialer",
    )

    /** "code 123456", "인증번호 482910", "OTP: 9981", "verification code is 55231". */
    private val oneTimeCode = Regex(
        """(?:code|otp|passcode|verification|verify|인증\s*번호|인증코드|확인\s*코드)\D{0,20}\b\d{4,8}\b|\b\d{4,8}\b\D{0,20}(?:code|otp|인증\s*번호|인증코드)""",
        RegexOption.IGNORE_CASE,
    )

    fun check(f: NotificationFacts, ownPackage: String): Decision? = when {
        f.packageName == ownPackage -> keep("own", "Our own notification.")
        f.isOngoing -> keep("ongoing", "Ongoing notification; the system would ignore the cancel anyway.")
        !f.isClearable -> keep("not-clearable", "Not clearable by the user, so not by us.")
        f.isGroupSummary -> keep("group-summary", "Group summary; cancelling it can take children with it.")
        f.category in keepCategories -> keep("category", "Category \"${f.category}\" is never touched.")
        f.packageName in keepPackages -> keep("system-package", "System or dialer package.")
        oneTimeCode.containsMatchIn(f.combinedText) -> keep("one-time-code", "Looks like a one-time code.")
        else -> null
    }

    private fun keep(name: String, because: String) = Decision(Verdict.KEEP, "hard:$name", because)
}
