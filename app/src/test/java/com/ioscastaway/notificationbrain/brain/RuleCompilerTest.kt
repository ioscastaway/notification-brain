package com.ioscastaway.notificationbrain.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleCompilerTest {
    @Test fun `compiled rules parse from model JSON, nulls and unknown keys included`() {
        val json = """
            {"rules":[{"action":"DISMISS","packageName":"com.example.shop","channelId":null,"category":null,
              "titleContains":null,"textContains":"% off","textRegex":null,"because":"sales","extra":1}],
             "explanation":"ok","unresolved":["Some App"]}
        """.trimIndent()
        val parsed = CompiledRules.parse(json)
        assertEquals(1, parsed.rules.size)
        assertEquals(Verdict.DISMISS, parsed.rules[0].verdict())
        assertEquals(Match(packageName = "com.example.shop", textContains = "% off"), parsed.rules[0].match())
        assertEquals(listOf("Some App"), parsed.unresolved)
    }

    @Test fun `user prompt lists known apps with channels and ends with the sentence`() {
        val ctx = CompileContext(listOf(KnownApp("com.example.shop", "Shop", listOf("promo", "orders")), KnownApp("com.b", "Bank")))
        val p = RuleCompilerPrompt.user("no more shop promos", ctx)
        assertTrue(p.contains("Shop -> com.example.shop  channels: promo, orders"))
        assertTrue(p.contains("- Bank -> com.b\n"))
        assertTrue(p.trimEnd().endsWith("no more shop promos"))
    }

    @Test fun `schema requires every field so the model cannot omit a match column`() {
        @Suppress("UNCHECKED_CAST")
        val props = RuleCompilerPrompt.schema["properties"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val items = (props["rules"] as Map<String, Any?>)["items"] as Map<String, Any?>
        assertEquals(listOf("action", "packageName", "channelId", "category", "titleContains", "textContains", "textRegex", "because"), items["required"])
    }

    @Test fun `policy with instructions round-trips`() {
        val p = InstructionEditor.adopt(Policy.seed(), "hello", CompiledRules(listOf(CompiledRule("KEEP", packageName = "a")), "e"), 9)
        assertEquals(p, Policy.parse(p.encode()))
    }
}
