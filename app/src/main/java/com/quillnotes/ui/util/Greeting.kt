package com.quillnotes.ui.util

import androidx.annotation.StringRes
import com.quillnotes.R
import java.util.Calendar

/** The greeting for the hour of day, as a string resource so it can be translated. */
@StringRes
fun greetingRes(now: Long = System.currentTimeMillis()): Int {
    val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> R.string.greeting_morning
        in 12..16 -> R.string.greeting_afternoon
        in 17..21 -> R.string.greeting_evening
        else -> R.string.greeting_night
    }
}
