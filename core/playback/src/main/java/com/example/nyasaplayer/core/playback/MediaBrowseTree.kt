package com.example.nyasaplayer.core.playback

import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.example.nyasaplayer.core.common.models.Artist
import com.example.nyasaplayer.core.common.models.Genre
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.data.api.ArtistRepository
import com.example.nyasaplayer.core.data.api.AuthRepository
import com.example.nyasaplayer.core.data.api.GenreRepository
import com.example.nyasaplayer.core.data.api.SongRepository
import com.example.nyasaplayer.core.data.api.UserRepository
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaBrowseTree @Inject constructor(
    private val songRepository: SongRepository,
    private val artistRepository: ArtistRepository,
    private val genreRepository: GenreRepository,
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
) {
    companion object {
        const val ROOT_ID = "ROOT"
        const val RECENTLY_PLAYED_ID = "RECENTLY_PLAYED"
        const val GENRES_ID = "GENRES"
        const val ARTISTS_ID = "ARTISTS"
        const val ALL_SONGS_ID = "ALL_SONGS"

        internal const val GENRE_PREFIX = "GENRE_"
        internal const val ARTIST_PREFIX = "ARTIST_"
        private const val DEFAULT_BROWSE_LIMIT = 50
        private const val RECENTLY_PLAYED_LIMIT = 20
    }

    val rootItem: MediaItem = buildBrowsableItem(
        mediaId = ROOT_ID,
        title = "NyasaPlayer",
        subtitle = null,
    )

    fun getRootChildren(): List<MediaItem> = listOf(
        buildBrowsableItem(
            mediaId = RECENTLY_PLAYED_ID,
            title = "Recently Played",
            subtitle = null,
        ),
        buildBrowsableItem(
            mediaId = GENRES_ID,
            title = "Genres",
            subtitle = null,
        ),
        buildBrowsableItem(
            mediaId = ARTISTS_ID,
            title = "Artists",
            subtitle = null,
        ),
        buildBrowsableItem(
            mediaId = ALL_SONGS_ID,
            title = "All Songs",
            subtitle = null,
        ),
    )

    @Suppress("ReturnCount")
    suspend fun getChildren(parentId: String): List<MediaItem> = when {
        parentId == ROOT_ID -> getRootChildren()
        parentId == RECENTLY_PLAYED_ID -> getRecentlyPlayedItems()
        parentId == GENRES_ID -> getGenreItems()
        parentId == ARTISTS_ID -> getArtistItems()
        parentId == ALL_SONGS_ID -> getAllSongItems()
        parentId.startsWith(GENRE_PREFIX) -> getGenreSongItems(parentId.removePrefix(GENRE_PREFIX))
        parentId.startsWith(ARTIST_PREFIX) -> getArtistSongItems(parentId.removePrefix(ARTIST_PREFIX))
        else -> emptyList()
    }

    suspend fun getItem(mediaId: String): MediaItem? = when {
        mediaId == ROOT_ID -> rootItem
        mediaId in listOf(RECENTLY_PLAYED_ID, GENRES_ID, ARTISTS_ID, ALL_SONGS_ID) ->
            getRootChildren().find { it.mediaId == mediaId }
        mediaId.startsWith(GENRE_PREFIX) -> {
            val genres = genreRepository.getGenresByPopularity(DEFAULT_BROWSE_LIMIT)
            genres.find { it.id == mediaId.removePrefix(GENRE_PREFIX) }?.toBrowsableItem()
        }
        mediaId.startsWith(ARTIST_PREFIX) -> {
            artistRepository.getArtistById(mediaId.removePrefix(ARTIST_PREFIX))?.toBrowsableItem()
        }
        else -> {
            val songs = songRepository.getSongsByIds(listOf(mediaId))
            songs.firstOrNull()?.toPlayableItem()
        }
    }

    suspend fun search(query: String): List<MediaItem> =
        songRepository.searchSongs(query, DEFAULT_BROWSE_LIMIT).map { it.toPlayableItem() }

    // ── Private helpers ──

    private suspend fun getRecentlyPlayedItems(): List<MediaItem> {
        val uid = authRepository.currentUser?.uid ?: return emptyList()
        val entries = userRepository.getRecentlyPlayed(uid, RECENTLY_PLAYED_LIMIT)
            .firstOrNull() ?: return emptyList()
        val mediaIds = entries.map { it.mediaId }
        val songs = songRepository.getSongsByIds(mediaIds)
        val songMap = songs.associateBy { it.mediaId }
        return mediaIds.mapNotNull { songMap[it]?.toPlayableItem() }
    }

    private suspend fun getGenreItems(): List<MediaItem> {
        val genres = genreRepository.getGenresByPopularity(DEFAULT_BROWSE_LIMIT)
        return genres.map { it.toBrowsableItem() }
    }

    private suspend fun getArtistItems(): List<MediaItem> {
        val artists = artistRepository.getArtistsByPopularity(DEFAULT_BROWSE_LIMIT)
        return artists.map { it.toBrowsableItem() }
    }

    private suspend fun getAllSongItems(): List<MediaItem> {
        val songs = songRepository.getSongsByPopularity(DEFAULT_BROWSE_LIMIT)
        return songs.map { it.toPlayableItem() }
    }

    private suspend fun getGenreSongItems(genreId: String): List<MediaItem> {
        val songs = songRepository.getSongsByGenre(genreId).firstOrNull() ?: return emptyList()
        return songs.map { it.toPlayableItem() }
    }

    private suspend fun getArtistSongItems(artistId: String): List<MediaItem> {
        val songs = songRepository.getSongsByArtist(artistId).firstOrNull() ?: return emptyList()
        return songs.map { it.toPlayableItem() }
    }
}

// ── MediaItem builders ──

private fun buildBrowsableItem(
    mediaId: String,
    title: String,
    subtitle: String?,
    artworkUri: Uri? = null,
): MediaItem = MediaItem.Builder()
    .setMediaId(mediaId)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setArtworkUri(artworkUri)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            .build(),
    )
    .build()

private fun Song.toPlayableItem(): MediaItem = MediaItem.Builder()
    .setMediaId(mediaId)
    .setUri(resolvedAudioUrl.toUri())
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setSubtitle(resolvedArtistName)
            .setArtist(resolvedArtistName)
            .setArtworkUri(resolvedCoverUrl.toUri())
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .build(),
    )
    .build()

private fun Genre.toBrowsableItem(): MediaItem = buildBrowsableItem(
    mediaId = "${MediaBrowseTree.GENRE_PREFIX}$id",
    title = name,
    subtitle = "${songIds.size} songs",
)

private fun Artist.toBrowsableItem(): MediaItem = buildBrowsableItem(
    mediaId = "${MediaBrowseTree.ARTIST_PREFIX}$id",
    title = name,
    subtitle = null,
    artworkUri = imageUrl.takeIf { it.isNotBlank() }?.toUri(),
)
