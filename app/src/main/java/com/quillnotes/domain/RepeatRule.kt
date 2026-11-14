package com.quillnotes.domain

import java.util.Calendar
import java.util.TimeZone

enum class RepeatUnit(val code: Char, internal val calendarField: Int) {
    DAY('D', Calendar.DAY_OF_YEAR),
    WEEK('W', Calendar.WEEK_OF_YEAR),
    MONTH('M', Calendar.MONTH),
    YEAR('Y', Calendar.YEAR)
}

/**
 * "Every [interval] [unit]s" for recurring tasks, stored on the note as a short code
 * such as `1D`, `2W` or `1M`.
 */
data class RepeatRule(val unit: RepeatUnit, val interval: Int = 1) {
    init {
        require(interval in 1..MAX_INTERVAL) { "interval out of range: $interval" }
    }

    val code: String get() = "$interval${unit.code}"

    /**
     * The first occurrence strictly after [now], counting forward from [anchor] (normally the
     * task's current due date). Every step is computed from the anchor rather than from the
     * previous step, so a monthly task due on the 31st does not drift to the 28th for good
     * after passing through February.
     */
    fun nextAfter(anchor: Long, now: Long, zone: TimeZone = TimeZone.getDefault()): Long {
        val cal = Calendar.getInstance(zone)
        for (step in 1..MAX_STEPS) {
            cal.timeInMillis = anchor
            cal.add(unit.calendarField, interval * step)
            if (cal.timeInMillis > now) return cal.timeInMillis
        }
        return anchor
    }

    companion object {
        const val MAX_INTERVAL = 365
        private const val MAX_STEPS = 20_000

        val EVERY_DAY = RepeatRule(RepeatUnit.DAY)
        val EVERY_WEEK = RepeatRule(RepeatUnit.WEEK)
        val EVERY_TWO_WEEKS = RepeatRule(RepeatUnit.WEEK, 2)
        val EVERY_MONTH = RepeatRule(RepeatUnit.MONTH)
        val EVERY_YEAR = RepeatRule(RepeatUnit.YEAR)

        /** The choices offered in the editor. */
        val PRESETS: List<RepeatRule> = listOf(EVERY_DAY, EVERY_WEEK, EVERY_TWO_WEEKS, EVERY_MONTH, EVERY_YEAR)

        /** Returns null for null, blank or malformed codes (so a bad value never crashes a screen). */
        fun parse(code: String?): RepeatRule? {
            if (code.isNullOrBlank() || code.length < 2) return null
            val unit = RepeatUnit.entries.firstOrNull { it.code == code.last() } ?: return null
            val interval = code.dropLast(1).toIntOrNull() ?: return null
            if (interval !in 1..MAX_INTERVAL) return null
            return RepeatRule(unit, interval)
        }
    }
}

/** Task priority, stored as 0–3 on the note. */
enum class Priority(val level: Int) {
    NONE(0), LOW(1), MEDIUM(2), HIGH(3);

    companion object {
        fun of(level: Int): Priority = entries.firstOrNull { it.level == level } ?: NONE
    }
}
