package com.ioscastaway.notificationbrain.brain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class DigestTest {
    private val now: Long = Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, 13, 15, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    private val day = 24L * 60 * 60 * 1000
    private fun item(id: Long, pkg: String, at: Long, reviewed: Boolean = false) =
        Digest.Item(id, pkg.substringAfterLast('.'), pkg, "t$id", null, at, reviewed)

    @Test fun `groups by day newest first, then by app with unreviewed first`() {
        val groups = Digest.group(listOf(
            item(1, "com.a", now - 1000),
            item(2, "com.b", now - 2000, reviewed = true),
            item(3, "com.b", now - 3000, reviewed = true),
            item(4, "com.a", now - day),
            item(5, "com.c", now - 9 * day),
        ), now)
        assertEquals(listOf("Today", "Yesterday"), groups.take(2).map { it.label })
        assertEquals(3, groups.size)
        val today = groups[0]
        assertEquals(3, today.total)
        assertEquals(1, today.unreviewed)
        assertEquals(listOf("com.a", "com.b"), today.apps.map { it.packageName })
        assertEquals(listOf(1L, 2L, 3L), today.ids)
        assertEquals(listOf(4L), groups[1].ids)
    }

    @Test fun `labels for the last week and dates beyond`() {
        val groups = Digest.group(listOf(item(1, "x", now - 3 * day), item(2, "x", now - 30 * day)), now)
        assertEquals("3 days ago", groups[0].label)
        assert(groups[1].label.contains("2026") || groups[1].label.matches(Regex(".*\\d.*")))
    }
}
