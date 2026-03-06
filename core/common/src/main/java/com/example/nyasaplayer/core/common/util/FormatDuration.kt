package com.example.nyasaplayer.core.common.util

private const val MillisPerSecond = 1000L
private const val SecondsPerMinute = 60L

fun formatDuration(ms: Long): String {
    val totalSeconds = ms / MillisPerSecond
    val minutes = totalSeconds / SecondsPerMinute
    val seconds = totalSeconds % SecondsPerMinute
    return "%d:%02d".format(minutes, seconds)
}
