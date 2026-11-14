package com.quillnotes.ui.util

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.quillnotes.R

/** [TimeText] backed by string resources, so "5m ago" and "yesterday" follow the app language. */
class ResourceTimeText(private val res: Resources) : TimeText {
    override fun justNow(inSentence: Boolean) =
        res.getString(if (inSentence) R.string.time_just_now_phrase else R.string.time_just_now)
    override fun minutesAgo(minutes: Long) = res.getString(R.string.time_minutes_ago, minutes)
    override fun hoursAgo(hours: Long) = res.getString(R.string.time_hours_ago, hours)
    override fun today() = res.getString(R.string.time_today)
    override fun tomorrow() = res.getString(R.string.time_tomorrow)
    override fun yesterday(inSentence: Boolean) =
        res.getString(if (inSentence) R.string.time_yesterday_phrase else R.string.time_yesterday)
    override fun onDate(date: String) = res.getString(R.string.time_on_date, date)
}

/** The words date helpers should use in the current composition. English until the app root provides [rememberTimeText]. */
val LocalTimeText = staticCompositionLocalOf<TimeText> { EnglishTimeText }

/** Translated date words for the current language; re-created when the configuration changes. */
@Composable
fun rememberTimeText(): TimeText {
    val res = LocalContext.current.resources
    val configuration = LocalConfiguration.current
    return remember(res, configuration) { ResourceTimeText(res) }
}
