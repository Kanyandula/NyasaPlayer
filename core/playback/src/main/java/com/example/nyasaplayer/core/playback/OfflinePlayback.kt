package com.example.nyasaplayer.core.playback

import com.example.nyasaplayer.core.common.models.Song

/**
 * Can start right now: the network is up, or the file is already on this device.
 *
 * Reads [Song.resolvedAudioUrl], the field `SongMediaItemMapper` streams from. Knows nothing about
 * downloads — a song is local because a caller rewrote its URI to a `file:` one, which mobile does
 * today and A9 will do on the car.
 */
fun Song.isPlayableNow(isOnline: Boolean): Boolean =
    isOnline || resolvedAudioUrl.startsWith("file:")

/**
 * Trying to play, buffering, offline, and not a local file: a stream that cannot load. Decides the car's
 * stall guard (A8); a pure function so it is testable without a ViewModel.
 */
fun PlaybackSnapshot.isStreamStalledOffline(isOnline: Boolean): Boolean =
    !isOnline && isBuffering && playWhenReady && currentSong?.isPlayableNow(isOnline = false) != true
