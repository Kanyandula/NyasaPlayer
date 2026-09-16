package com.example.nyasaplayer.auto.ui.screens

import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.data.local.entity.DownloadStatus

/**
 * One Downloads row: the song, and how far its download has got.
 *
 * [sizeBytes] is what the file actually weighs once complete and 0 before that, which is why the
 * storage line adds up completed rows only.
 */
data class CarDownloadItem(
    val song: Song,
    val status: DownloadStatus,
    val progress: Int = 0,
    val sizeBytes: Long = 0L,
) {
    /** Only a finished download has a file to play; the rest are rows about work, not music. */
    val isPlayable: Boolean get() = status == DownloadStatus.Completed
}

/**
 * Rows to render. Parked shows everything; driving truncates to [maxItems] (FR-2.4).
 *
 * Unwindowed, unlike the queue: there is no "current" row here that has to stay visible, so the
 * first page is always the right page.
 */
internal fun downloadDisplayItems(
    items: List<CarDownloadItem>,
    maxItems: Int,
    isDriving: Boolean,
): List<CarDownloadItem> = if (isDriving) items.take(maxItems.coerceAtLeast(0)) else items

/**
 * Whether download mutations — remove, remove all, retry — may run.
 *
 * Parked only. Removing destroys the one copy that plays without a connection, and retrying starts
 * a network fetch; neither is work to hand a driver in motion (PRD screen 15, aaos-DESIGN
 * "Downloads edits"). The location gate cannot express this: Downloads stays *viewable* while
 * driving, so this is an action inside a permitted location — see GateResult's note.
 */
internal fun canMutateDownloads(isDriving: Boolean): Boolean = !isDriving

/** Remove all is parked-only work, and pointless with no finished download to remove. */
internal fun canRemoveAllDownloads(
    items: List<CarDownloadItem>,
    isDriving: Boolean,
): Boolean = canMutateDownloads(isDriving) && items.any { it.isPlayable }

/**
 * A file size a driver can read at a glance: "512 KB", "148 MB", "1.4 GB".
 *
 * Decimal units, matching what a head unit's own storage settings report. Integer arithmetic
 * throughout, so no locale can turn the decimal point into a comma halfway down the screen.
 */
internal fun formatDownloadSize(bytes: Long): String = when {
    bytes <= 0L -> "0 MB"
    bytes < BytesPerMegabyte -> "${ceilDiv(bytes, BytesPerKilobyte)} KB"
    bytes < BytesPerGigabyte -> "${ceilDiv(bytes, BytesPerMegabyte)} MB"
    else -> {
        val tenths = bytes / (BytesPerGigabyte / TenthsPerUnit)
        "${tenths / TenthsPerUnit}.${tenths % TenthsPerUnit} GB"
    }
}

private const val BytesPerKilobyte = 1_000L
private const val BytesPerMegabyte = 1_000_000L
private const val BytesPerGigabyte = 1_000_000_000L
private const val TenthsPerUnit = 10L

/** Storage line under the title: "12 songs · 148 MB". */
internal fun formatDownloadSummary(items: List<CarDownloadItem>): String {
    val completed = items.filter { it.isPlayable }
    if (completed.isEmpty()) return "Nothing downloaded yet"
    val noun = if (completed.size == 1) "song" else "songs"
    return "${completed.size} $noun · ${formatDownloadSize(completed.sumOf { it.sizeBytes })}"
}

/** What the album screen's Download control says, and whether it acts. */
data class CarDetailDownload(
    val label: String,
    val enabled: Boolean,
)

/**
 * The Download control for [tracks] — screen 11's third primary action, unblocked once the
 * downloader moved into a shared module (D12).
 *
 * Aggregate, not per-track: the control acts on the whole album, so it reports the whole album.
 * Order matters — an album already downloaded reads "Downloaded" whether or not the car is moving,
 * because that is true either way and is more use to the driver than being told it is locked.
 */
internal fun albumDownloadControl(
    tracks: List<Song>,
    downloads: List<CarDownloadItem>,
    isDriving: Boolean,
): CarDetailDownload {
    if (tracks.isEmpty()) return CarDetailDownload("Download", enabled = false)
    val byId = downloads.associateBy { it.song.mediaId }
    val statuses = tracks.map { byId[it.mediaId]?.status }
    return when {
        statuses.all { it == DownloadStatus.Completed } ->
            CarDetailDownload("Downloaded", enabled = false)

        statuses.any { it == DownloadStatus.Downloading || it == DownloadStatus.Pending } ->
            CarDetailDownload("Downloading", enabled = false)

        // Visible and disabled, never hidden, and the label is the explanation (FR-2.6).
        isDriving -> CarDetailDownload("Parked only", enabled = false)

        else -> CarDetailDownload("Download", enabled = true)
    }
}

/** Rounds up, so a 1-byte file reads as "1 KB" rather than "0 KB". */
private fun ceilDiv(value: Long, divisor: Long): Long = (value + divisor - 1L) / divisor
