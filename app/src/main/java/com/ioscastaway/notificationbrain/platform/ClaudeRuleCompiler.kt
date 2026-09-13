package com.ioscastaway.notificationbrain.platform

import com.anthropic.client.AnthropicClient
import com.anthropic.core.JsonValue
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.ioscastaway.notificationbrain.brain.CompileContext
import com.ioscastaway.notificationbrain.brain.CompiledRules
import com.ioscastaway.notificationbrain.brain.RuleCompiler
import com.ioscastaway.notificationbrain.brain.RuleCompilerPrompt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.jvm.optionals.getOrNull

/**
 * One request, structured output, no loop. What leaves the device: the sentence the user typed
 * and the known-apps list (labels, packages, channel ids). Never a notification body.
 */
class ClaudeRuleCompiler(
    private val client: AnthropicClient,
    private val model: String,
) : RuleCompiler {

    override suspend fun compile(text: String, context: CompileContext): CompiledRules = withContext(Dispatchers.IO) {
        val schema = JsonOutputFormat.Schema.builder().apply {
            RuleCompilerPrompt.schema.forEach { (k, v) -> putAdditionalProperty(k, JsonValue.from(v)) }
        }.build()
        val params = MessageCreateParams.builder()
            .model(model)
            .maxTokens(4096L)
            .system(RuleCompilerPrompt.SYSTEM)
            // Adaptive thinking is the model default; effort keeps a small task cheap.
            .outputConfig(
                OutputConfig.builder()
                    .effort(OutputConfig.Effort.MEDIUM)
                    .format(JsonOutputFormat.builder().schema(schema).build())
                    .build(),
            )
            .addUserMessage(RuleCompilerPrompt.user(text, context))
            .build()
        val response = client.messages().create(params)
        if (response.stopReason().getOrNull() == StopReason.REFUSAL) {
            throw IllegalStateException("The model declined to compile this sentence.")
        }
        val json = response.content().mapNotNull { it.text().getOrNull()?.text() }.joinToString("")
        CompiledRules.parse(json)
    }
}
