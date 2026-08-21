package com.example.nyasaplayer.auto.fake

import com.example.nyasaplayer.core.common.models.Playlist
import com.example.nyasaplayer.core.data.api.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class FakePlaylistRepository : PlaylistRepository {

    private val emissions = MutableSharedFlow<List<Playlist>>(replay = 1)

    /** Nothing is emitted until a test calls this. Models Firestore's first-emission latency. */
    suspend fun emit(playlists: List<Playlist>) {
        emissions.emit(playlists)
    }

    override fun getPlaylists(userId: String): Flow<List<Playlist>> = emissions

    override suspend fun createPlaylist(userId: String, name: String): String =
        error("A3 wires read-only playlist access (D15)")

    override suspend fun addSongToPlaylist(userId: String, playlistId: String, mediaId: String) =
        error("A3 wires read-only playlist access (D15)")

    override suspend fun removeSongFromPlaylist(userId: String, playlistId: String, mediaId: String) =
        error("A3 wires read-only playlist access (D15)")

    override suspend fun deletePlaylist(userId: String, playlistId: String) =
        error("A3 wires read-only playlist access (D15)")

    /** Searches the last emission, which is what a one-shot repository read would have seen. */
    override suspend fun searchPlaylists(userId: String, query: String, limit: Int): List<Playlist> {
        val needle = query.lowercase()
        return emissions.replayCache.lastOrNull()
            .orEmpty()
            .filter { it.name.lowercase().contains(needle) }
            .take(limit)
    }
}
