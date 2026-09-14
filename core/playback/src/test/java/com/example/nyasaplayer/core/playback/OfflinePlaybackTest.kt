package com.example.nyasaplayer.core.playback

import com.example.nyasaplayer.core.common.models.Song
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one offline rule both surfaces will share (A8, D71). Plain JVM: it reads two strings.
 *
 * `file:/` with one slash is what `File.toURI()` produces, which is how mobile builds local URIs.
 */
class OfflinePlaybackTest {

    private val streamed = Song(mediaId = "s", audioUrl = "https://cdn.example/s.mp3")
    private val local = Song(mediaId = "l", audioUrl = "file:/data/user/10/pkg/files/l.audio")

    @Test
    fun online_streamedSong_isPlayable() {
        assertTrue(streamed.isPlayableNow(isOnline = true))
    }

    @Test
    fun offline_streamedSong_isNotPlayable() {
        assertFalse(streamed.isPlayableNow(isOnline = false))
    }

    @Test
    fun offline_localFile_isPlayable() {
        assertTrue(local.isPlayableNow(isOnline = false))
    }

    @Test
    fun offline_readsTheSameFieldThePlayerStreamsFrom() {
        // audioUrl blank, so resolvedAudioUrl falls back to songUrl — which is what SongMediaItemMapper
        // hands the player.
        val fallback = Song(mediaId = "f", audioUrl = "", songUrl = "file:/data/user/10/pkg/files/f.audio")
        assertTrue(fallback.isPlayableNow(isOnline = false))
    }

    @Test
    fun isStreamStalledOffline_offlineBufferingTryingToPlayStreamedSong_isStalled() {
        val snapshot = PlaybackSnapshot(currentSong = streamed, isBuffering = true, playWhenReady = true)

        assertTrue(snapshot.isStreamStalledOffline(isOnline = false))
    }

    @Test
    fun isStreamStalledOffline_online_isNotStalled() {
        val snapshot = PlaybackSnapshot(currentSong = streamed, isBuffering = true, playWhenReady = true)

        assertFalse(snapshot.isStreamStalledOffline(isOnline = true))
    }

    @Test
    fun isStreamStalledOffline_notBuffering_isNotStalled() {
        val snapshot = PlaybackSnapshot(currentSong = streamed, isBuffering = false, playWhenReady = true)

        assertFalse(snapshot.isStreamStalledOffline(isOnline = false))
    }

    @Test
    fun isStreamStalledOffline_notTryingToPlay_isNotStalled() {
        // A restore buffers with playWhenReady = false; that must not read as stalled.
        val snapshot = PlaybackSnapshot(currentSong = streamed, isBuffering = true, playWhenReady = false)

        assertFalse(snapshot.isStreamStalledOffline(isOnline = false))
    }

    @Test
    fun isStreamStalledOffline_localCurrentSong_isNotStalled() {
        val snapshot = PlaybackSnapshot(currentSong = local, isBuffering = true, playWhenReady = true)

        assertFalse(snapshot.isStreamStalledOffline(isOnline = false))
    }

    @Test
    fun isStreamStalledOffline_noCurrentSong_isStalled() {
        // Nothing local to protect, so there is nothing that excuses the stall.
        val snapshot = PlaybackSnapshot(currentSong = null, isBuffering = true, playWhenReady = true)

        assertTrue(snapshot.isStreamStalledOffline(isOnline = false))
    }
}
