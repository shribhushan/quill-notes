package com.quillnotes.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class RepeatRuleTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val ist = TimeZone.getTimeZone("Asia/Kolkata")
    private val newYork = TimeZone.getTimeZone("America/New_York")

    private fun at(zone: TimeZone, y: Int, m: Int, d: Int, h: Int = 9, min: Int = 0): Long =
        Calendar.getInstance(zone).apply { clear(); set(y, m - 1, d, h, min, 0) }.timeInMillis

    @Test fun `codes round trip`() {
        for (r in RepeatRule.PRESETS) assertEquals(r, RepeatRule.parse(r.code))
        assertEquals("2W", RepeatRule(RepeatUnit.WEEK, 2).code)
    }

    @Test fun `bad codes parse to null instead of throwing`() {
        for (bad in listOf(null, "", "D", "0D", "-1D", "xD", "1Q", "1.5D", "999D", "1d"))
            assertNull("'$bad'", RepeatRule.parse(bad))
    }

    @Test fun `daily moves one day on and keeps the time of day`() {
        val due = at(ist, 2026, 11, 14, 18, 30)
        assertEquals(at(ist, 2026, 11, 15, 18, 30), RepeatRule.EVERY_DAY.nextAfter(due, due, ist))
    }

    @Test fun `an overdue task jumps to the next future occurrence, not just one step`() {
        val due = at(ist, 2026, 11, 1, 9)
        val now = at(ist, 2026, 11, 14, 12)
        assertEquals(at(ist, 2026, 11, 15, 9), RepeatRule.EVERY_DAY.nextAfter(due, now, ist))
        assertEquals(at(ist, 2026, 11, 15, 9), RepeatRule.EVERY_WEEK.nextAfter(due, now, ist))
        assertEquals(at(ist, 2026, 11, 29, 9), RepeatRule(RepeatUnit.WEEK, 4).nextAfter(due, now, ist))
    }

    @Test fun `the result is strictly after now even when now equals an occurrence`() {
        val due = at(ist, 2026, 11, 14, 9)
        assertEquals(at(ist, 2026, 11, 21, 9), RepeatRule.EVERY_WEEK.nextAfter(due, at(ist, 2026, 11, 14, 9), ist))
    }

    @Test fun `monthly on the 31st clamps for short months without drifting`() {
        val jan31 = at(utc, 2026, 1, 31)
        val rule = RepeatRule.EVERY_MONTH
        assertEquals(at(utc, 2026, 2, 28), rule.nextAfter(jan31, jan31, utc))
        // Counting from the original anchor, March is still the 31st.
        assertEquals(at(utc, 2026, 3, 31), rule.nextAfter(jan31, at(utc, 2026, 3, 1), utc))
        assertEquals(at(utc, 2026, 4, 30), rule.nextAfter(jan31, at(utc, 2026, 4, 1), utc))
    }

    @Test fun `yearly on Feb 29 lands on Feb 28 in common years`() {
        val leap = at(utc, 2024, 2, 29)
        assertEquals(at(utc, 2025, 2, 28), RepeatRule.EVERY_YEAR.nextAfter(leap, leap, utc))
        assertEquals(at(utc, 2028, 2, 29), RepeatRule.EVERY_YEAR.nextAfter(leap, at(utc, 2027, 6, 1), utc))
    }

    @Test fun `wall clock time survives a daylight saving change`() {
        // US clocks go forward on 8 March 2026: 24h later is not the same wall time.
        val due = at(newYork, 2026, 3, 7, 9)
        val next = RepeatRule.EVERY_DAY.nextAfter(due, due, newYork)
        assertEquals(at(newYork, 2026, 3, 8, 9), next)
        assertEquals(23 * 3_600_000L, next - due)
    }

    @Test fun `priority levels`() {
        assertEquals(Priority.HIGH, Priority.of(3))
        assertEquals(Priority.NONE, Priority.of(99))
        assertTrue(Priority.HIGH.level > Priority.LOW.level)
    }
}
