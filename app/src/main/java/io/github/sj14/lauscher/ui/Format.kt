package io.github.sj14.lauscher.ui

import android.text.format.DateUtils
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

/** Formats milliseconds as "1:02:03" or "2:03". */
fun formatTime(milliseconds: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds.coerceAtLeast(0))
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

/** Formats a duration in a short, readable form such as "1 h 5 min" or "42 min". */
fun formatDuration(milliseconds: Long): String {
    val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "$hours h $minutes min"
        hours > 0 -> "$hours h"
        else -> "$minutes min"
    }
}

/** "Today", "3 days ago" for recent dates, otherwise the date in the user's locale. */
fun formatDate(timestamp: Long): String {
    if (timestamp <= 0) return ""
    val age = System.currentTimeMillis() - timestamp
    return if (age < TimeUnit.DAYS.toMillis(7)) {
        DateUtils.getRelativeTimeSpanString(timestamp, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS).toString()
    } else {
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp))
    }
}
