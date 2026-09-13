package com.ioscastaway.notificationbrain.brain

import java.util.Calendar

/**
 * Groups dismissed notifications for catching up in bulk: by day, then by app, newest first.
 * Pure so the bucketing is testable; the UI only renders it.
 */
object Digest {
    data class Item(val id: Long, val appLabel: String, val packageName: String, val title: String?, val text: String?, val postedAt: Long, val reviewed: Boolean)
    data class AppGroup(val appLabel: String, val packageName: String, val items: List<Item>) {
        val unreviewed: Int get() = items.count { !it.reviewed }
    }
    data class DayGroup(val label: String, val dayStart: Long, val apps: List<AppGroup>) {
        val total: Int get() = apps.sumOf { it.items.size }
        val unreviewed: Int get() = apps.sumOf { it.unreviewed }
        val ids: List<Long> get() = apps.flatMap { g -> g.items.map { it.id } }
    }

    fun group(items: List<Item>, now: Long): List<DayGroup> {
        val today = startOfDay(now)
        return items.sortedByDescending { it.postedAt }
            .groupBy { startOfDay(it.postedAt) }
            .entries.sortedByDescending { it.key }
            .map { (day, rows) ->
                val apps = rows.groupBy { it.packageName }.values
                    .map { AppGroup(it.first().appLabel, it.first().packageName, it) }
                    .sortedWith(compareByDescending<AppGroup> { it.unreviewed }.thenByDescending { it.items.size })
                DayGroup(dayLabel(day, today), day, apps)
            }
    }

    fun startOfDay(ms: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun dayLabel(day: Long, today: Long): String {
        val diffDays = ((today - day) / (24L * 60 * 60 * 1000)).toInt()
        return when (diffDays) {
            0 -> "Today"
            1 -> "Yesterday"
            in 2..6 -> "$diffDays days ago"
            else -> java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(day))
        }
    }
}
