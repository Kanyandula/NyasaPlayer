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
