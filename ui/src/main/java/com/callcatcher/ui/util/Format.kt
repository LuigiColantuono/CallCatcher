package com.callcatcher.ui.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
private val dateTimeFormat = SimpleDateFormat("d MMM yyyy · HH:mm", Locale.getDefault())
private val weekdayFormat = SimpleDateFormat("EEEE d MMMM", Locale.getDefault())
private val fullDateFormat = SimpleDateFormat("d MMMM yyyy", Locale.getDefault())

fun formatDateTime(millis: Long): String = dateTimeFormat.format(Date(millis))

fun formatTimeOfDay(millis: Long): String = timeFormat.format(Date(millis))

/** "m:ss" or "h:mm:ss", for list rows and timeline labels. */
fun formatDuration(millis: Long): String {
    val total = TimeUnit.MILLISECONDS.toSeconds(millis.coerceAtLeast(0))
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** "1 h 24 min" / "12 min" / "45 s", for summaries. */
fun formatTotalDuration(millis: Long): String {
    val total = TimeUnit.MILLISECONDS.toSeconds(millis.coerceAtLeast(0))
    val h = total / 3600
    val m = (total % 3600) / 60
    return when {
        h > 0 -> "$h h ${m} min"
        m > 0 -> "$m min"
        else -> "$total s"
    }
}

/** Parts for the big readout: value/unit pairs, e.g. [("03","m"), ("12","s")]. */
fun clockParts(millis: Long): List<Pair<String, String>> {
    val total = TimeUnit.MILLISECONDS.toSeconds(millis.coerceAtLeast(0))
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) {
        listOf(h.toString() to "h", "%02d".format(m) to "m", "%02d".format(s) to "s")
    } else {
        listOf("%02d".format(m) to "m", "%02d".format(s) to "s")
    }
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.2f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%d KB".format(bytes / 1_000)
    else -> "$bytes B"
}

/** Section header for the list: "Today", "Yesterday", a weekday within a week, else a date. */
fun formatDayHeader(millis: Long, now: Long = System.currentTimeMillis()): String {
    val day = startOfDay(millis)
    val today = startOfDay(now)
    val days = TimeUnit.MILLISECONDS.toDays(today - day)
    val label = when {
        days == 0L -> "Today"
        days == 1L -> "Yesterday"
        days in 2..6 -> weekdayFormat.format(Date(millis))
        else -> fullDateFormat.format(Date(millis))
    }
    return label.replaceFirstChar { it.titlecase(Locale.getDefault()) }
}

fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis
