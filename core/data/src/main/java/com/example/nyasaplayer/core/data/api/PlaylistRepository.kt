package com.example.nyasaplayer.core.data.api

import com.example.nyasaplayer.core.common.models.Playlist
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {
    fun getPlaylists(userId: String): Flow<List<Playlist>>
    suspend fun createPlaylist(userId: String, name: String): String
    suspend fun addSongToPlaylist(userId: String, playlistId: String, mediaId: String)
    suspend fun removeSongFromPlaylist(userId: String, playlistId: String, mediaId: String)
    suspend fun deletePlaylist(userId: String, playlistId: String)

    /** [userId]'s playlists matching [query] on name, best match first. */
    suspend fun searchPlaylists(userId: String, query: String, limit: Int): List<Playlist>
}
