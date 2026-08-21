package com.example.nyasaplayer.core.common.util

private const val MillisPerSecond = 1000L
private const val SecondsPerMinute = 60L
private const val MinutesPerHour = 60L
private const val SecondsDigits = 2

fun formatDuration(ms: Long): String {
    val totalSeconds = ms / MillisPerSecond
    val minutes = totalSeconds / SecondsPerMinute
    val seconds = totalSeconds % SecondsPerMinute
    // Concatenation rather than String.format: this runs once per visible row on every list
    // recomposition, and format() builds a Formatter and does a locale lookup each call.
    return "$minutes:${seconds.toString().padStart(SecondsDigits, '0')}"
}

/** "1 hr 32 min" or "48 min" — used for aggregate remaining durations, not per-track. */
fun formatDurationLong(ms: Long): String {
    val totalMinutes = ms / MillisPerSecond / SecondsPerMinute
    val hours = totalMinutes / MinutesPerHour
    val minutes = totalMinutes % MinutesPerHour
    return if (hours > 0) "$hours hr $minutes min" else "$minutes min"
}
