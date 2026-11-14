package com.quillnotes.domain

import com.quillnotes.data.local.entity.NoteEntity
import java.util.Calendar
import java.util.TimeZone

/** Calendar and mood maths behind the journal's month view, trends and "on this day". */
object JournalStats {

    /** Mood keys from the mood selector, scored from happiest to saddest. */
    val MOOD_SCORES: Map<String, Int> = mapOf("happy" to 2, "calm" to 1, "neutral" to 0, "anxious" to -1, "sad" to -2)

    fun moodScore(key: String?): Int? = key?.let { MOOD_SCORES[it] }

    data class DayCell(val dayStart: Long, val inMonth: Boolean)

    private fun cal(ts: Long, zone: TimeZone) = Calendar.getInstance(zone).apply { timeInMillis = ts }

    fun dayStart(ts: Long, zone: TimeZone = TimeZone.getDefault()): Long = cal(ts, zone).apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun startOfMonth(ts: Long, zone: TimeZone = TimeZone.getDefault()): Long = cal(ts, zone).apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun shiftMonths(monthStart: Long, delta: Int, zone: TimeZone = TimeZone.getDefault()): Long =
        cal(monthStart, zone).apply { add(Calendar.MONTH, delta) }.timeInMillis

    /** Whole weeks (rows of seven) covering the month containing [monthAnchor]. */
    fun monthGrid(
        monthAnchor: Long,
        firstDayOfWeek: Int = Calendar.getInstance().firstDayOfWeek,
        zone: TimeZone = TimeZone.getDefault()
    ): List<List<DayCell>> {
        val first = startOfMonth(monthAnchor, zone)
        val month = cal(first, zone).get(Calendar.MONTH)
        val c = cal(first, zone)
        val lead = (c.get(Calendar.DAY_OF_WEEK) - firstDayOfWeek + 7) % 7
        c.add(Calendar.DAY_OF_YEAR, -lead)
        val rows = ArrayList<List<DayCell>>()
        do {
            val week = ArrayList<DayCell>(7)
            repeat(7) {
                week += DayCell(c.timeInMillis, c.get(Calendar.MONTH) == month)
                c.add(Calendar.DAY_OF_YEAR, 1)
            }
            rows += week
        } while (c.get(Calendar.MONTH) == month)
        return rows
    }

    data class MonthSummary(
        val monthStart: Long,
        val entryCount: Int,
        val daysWritten: Int,
        /** Entries per mood key (entries without a mood are not counted). */
        val moodCounts: Map<String, Int>,
        val averageScore: Double?,
        /** Average mood score for each day that has one, keyed by day start. */
        val dailyScores: Map<Long, Double>
    )

    fun summarize(entries: List<NoteEntity>, monthStart: Long, zone: TimeZone = TimeZone.getDefault()): MonthSummary {
        val end = shiftMonths(monthStart, 1, zone)
        val inMonth = entries.filter { it.createdAt in monthStart until end }
        val counts = LinkedHashMap<String, Int>()
        val perDay = HashMap<Long, MutableList<Int>>()
        for (e in inMonth) {
            val key = e.mood ?: continue
            val score = moodScore(key) ?: continue
            counts[key] = (counts[key] ?: 0) + 1
            perDay.getOrPut(dayStart(e.createdAt, zone)) { mutableListOf() } += score
        }
        val all = perDay.values.flatten()
        return MonthSummary(
            monthStart = monthStart,
            entryCount = inMonth.size,
            daysWritten = inMonth.map { dayStart(it.createdAt, zone) }.distinct().size,
            moodCounts = counts.entries.sortedByDescending { it.value }.associate { it.key to it.value },
            averageScore = if (all.isEmpty()) null else all.average(),
            dailyScores = perDay.mapValues { it.value.average() }
        )
    }

    /** Entries written on today's month and day in earlier years, most recent year first. */
    fun onThisDay(entries: List<NoteEntity>, now: Long, zone: TimeZone = TimeZone.getDefault()): List<NoteEntity> {
        val today = cal(now, zone)
        val year = today.get(Calendar.YEAR)
        return entries.filter {
            val c = cal(it.createdAt, zone)
            c.get(Calendar.YEAR) < year &&
                c.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                c.get(Calendar.DAY_OF_MONTH) == today.get(Calendar.DAY_OF_MONTH)
        }.sortedByDescending { it.createdAt }
    }
}

/** Week-view and ordering helpers for the planner. */
object PlannerStats {
    fun weekStart(ts: Long, firstDayOfWeek: Int = Calendar.getInstance().firstDayOfWeek, zone: TimeZone = TimeZone.getDefault()): Long {
        val c = Calendar.getInstance(zone).apply { timeInMillis = JournalStats.dayStart(ts, zone) }
        val back = (c.get(Calendar.DAY_OF_WEEK) - firstDayOfWeek + 7) % 7
        c.add(Calendar.DAY_OF_YEAR, -back)
        return c.timeInMillis
    }

    /** Seven consecutive day starts beginning at [weekStart]. */
    fun weekDays(weekStart: Long, zone: TimeZone = TimeZone.getDefault()): List<Long> {
        val c = Calendar.getInstance(zone).apply { timeInMillis = weekStart }
        return List(7) { val d = c.timeInMillis; c.add(Calendar.DAY_OF_YEAR, 1); d }
    }

    fun shiftWeeks(weekStart: Long, delta: Int, zone: TimeZone = TimeZone.getDefault()): Long =
        Calendar.getInstance(zone).apply { timeInMillis = weekStart; add(Calendar.WEEK_OF_YEAR, delta) }.timeInMillis

    /** Tasks with a due date, grouped by the day they fall on; each day sorted by priority then time. */
    fun tasksByDay(tasks: List<NoteEntity>, zone: TimeZone = TimeZone.getDefault()): Map<Long, List<NoteEntity>> =
        tasks.filter { it.dueDate != null }
            .groupBy { JournalStats.dayStart(it.dueDate!!, zone) }
            .mapValues { (_, list) -> list.sortedWith(taskOrder) }

    /** Open tasks first, then higher priority, then earlier due time, then newest. */
    val taskOrder: Comparator<NoteEntity> =
        compareBy<NoteEntity> { it.isCompleted }
            .thenByDescending { it.priority }
            .thenBy { it.dueDate ?: Long.MAX_VALUE }
            .thenByDescending { it.updatedAt }
}
