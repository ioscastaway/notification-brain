package com.ioscastaway.notificationbrain.brain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** An app the compiler may name in a rule. Labels are what the user types; packages are what rules need. */
@Serializable
data class KnownApp(val packageName: String, val label: String, val channels: List<String> = emptyList())

data class CompileContext(val knownApps: List<KnownApp>)

/**
 * What the model returns for one sentence. Kept as plain data so parsing is testable on the JVM
 * without the SDK. `action` is "KEEP" or "DISMISS"; match fields mirror [Match].
 */
@Serializable
data class CompiledRule(
    val action: String,
    val packageName: String? = null,
    val channelId: String? = null,
    val category: String? = null,
    val titleContains: String? = null,
    val textContains: String? = null,
    val textRegex: String? = null,
    val because: String = "",
) {
    fun verdict(): Verdict? = runCatching { Verdict.valueOf(action.uppercase()) }.getOrNull()
    fun match() = Match(packageName, channelId, category, titleContains, textContains, textRegex)
}

@Serializable
data class CompiledRules(
    val rules: List<CompiledRule> = emptyList(),
    /** One or two sentences: how the sentence was read, and anything it could not express. */
    val explanation: String = "",
    /** App names or wishes the compiler could not map to anything it knows. */
    val unresolved: List<String> = emptyList(),
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }
        fun parse(text: String): CompiledRules = json.decodeFromString(text)
    }
}

/** The seam to the model. Stage 1 implements it with Claude; tests use a fake. */
interface RuleCompiler {
    suspend fun compile(text: String, context: CompileContext): CompiledRules
}

/**
 * Prompt text lives here, not in the platform layer, so it can be unit-tested and so the
 * nightly reviser can share it later. The user's sentence is data, never instructions.
 */
object RuleCompilerPrompt {
    const val SYSTEM: String = """You turn one sentence a phone owner wrote about their notifications into rules for a notification filter.

Output rules only for what the sentence says. Each rule is KEEP or DISMISS with a match. All non-null match fields must hold at once. Match fields:
- packageName: exact Android package. Use only packages from the known-apps list; map app names the user wrote (any language) to those packages.
- channelId: exact channel id from the known-apps list for that package. Use it when the user means a kind of notification the app clearly separates (promotions, orders); otherwise leave null.
- category: Android notification category string (msg, email, promo, social, recommendation, status, reminder, event, transport, progress, service, err). Leave null unless the user clearly means a category.
- titleContains / textContains: case-insensitive substring in the title / in title+text. Use for words the user quoted or clearly meant literally.
- textRegex: only when a substring cannot express it.
- because: a short paraphrase of the user's reason for this rule, written in the same language the sentence is written in.

Rules are evaluated first-match-wins in the order you output them, so put exceptions (KEEP) before the broader DISMISS they carve out of. Never output a rule that could dismiss calls, alarms, or one-time codes; the filter blocks those anyway.

If the user names an app that is not in the known-apps list, do not guess a package: put the name in "unresolved" and explain. If the sentence is not about notifications, output no rules and explain.

Respond with JSON only, matching the schema you were given."""

    fun user(text: String, context: CompileContext): String = buildString {
        append("Known apps (label -> package, channels seen so far):\n")
        if (context.knownApps.isEmpty()) append("(none yet)\n")
        context.knownApps.forEach { app ->
            append("- ").append(app.label).append(" -> ").append(app.packageName)
            if (app.channels.isNotEmpty()) append("  channels: ").append(app.channels.joinToString(", "))
            append('\n')
        }
        append("\nThe owner's sentence:\n")
        append(text.trim())
    }

    /** JSON schema for structured output. Mirrors [CompiledRules]. */
    val schema: Map<String, Any?> = mapOf(
        "type" to "object",
        "additionalProperties" to false,
        "required" to listOf("rules", "explanation", "unresolved"),
        "properties" to mapOf(
            "rules" to mapOf(
                "type" to "array",
                "items" to mapOf(
                    "type" to "object",
                    "additionalProperties" to false,
                    "required" to listOf("action", "packageName", "channelId", "category", "titleContains", "textContains", "textRegex", "because"),
                    "properties" to mapOf(
                        "action" to mapOf("type" to "string", "enum" to listOf("KEEP", "DISMISS")),
                        "packageName" to mapOf("type" to listOf("string", "null")),
                        "channelId" to mapOf("type" to listOf("string", "null")),
                        "category" to mapOf("type" to listOf("string", "null")),
                        "titleContains" to mapOf("type" to listOf("string", "null")),
                        "textContains" to mapOf("type" to listOf("string", "null")),
                        "textRegex" to mapOf("type" to listOf("string", "null")),
                        "because" to mapOf("type" to "string"),
                    ),
                ),
            ),
            "explanation" to mapOf("type" to "string"),
            "unresolved" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
        ),
    )
}

/**
 * Pure edits of a [Policy] for instructions. Adoption prepends the compiled rules (latest wish
 * wins, exceptions the compiler ordered first stay first) and drops older rules with an identical
 * match, like [PolicyLearner]. Removal drops exactly the rules the instruction produced.
 */
object InstructionEditor {
    fun adopt(current: Policy, text: String, compiled: CompiledRules, at: Long): Policy {
        val id = "ins-${at}"
        val rules = compiled.rules.mapIndexedNotNull { i, c ->
            val verdict = c.verdict() ?: return@mapIndexedNotNull null
            val match = c.match()
            if (match == Match()) return@mapIndexedNotNull null // "(anything)" rules are never accepted
            Rule(
                id = "${verdict.name.lowercase()}-${slug(match.describe())}-${at % 100000}-$i",
                action = verdict,
                match = match,
                origin = RuleOrigin.INSTRUCTION,
                because = c.because.ifBlank { text.trim().take(80) },
                createdAt = at,
                instructionId = id,
            )
        }
        val newMatches = rules.map { it.match }.toSet()
        val kept = current.rules.filterNot { it.match in newMatches }
        return current.withRules(rules + kept, at).copy(
            instructions = current.instructions + Instruction(id, text.trim(), at, compiled.explanation),
        )
    }

    fun remove(current: Policy, instructionId: String, at: Long): Policy {
        if (current.instructions.none { it.id == instructionId }) return current
        return current.withRules(current.rules.filterNot { it.instructionId == instructionId }, at)
            .copy(instructions = current.instructions.filterNot { it.id == instructionId })
    }

    private fun slug(s: String) = s.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40)
}
