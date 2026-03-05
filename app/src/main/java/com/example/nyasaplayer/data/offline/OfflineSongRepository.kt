package com.example.nyasaplayer.data.offline

import com.example.nyasaplayer.data.api.SongRepository
import com.example.nyasaplayer.data.local.dao.SongDao
import com.example.nyasaplayer.models.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class OfflineSongRepository @Inject constructor(
    private val songDao: SongDao,
) : SongRepository {

    private companion object {
        const val SQLITE_BIND_VARIABLE_LIMIT = 999
    }

    override fun getSongs(): Flow<List<Song>> =
        songDao.getAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getSongsByIds(ids: List<String>): List<Song> {
        if (ids.isEmpty()) return emptyList()
        val songs = ids.chunked(SQLITE_BIND_VARIABLE_LIMIT).flatMap { chunk ->
            songDao.getByMediaIds(chunk).map { it.toDomain() }
        }
        val songMap = songs.associateBy { it.mediaId }
        return ids.mapNotNull { songMap[it] }
    }

    override fun getSongsByArtist(artistId: String): Flow<List<Song>> =
        songDao.getByArtistId(artistId).map { entities -> entities.map { it.toDomain() } }

    override fun getSongsByGenre(genreId: String): Flow<List<Song>> =
        songDao.getByGenreId(genreId).map { entities -> entities.map { it.toDomain() } }
}
