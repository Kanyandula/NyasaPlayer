package com.example.nyasaplayer.core.data.fake

import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.data.api.SongRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeSongRepository : SongRepository {

    val songs = MutableStateFlow<List<Song>>(emptyList())

    override fun getSongs(): Flow<List<Song>> = songs

    override suspend fun getSongsByIds(ids: List<String>): List<Song> {
        val idSet = ids.toSet()
        return songs.value.filter { it.mediaId in idSet }
    }

    override fun getSongsByArtist(artistId: String): Flow<List<Song>> =
        songs.map { list -> list.filter { it.artistId == artistId } }

    override fun getSongsByGenre(genreId: String): Flow<List<Song>> =
        songs.map { list -> list.filter { genreId in it.genreIds } }

    override suspend fun getSongsByPopularity(limit: Int): List<Song> =
        songs.value.sortedByDescending { it.popularity }.take(limit)

    override suspend fun searchSongs(query: String, limit: Int): List<Song> {
        val lowerQuery = query.lowercase()
        return songs.value.filter { song ->
            song.title.lowercase().contains(lowerQuery) ||
                song.artistName.lowercase().contains(lowerQuery)
        }.sortedByDescending { it.popularity }.take(limit)
    }
}
