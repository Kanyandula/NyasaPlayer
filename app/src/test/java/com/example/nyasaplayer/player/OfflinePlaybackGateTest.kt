package com.example.nyasaplayer.player

import com.example.nyasaplayer.core.common.models.Song
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T28's decision, on the JVM.
 *
 * These prove the rule refuses and allows the right things. They do not prove `PlayerViewModel`
 * asks the gate, nor that the UI shows the error — its six collaborators are not constructible in
 * a plain unit test, so that half is the phone pass
 * (`docs/superpowers/specs/2026-09-19-t28-mobile-offline-rule-design.md`).
 */
class OfflinePlaybackGateTest {

    @Test
    fun `offline, a streamed song is refused`() {
        assertTrue(OfflinePlaybackGate.refuses(streamed, isOnline = false))
    }

    @Test
    fun `offline, a downloaded song is allowed`() {
        assertFalse(OfflinePlaybackGate.refuses(downloaded, isOnline = false))
    }

    @Test
    fun `online, a streamed song is allowed`() {
        assertFalse(OfflinePlaybackGate.refuses(streamed, isOnline = true))
    }

    /** `resolvedAudioUrl` falls back to `songUrl`, which the old zip comparison never read. */
    @Test
    fun `offline, a song local only through songUrl is allowed`() {
        val song = Song(mediaId = "3", audioUrl = "", songUrl = LocalUri)
        assertFalse(OfflinePlaybackGate.refuses(song, isOnline = false))
    }

    /** Resolution changes nothing here, so the old "did the URL change" proxy read it as remote. */
    @Test
    fun `offline, a song whose catalogue url is already local is allowed`() {
        val song = Song(mediaId = "4", audioUrl = LocalUri, songUrl = LocalUri)
        assertFalse(OfflinePlaybackGate.refuses(song, isOnline = false))
    }

    @Test
    fun `offline, a queue with one downloaded song is allowed`() {
        val queue = listOf(streamed, streamed.copy(mediaId = "5"), downloaded)
        assertFalse(OfflinePlaybackGate.refuses(queue, isOnline = false))
    }

    @Test
    fun `offline, a queue of only streamed songs is refused`() {
        assertTrue(OfflinePlaybackGate.refuses(listOf(streamed, streamed.copy(mediaId = "6")), isOnline = false))
    }

    @Test
    fun `offline, an empty queue is refused`() {
        assertTrue(OfflinePlaybackGate.refuses(emptyList(), isOnline = false))
    }

    @Test
    fun `online, a queue of only streamed songs is allowed`() {
        assertFalse(OfflinePlaybackGate.refuses(listOf(streamed), isOnline = true))
    }

    private companion object {
        const val LocalUri = "file:/data/user/0/com.example.nyasaplayer/files/downloads/1.audio"

        val streamed = Song(mediaId = "1", audioUrl = "https://example.com/1.mp3")
        val downloaded = Song(mediaId = "2", audioUrl = LocalUri, songUrl = LocalUri)
    }
}
