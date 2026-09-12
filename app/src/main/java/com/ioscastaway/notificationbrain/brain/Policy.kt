package com.ioscastaway.notificationbrain.brain

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The part of the app that is allowed to change at runtime. Serialized to `files/policy.json`,
 * rewritten by [PolicyLearner] on every piece of feedback, and only adopted after [PolicyCourt]
 * has replayed it against the archive.
 *
 * Rules are ordered; the first match wins. Anything no rule matches is kept. The hard-keep guard in
 * [PolicyEngine] runs before the rules and is deliberately not part of this file.
 */
@Serializable
data class Policy(
    val version: Int = 1,
    val revisedAt: Long = 0L,
    val rules: List<Rule> = emptyList(),
    /** Free-form, model- or learner-written explanation of why the rules look like they do. */
    val notes: String = "",
    /** Natural-language rules the user typed, each tied to the [Rule]s it compiled into. */
    val instructions: List<Instruction> = emptyList(),
) {
    fun withRules(newRules: List<Rule>, at: Long): Policy =
        copy(version = version + 1, revisedAt = at, rules = newRules)

    companion object {
        val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
        fun parse(text: String): Policy = json.decodeFromString(text)
        fun seed(): Policy = Policy(
            version = 1,
            notes = "Seed policy. No dismiss rules yet: the brain keeps everything until you teach it " +
                "from the Learn tab. The hard-keep guard (ongoing, calls, alarms, one-time codes) is " +
                "in code, not here, and cannot be overridden by feedback.",
        )
    }

    fun encode(): String = json.encodeToString(this)
}

@Serializable
data class Rule(
    val id: String,
    val action: Verdict,
    val match: Match,
    val origin: RuleOrigin,
    val because: String,
    val createdAt: Long,
    /** Set when the rule was compiled from an [Instruction]; removing the instruction removes it. */
    val instructionId: String? = null,
)

/**
 * One sentence the user wrote, kept verbatim. The rules are what the engine runs; the text is
 * what the user meant, and what the nightly reviser (stage 1.5) will read.
 */
@Serializable
data class Instruction(
    val id: String,
    val text: String,
    val createdAt: Long,
    /** The compiler's own one-line explanation of how it read the sentence. */
    val explanation: String = "",
)

@Serializable
enum class RuleOrigin {
    /** Written by hand in the seed policy. */
    SEED,
    /** Derived directly from a feedback chip the user tapped. */
    FEEDBACK,
    /** Written by a model during a nightly revision (stage 1.5, not yet wired). */
    REVISER,
    /** Compiled by a model from a sentence the user typed on the Rules tab. */
    INSTRUCTION,
    /** Proposed by [AutoLearner] from repeated quick swipes; adopted only after the court. */
    OBSERVED,
}

/**
 * All non-null fields must match. Text matches are case-insensitive substrings; `textRegex` is a
 * regular expression against title and text joined by a newline.
 */
@Serializable
data class Match(
    val packageName: String? = null,
    val channelId: String? = null,
    val category: String? = null,
    val titleContains: String? = null,
    val textContains: String? = null,
    val textRegex: String? = null,
) {
    fun matches(f: NotificationFacts): Boolean {
        if (packageName != null && packageName != f.packageName) return false
        if (channelId != null && channelId != f.channelId) return false
        if (category != null && category != f.category) return false
        if (titleContains != null && f.title?.contains(titleContains, ignoreCase = true) != true) return false
        if (textContains != null && !f.combinedText.contains(textContains, ignoreCase = true)) return false
        if (textRegex != null && !Regex(textRegex, RegexOption.IGNORE_CASE).containsMatchIn(f.combinedText)) return false
        return true
    }

    /** Human-readable, for the Policy screen and for rule ids. */
    fun describe(): String = buildList {
        packageName?.let { add(it) }
        channelId?.let { add("channel=$it") }
        category?.let { add("category=$it") }
        titleContains?.let { add("title~\"$it\"") }
        textContains?.let { add("text~\"$it\"") }
        textRegex?.let { add("regex=/$it/") }
    }.joinToString(" ").ifEmpty { "(anything)" }
}
