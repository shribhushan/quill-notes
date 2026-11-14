package com.quillnotes.ui

import com.quillnotes.ui.util.dayLabel
import com.quillnotes.ui.util.dueLabel
import com.quillnotes.ui.util.joinTags
import com.quillnotes.ui.util.localToPickerDate
import com.quillnotes.ui.util.parseTags
import com.quillnotes.ui.util.pickerDateToLocal
import com.quillnotes.ui.util.readingMinutes
import com.quillnotes.ui.util.relativeTime
import com.quillnotes.ui.util.shiftDays
import com.quillnotes.ui.util.startOfDay
import com.quillnotes.ui.util.wordCount
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class FormattersTest {
    private lateinit var tz: TimeZone
    private lateinit var locale: Locale

    @Before fun pinZoneAndLocale() {
        tz = TimeZone.getDefault(); locale = Locale.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))
        Locale.setDefault(Locale.US)
    }

    @After fun restore() {
        TimeZone.setDefault(tz); Locale.setDefault(locale)
    }

    private fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0): Long =
        Calendar.getInstance().apply { clear(); set(y, m - 1, d, h, min, 0) }.timeInMillis

    @Test fun `start of day is local midnight`() {
        val ts = at(2026, 11, 14, 17, 45)
        assertEquals(at(2026, 11, 14, 0, 0), startOfDay(ts))
        assertEquals(startOfDay(ts), startOfDay(startOfDay(ts)))
    }

    @Test fun `shifting days is calendar based across a month and year end`() {
        assertEquals(at(2027, 1, 1, 0), shiftDays(at(2026, 12, 31, 0), 1))
        assertEquals(at(2026, 2, 28, 0), shiftDays(at(2026, 3, 1, 0), -1))
    }

    @Test fun `shifting days survives daylight saving`() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        val before = at(2026, 3, 7, 0)
        assertEquals(at(2026, 3, 8, 0), shiftDays(before, 1)) // a 23-hour day
        assertEquals(at(2026, 3, 9, 0), shiftDays(before, 2))
    }

    @Test fun `relative time wording`() {
        val now = at(2026, 11, 14, 15)
        assertEquals("Just now", relativeTime(now - 20_000, now))
        assertEquals("5m ago", relativeTime(now - 5 * 60_000, now))
        assertEquals("2h ago", relativeTime(now - 2 * 3_600_000L, now))
        assertEquals("Yesterday", relativeTime(at(2026, 11, 13, 9), now))
        assertEquals("Wednesday", relativeTime(at(2026, 11, 11, 9), now))
        assertEquals("Oct 1", relativeTime(at(2026, 10, 1, 9), now))
        assertEquals("Oct 1, 2025", relativeTime(at(2025, 10, 1, 9), now))
    }

    @Test fun `due and day labels`() {
        val now = at(2026, 11, 14, 10)
        assertEquals("Today", dueLabel(at(2026, 11, 14, 18), now))
        assertEquals("Tomorrow", dueLabel(at(2026, 11, 15, 9), now))
        assertEquals("Yesterday", dueLabel(at(2026, 11, 13, 9), now))
        assertEquals("Wednesday", dueLabel(at(2026, 11, 18, 9), now))
        assertEquals("Dec 25", dueLabel(at(2026, 12, 25, 9), now))
        assertEquals("Today", dayLabel(startOfDay(now), now))
        assertEquals("Yesterday", dayLabel(shiftDays(startOfDay(now), -1), now))
    }

    @Test fun `picker dates keep the picked calendar day whatever the zone`() {
        val utcMidnight = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { clear(); set(2026, 10, 14) }.timeInMillis
        val local = pickerDateToLocal(utcMidnight, hour = 9)
        assertEquals(at(2026, 11, 14, 9), local)
        assertEquals(utcMidnight, localToPickerDate(local))
        // A far-west zone must not slip a day.
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"))
        assertEquals(Calendar.getInstance().apply { clear(); set(2026, 10, 14, 9, 0, 0) }.timeInMillis, pickerDateToLocal(utcMidnight, 9))
        assertEquals(utcMidnight, localToPickerDate(pickerDateToLocal(utcMidnight, 23)))
    }

    @Test fun `tags parse trim and de-duplicate`() {
        assertEquals(listOf("a", "b", "c d"), parseTags(" #a, b ,, c d, a "))
        assertEquals(emptyList<String>(), parseTags(" , ,"))
        assertEquals("a, b", joinTags(listOf("a", "b")))
    }

    @Test fun `word count and reading time`() {
        assertEquals(0, wordCount("   \n "))
        assertEquals(3, wordCount("one  two\nthree"))
        assertEquals(0, readingMinutes(0))
        assertEquals(1, readingMinutes(5))
        assertEquals(1, readingMinutes(220))
        assertEquals(1, readingMinutes(240)) // up to ~20 words over a minute is tolerated
        assertEquals(2, readingMinutes(241))
    }
}
