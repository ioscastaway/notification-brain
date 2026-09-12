package com.ioscastaway.notificationbrain.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InstructionEditorTest {
    private val compiled = CompiledRules(
        rules = listOf(
            CompiledRule("KEEP", packageName = "com.example.shop", channelId = "orders", because = "delivery updates matter"),
            CompiledRule("DISMISS", packageName = "com.example.shop", because = "promotions are noise"),
        ),
        explanation = "Keep order updates, dismiss the rest of the shop app.",
    )

    @Test fun `adopt prepends compiled rules in the compiler's order and records the instruction`() {
        val current = Policy(rules = listOf(dismissRule("com.other")))
        val next = InstructionEditor.adopt(current, "shop promos out, orders stay", compiled, 7_000)
        assertEquals(3, next.rules.size)
        assertEquals(Verdict.KEEP, next.rules[0].action)
        assertEquals(Verdict.DISMISS, next.rules[1].action)
        assertEquals(RuleOrigin.INSTRUCTION, next.rules[0].origin)
        assertEquals(next.instructions.single().id, next.rules[0].instructionId)
        assertEquals("shop promos out, orders stay", next.instructions.single().text)
        assertEquals(current.version + 1, next.version)
        val e = engine(next)
        assertEquals(Verdict.KEEP, e.classify(facts(channel = "orders", text = "Order shipped")).verdict)
        assertEquals(Verdict.DISMISS, e.classify(facts(channel = "promo")).verdict)
    }

    @Test fun `identical matches from older rules are replaced`() {
        val current = Policy(rules = listOf(keepRule("com.example.shop")))
        val next = InstructionEditor.adopt(current, "x", compiled, 1)
        assertEquals(2, next.rules.size)
        assertTrue(next.rules.all { it.origin == RuleOrigin.INSTRUCTION })
    }

    @Test fun `rules with no match or an unknown action are dropped`() {
        val junk = CompiledRules(rules = listOf(CompiledRule("DISMISS"), CompiledRule("MAYBE", packageName = "a")))
        val next = InstructionEditor.adopt(Policy.seed(), "x", junk, 1)
        assertTrue(next.rules.isEmpty())
        assertEquals(1, next.instructions.size)
    }

    @Test fun `remove drops exactly that instruction's rules`() {
        val withOne = InstructionEditor.adopt(Policy(rules = listOf(dismissRule("com.other"))), "x", compiled, 1)
        val id = withOne.instructions.single().id
        val back = InstructionEditor.remove(withOne, id, 2)
        assertEquals(listOf(dismissRule("com.other")), back.rules)
        assertTrue(back.instructions.isEmpty())
        assertEquals(withOne, InstructionEditor.remove(withOne, "nope", 3))
    }

    @Test fun `court refuses an instruction that would dismiss something marked important`() {
        val history = listOf(ReplayCase(facts(channel = "promo", postedAt = 1), Label.IMPORTANT))
        val candidate = InstructionEditor.adopt(Policy.seed(), "x", compiled, 5)
        assertTrue(PolicyCourt(OWN).judge(Policy.seed(), candidate, history) is Adoption.Rejected)
    }

    @Test fun `removing a keep exception is judged too`() {
        val base = InstructionEditor.adopt(Policy.seed(), "x", compiled, 5)
        val history = listOf(ReplayCase(facts(channel = "orders", postedAt = 1), Label.IMPORTANT))
        val id = base.instructions.single().id
        // Removing the whole instruction also removes its DISMISS, so nothing is exposed: adopted.
        val removed = InstructionEditor.remove(base, id, 6)
        assertTrue(PolicyCourt(OWN).judge(base, removed, history) is Adoption.Adopted)
    }
}
