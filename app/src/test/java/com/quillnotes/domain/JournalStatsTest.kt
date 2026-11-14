package com.quillnotes.domain

import com.quillnotes.data.local.entity.NoteEntity
import com.quillnotes.data.local.entity.NoteType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class JournalStatsTest {
    private val zone = TimeZone.getTimeZone("Asia/Kolkata")

    private fun at(y: Int, m: Int, d: Int, h: Int = 10): Long =
        Calendar.getInstance(zone).apply { clear(); set(y, m - 1, d, h, 0, 0) }.timeInMillis

    private fun entry(ts: Long, mood: String? = null) =
        NoteEntity(title = "e", content = "", type = NoteType.JOURNAL, createdAt = ts, updatedAt = ts, mood = mood)

    @Test fun `grid covers whole weeks and marks the month`() {
        // November 2026 starts on a Sunday; with Monday as first day it needs a lead of 6 days.
        val grid = JournalStats.monthGrid(at(2026, 11, 14), Calendar.MONDAY, zone)
        assertTrue(grid.all { it.size == 7 })
        assertEquals(JournalStats.dayStart(at(2026, 10, 26), zone), grid.first().first().dayStart)
        assertFalse(grid.first().first().inMonth)
        assertEquals(6, grid.first().count { !it.inMonth })
        val inMonth = grid.flatten().filter { it.inMonth }
        assertEquals(30, inMonth.size)
        assertEquals(JournalStats.dayStart(at(2026, 11, 30), zone), inMonth.last().dayStart)
    }

    @Test fun `grid with sunday first starts on the 1st for november 2026`() {
        val grid = JournalStats.monthGrid(at(2026, 11, 3), Calendar.SUNDAY, zone)
        assertEquals(JournalStats.dayStart(at(2026, 11, 1), zone), grid.first().first().dayStart)
        assertTrue(grid.first().all { it.inMonth })
    }

    @Test fun `grid rows are consecutive days`() {
        val days = JournalStats.monthGrid(at(2026, 2, 10), Calendar.MONDAY, zone).flatten().map { it.dayStart }
        for (i in 1 until days.size) {
            val gap = Calendar.getInstance(zone).apply { timeInMillis = days[i - 1]; add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
            assertEquals(gap, days[i])
        }
    }

    @Test fun `february needs four rows when it starts on the first column`() {
        // Feb 2027 starts on a Monday: exactly four Monday-first weeks.
        assertEquals(4, JournalStats.monthGrid(at(2027, 2, 15), Calendar.MONDAY, zone).size)
    }

    @Test fun `month shifting handles year ends`() {
        val dec = JournalStats.startOfMonth(at(2026, 12, 20), zone)
        assertEquals(JournalStats.startOfMonth(at(2027, 1, 1), zone), JournalStats.shiftMonths(dec, 1, zone))
        assertEquals(JournalStats.startOfMonth(at(2026, 11, 1), zone), JournalStats.shiftMonths(dec, -1, zone))
    }

    @Test fun `summary counts moods, days and the average score`() {
        val nov = JournalStats.startOfMonth(at(2026, 11, 1), zone)
        val entries = listOf(
            entry(at(2026, 11, 2), "happy"), entry(at(2026, 11, 2, 20), "calm"), entry(at(2026, 11, 5), "sad"),
            entry(at(2026, 11, 9), null), entry(at(2026, 10, 31), "happy"), entry(at(2026, 12, 1), "happy")
        )
        val s = JournalStats.summarize(entries, nov, zone)
        assertEquals(4, s.entryCount)
        assertEquals(3, s.daysWritten)
        assertEquals(mapOf("happy" to 1, "calm" to 1, "sad" to 1), s.moodCounts)
        assertEquals((2 + 1 - 2) / 3.0, s.averageScore!!, 1e-9)
        assertEquals(1.5, s.dailyScores[JournalStats.dayStart(at(2026, 11, 2), zone)]!!, 1e-9)
    }

    @Test fun `an empty month has no average`() {
        val s = JournalStats.summarize(emptyList(), JournalStats.startOfMonth(at(2026, 11, 1), zone), zone)
        assertEquals(0, s.entryCount)
        assertNull(s.averageScore)
    }

    @Test fun `on this day finds earlier years only, newest first`() {
        val now = at(2026, 11, 14)
        val entries = listOf(
            entry(at(2025, 11, 14), "happy"), entry(at(2023, 11, 14)), entry(at(2026, 11, 14)),
            entry(at(2025, 11, 15)), entry(at(2024, 10, 14))
        )
        assertEquals(listOf(at(2025, 11, 14), at(2023, 11, 14)), JournalStats.onThisDay(entries, now, zone).map { it.createdAt })
    }

    @Test fun `mood scores`() {
        assertEquals(2, JournalStats.moodScore("happy"))
        assertEquals(-2, JournalStats.moodScore("sad"))
        assertNull(JournalStats.moodScore("elated"))
        assertNull(JournalStats.moodScore(null))
    }

    // ── planner ──

    private fun task(due: Long?, priority: Int = 0, done: Boolean = false, updated: Long = 0) =
        NoteEntity(title = "t", content = "", type = NoteType.TASK, dueDate = due, priority = priority, isCompleted = done, updatedAt = updated)

    @Test fun `week starts on the chosen day and has seven days`() {
        val wed = at(2026, 11, 18)
        assertEquals(JournalStats.dayStart(at(2026, 11, 16), zone), PlannerStats.weekStart(wed, Calendar.MONDAY, zone))
        assertEquals(JournalStats.dayStart(at(2026, 11, 15), zone), PlannerStats.weekStart(wed, Calendar.SUNDAY, zone))
        val days = PlannerStats.weekDays(PlannerStats.weekStart(wed, Calendar.MONDAY, zone), zone)
        assertEquals(7, days.size)
        assertEquals(JournalStats.dayStart(at(2026, 11, 22), zone), days.last())
        assertEquals(PlannerStats.weekStart(at(2026, 11, 25), Calendar.MONDAY, zone),
            PlannerStats.shiftWeeks(PlannerStats.weekStart(wed, Calendar.MONDAY, zone), 1, zone))
    }

    @Test fun `tasks group by due day and sort by priority within a day`() {
        val d = at(2026, 11, 16)
        val low = task(d + 1000, priority = 1)
        val high = task(d + 5000, priority = 3)
        val done = task(d + 100, priority = 3, done = true)
        val other = task(at(2026, 11, 17))
        val byDay = PlannerStats.tasksByDay(listOf(low, done, other, high, task(null)), zone)
        assertEquals(2, byDay.size)
        assertEquals(listOf(high, low, done), byDay[JournalStats.dayStart(d, zone)])
    }
}
