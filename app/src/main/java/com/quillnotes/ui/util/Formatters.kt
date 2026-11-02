package com.quillnotes.ui.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val MINUTE = 60_000L
private const val HOUR = 60 * MINUTE

private fun format(pattern: String, timestamp: Long): String =
    SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timestamp))

fun startOfDay(timestamp: Long): Long = Calendar.getInstance().apply {
    timeInMillis = timestamp
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** Shifts a day-start timestamp by whole calendar days (DST-safe). */
fun shiftDays(dayStart: Long, days: Int): Long = Calendar.getInstance().apply {
    timeInMillis = dayStart
    add(Calendar.DAY_OF_YEAR, days)
}.timeInMillis

private fun sameYear(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR)
}

fun relativeTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    val diff = now - timestamp
    val today = startOfDay(now)
    return when {
        diff < MINUTE -> "Just now"
        diff < HOUR -> "${diff / MINUTE}m ago"
        timestamp >= today -> "${diff / HOUR}h ago"
        timestamp >= shiftDays(today, -1) -> "Yesterday"
        timestamp >= shiftDays(today, -6) -> format("EEEE", timestamp)
        sameYear(timestamp, now) -> format("MMM d", timestamp)
        else -> format("MMM d, yyyy", timestamp)
    }
}

fun dayLabel(dayStart: Long, now: Long = System.currentTimeMillis()): String {
    val today = startOfDay(now)
    return when (dayStart) {
        today -> "Today"
        shiftDays(today, -1) -> "Yesterday"
        else -> if (sameYear(dayStart, now)) format("EEEE, MMM d", dayStart) else format("EEE, MMM d, yyyy", dayStart)
    }
}

fun timeOfDay(timestamp: Long): String = format("h:mm a", timestamp)

fun headerDate(now: Long = System.currentTimeMillis()): String = format("EEEE, d MMMM", now)

fun shortWeekday(timestamp: Long): String = format("EEEEE", timestamp)

fun greeting(now: Long = System.currentTimeMillis()): String {
    val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Good night"
    }
}

fun dueLabel(dueDate: Long, now: Long = System.currentTimeMillis()): String {
    val today = startOfDay(now)
    val due = startOfDay(dueDate)
    return when {
        due == today -> "Today"
        due == shiftDays(today, 1) -> "Tomorrow"
        due == shiftDays(today, -1) -> "Yesterday"
        due > today && due < shiftDays(today, 7) -> format("EEEE", dueDate)
        sameYear(dueDate, now) -> format("MMM d", dueDate)
        else -> format("MMM d, yyyy", dueDate)
    }
}

fun parseTags(raw: String): List<String> =
    raw.split(',')
        .map { it.trim().removePrefix("#").trim() }
        .filter { it.isNotEmpty() }
        .distinct()

fun joinTags(tags: List<String>): String = tags.joinToString(", ")

fun wordCount(text: String): Int = text.split(Regex("\\s+")).count { it.isNotBlank() }

/**
 * Material's date picker works in UTC midnights. Converts the picked calendar date to a
 * local timestamp on that same date (at [hour], used as the reminder time).
 */
fun pickerDateToLocal(utcMillis: Long, hour: Int = 9): Long {
    val utc = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
    return Calendar.getInstance().apply {
        clear()
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), hour, 0, 0)
    }.timeInMillis
}

/** Inverse of [pickerDateToLocal]: the UTC midnight of a local timestamp's calendar date. */
fun localToPickerDate(localMillis: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = localMillis }
    return Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
    }.timeInMillis
}

fun readingMinutes(words: Int): Int = if (words == 0) 0 else maxOf(1, (words + 199) / 220)

/**
 * [relativeTime] phrased to sit inside a sentence: "just now", "5m ago", "yesterday",
 * "on Saturday", "on Oct 21".
 */
fun relativePhrase(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    val diff = now - timestamp
    val today = startOfDay(now)
    return when {
        diff < MINUTE -> "just now"
        diff < HOUR -> "${diff / MINUTE}m ago"
        timestamp >= today -> "${diff / HOUR}h ago"
        timestamp >= shiftDays(today, -1) -> "yesterday"
        timestamp >= shiftDays(today, -6) -> "on " + format("EEEE", timestamp)
        sameYear(timestamp, now) -> "on " + format("MMM d", timestamp)
        else -> "on " + format("MMM d, yyyy", timestamp)
    }
}
