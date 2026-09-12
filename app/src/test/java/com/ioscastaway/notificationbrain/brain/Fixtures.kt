package com.ioscastaway.notificationbrain.brain

const val OWN = "com.ioscastaway.notificationbrain"

fun facts(
    pkg: String = "com.example.shop",
    channel: String? = "promo",
    title: String? = "Weekend deal",
    text: String? = "50% off everything, today only",
    category: String? = null,
    ongoing: Boolean = false,
    clearable: Boolean = true,
    summary: Boolean = false,
    postedAt: Long = 1_000_000L,
    key: String = "$pkg|$postedAt",
) = NotificationFacts(
    key = key, packageName = pkg, channelId = channel, title = title, text = text, category = category,
    postedAt = postedAt, isOngoing = ongoing, isClearable = clearable, isGroupSummary = summary,
)

fun engine(policy: Policy) = PolicyEngine({ policy }, OWN)

fun dismissRule(pkg: String, channel: String? = null, id: String = "d-$pkg-$channel") =
    Rule(id, Verdict.DISMISS, Match(pkg, channel), RuleOrigin.SEED, "test", 0)

fun keepRule(pkg: String, channel: String? = null, id: String = "k-$pkg-$channel") =
    Rule(id, Verdict.KEEP, Match(pkg, channel), RuleOrigin.SEED, "test", 0)
