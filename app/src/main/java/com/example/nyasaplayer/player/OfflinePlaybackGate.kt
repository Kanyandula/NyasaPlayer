package com.example.nyasaplayer.player

import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.playback.isPlayableNow

/**
 * What mobile is allowed to start right now, decided by `:core:playback`'s shared rule (T28).
 *
 * Both surfaces used to answer this separately — the car through `Song.isPlayableNow`, mobile
 * through three hand-written copies of `!isOnline && !isDownloaded`. The copies are the defect:
 * one of them read "is anything downloaded" as "did resolution change a URL", which is false for a
 * song whose catalogue URL is already the local file.
 *
 * It sits outside `PlayerViewModel` only so it can be tested: the ViewModel needs a `MediaController`
 * and a `NetworkMonitor` before it can be constructed at all.
 *
 * Callers pass **resolved** songs — `DownloadRepository.resolveLocalUri` applied — because the rule
 * reads the URL rather than asking the download repository.
 */
internal object OfflinePlaybackGate {

    /** A single song the caller asked to play. */
    fun refuses(resolved: Song, isOnline: Boolean): Boolean = !resolved.isPlayableNow(isOnline)

    /** A queue: one playable song is enough to start, and an empty queue has none. */
    fun refuses(resolved: List<Song>, isOnline: Boolean): Boolean =
        resolved.none { it.isPlayableNow(isOnline) }
}
