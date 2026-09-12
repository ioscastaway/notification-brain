package com.ioscastaway.notificationbrain.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyEngineTest {
    private val dismissShop = Policy(rules = listOf(dismissRule("com.example.shop")))

    @Test fun `default is keep`() {
        val d = engine(Policy.seed()).classify(facts())
        assertEquals(Verdict.KEEP, d.verdict)
        assertEquals("default", d.ruleId)
    }

    @Test fun `first matching rule wins`() {
        val p = Policy(rules = listOf(keepRule("com.example.shop", "promo"), dismissRule("com.example.shop")))
        assertEquals(Verdict.KEEP, engine(p).classify(facts(channel = "promo")).verdict)
        assertEquals(Verdict.DISMISS, engine(p).classify(facts(channel = "orders")).verdict)
    }

    @Test fun `hard keep beats any dismiss rule`() {
        val e = engine(dismissShop)
        assertTrue(e.classify(facts(ongoing = true)).ruleId.startsWith("hard:"))
        assertTrue(e.classify(facts(clearable = false)).ruleId.startsWith("hard:"))
        assertTrue(e.classify(facts(summary = true)).ruleId.startsWith("hard:"))
        assertTrue(e.classify(facts(category = "call")).ruleId.startsWith("hard:"))
        assertTrue(e.classify(facts(category = "alarm")).ruleId.startsWith("hard:"))
        assertEquals("hard:own", e.classify(facts(pkg = OWN)).ruleId)
        assertEquals(Verdict.DISMISS, e.classify(facts()).verdict)
    }

    @Test fun `one-time codes are never dismissed`() {
        val e = engine(dismissShop)
        listOf(
            "Your verification code is 482910",
            "[Shop] 인증번호 [123456]를 입력해 주세요",
            "OTP: 9981",
            "G-552314 is your code",
            "Use code 77 88 99",  // not a code by our definition (no 4-8 digit run), so dismissed
        ).forEachIndexed { i, body ->
            val d = e.classify(facts(text = body))
            if (i < 4) assertEquals(body, "hard:one-time-code", d.ruleId) else assertEquals(body, Verdict.DISMISS, d.verdict)
        }
    }

    @Test fun `text matches are case-insensitive substrings and regex`() {
        val p = Policy(rules = listOf(
            Rule("r1", Verdict.DISMISS, Match(textContains = "% OFF"), RuleOrigin.SEED, "", 0),
            Rule("r2", Verdict.DISMISS, Match(textRegex = "^Weekend"), RuleOrigin.SEED, "", 0),
        ))
        assertEquals("rule:r1", engine(p).classify(facts()).ruleId)
        assertEquals("rule:r2", engine(p).classify(facts(text = "nothing here")).ruleId)
        assertEquals("default", engine(p).classify(facts(title = "Order shipped", text = "On its way")).ruleId)
    }
}
