package com.example.nyasaplayer.core.data.offline

import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.data.api.SongRepository
import com.example.nyasaplayer.core.data.local.dao.SongDao
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

    override suspend fun getSongsByPopularity(limit: Int): List<Song> =
        songDao.getByPopularity(limit).map { it.toDomain() }

    override suspend fun searchSongs(query: String, limit: Int): List<Song> =
        songDao.search(escapeLikeArgument(query), limit).map { it.toDomain() }
}
