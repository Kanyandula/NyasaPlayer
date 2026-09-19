@file:androidx.media3.common.util.UnstableApi

package com.example.nyasaplayer.core.playback

import androidx.media3.common.MediaItem
import com.example.nyasaplayer.core.data.api.DownloadRepository
import com.example.nyasaplayer.core.data.api.SongRepository
import com.example.nyasaplayer.core.data.download.resolveLocalUri

/**
 * The playable items for [ids], pointed at their downloaded files where there are any (T31).
 *
 * `PlaybackService.onAddMediaItems` is the entry point external controllers play through, and it
 * was the one that skipped the resolution `PlaybackStatePersistence.restore()` does. Request order
 * is kept and unknown ids dropped, by `SongRepository.getSongsByIds`.
 */
suspend fun SongRepository.playableItems(
    ids: List<String>,
    downloads: DownloadRepository,
): List<MediaItem> = getSongsByIds(ids).map(downloads::resolveLocalUri).map { it.toMediaItem() }
