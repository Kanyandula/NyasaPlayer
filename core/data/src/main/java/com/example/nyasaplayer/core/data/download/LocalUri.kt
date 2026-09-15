package com.example.nyasaplayer.core.data.download

import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.data.api.DownloadRepository
import java.io.File

/**
 * Where a completed download actually sits, or null when it does not.
 *
 * The recorded path is checked against the filesystem: Room keeps saying a song is downloaded
 * after the user clears app storage, and handing the player a path to a deleted file fails at
 * playback time instead of falling back to the stream.
 */
fun DownloadRepository.localUriFor(mediaId: String): String? {
    val path = getLocalFilePath(mediaId) ?: return null
    val file = File(path)
    return if (file.exists()) file.toURI().toString() else null
}

/**
 * [song] pointed at its downloaded file, or returned unchanged when it has none.
 *
 * Both URL fields are rewritten because `resolvedAudioUrl` falls back to `songUrl`, and the shared
 * offline rule (`core:playback` `isPlayableNow`) decides on the resolved value starting with
 * `file:`. Rewriting only one field leaves a downloaded song looking unplayable offline.
 *
 * An extension on the repository interface rather than a method on `SongDownloadManager`: the car
 * and the restore path both need resolution and neither needs the downloader, and a `Context`-bound
 * singleton in that seam would put `:core:playback`'s restore tests on Robolectric to construct it.
 */
fun DownloadRepository.resolveLocalUri(song: Song): Song {
    val localUri = localUriFor(song.mediaId) ?: return song
    return song.copy(audioUrl = localUri, songUrl = localUri)
}
