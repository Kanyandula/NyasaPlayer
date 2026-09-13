package com.example.nyasaplayer.core.playback

import com.example.nyasaplayer.core.common.models.Song
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one offline rule both surfaces will share (A8, D71). Plain JVM: it reads two strings.
 *
 * `file:/` with one slash is deliberate — mobile builds local URIs with `File.toURI()`, which is the
 * form that produces.
 */
class OfflinePlaybackTest {

    private val streamed = Song(mediaId = "s", audioUrl = "https://cdn.example/s.mp3")
    private val local = Song(mediaId = "l", audioUrl = "file:/data/user/10/pkg/files/l.audio")

    @Test
    fun online_anythingPlays() {
        assertTrue(streamed.isPlayableNow(isOnline = true))
    }

    @Test
    fun offline_aStreamedSongDoesNot() {
        assertFalse(streamed.isPlayableNow(isOnline = false))
    }

    @Test
    fun offline_aLocalFilePlays() {
        assertTrue(local.isPlayableNow(isOnline = false))
    }

    @Test
    fun offline_readsTheSameFieldThePlayerStreamsFrom() {
        // audioUrl blank, so resolvedAudioUrl falls back to songUrl — which is what SongMediaItemMapper
        // hands the player.
        val fallback = Song(mediaId = "f", audioUrl = "", songUrl = "file:/data/user/10/pkg/files/f.audio")
        assertTrue(fallback.isPlayableNow(isOnline = false))
    }
}
