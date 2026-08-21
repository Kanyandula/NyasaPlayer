@file:androidx.media3.common.util.UnstableApi

package com.example.nyasaplayer.core.playback

import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaConstants
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
        const val LIKED_SONGS_ID = "LIKED_SONGS"
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
        extras = GRID_BROWSABLE_HINT,
    )

    private val rootChildren: List<MediaItem> = listOf(
        buildBrowsableItem(
            mediaId = RECENTLY_PLAYED_ID,
            title = "Recently Played",
            subtitle = null,
            extras = LIST_PLAYABLE_HINT,
        ),
        buildBrowsableItem(
            mediaId = LIKED_SONGS_ID,
            title = "Liked Songs",
            subtitle = null,
            extras = LIST_PLAYABLE_HINT,
        ),
        buildBrowsableItem(
            mediaId = GENRES_ID,
            title = "Genres",
            subtitle = null,
            extras = GRID_BROWSABLE_HINT,
        ),
        buildBrowsableItem(
            mediaId = ARTISTS_ID,
            title = "Artists",
            subtitle = null,
            extras = GRID_BROWSABLE_HINT,
        ),
        buildBrowsableItem(
            mediaId = ALL_SONGS_ID,
            title = "All Songs",
            subtitle = null,
            extras = LIST_PLAYABLE_HINT,
        ),
    )

    fun getRootChildren(): List<MediaItem> = rootChildren

    suspend fun getChildren(parentId: String): List<MediaItem> = when (parentId) {
        ROOT_ID -> getRootChildren()
        RECENTLY_PLAYED_ID -> getRecentlyPlayedItems()
        LIKED_SONGS_ID -> getLikedSongItems()
        GENRES_ID -> getGenreItems()
        ARTISTS_ID -> getArtistItems()
        ALL_SONGS_ID -> getAllSongItems()
        else -> when {
            parentId.startsWith(GENRE_PREFIX) -> getGenreSongItems(parentId.removePrefix(GENRE_PREFIX))
            parentId.startsWith(ARTIST_PREFIX) -> getArtistSongItems(parentId.removePrefix(ARTIST_PREFIX))
            else -> emptyList()
        }
    }

    suspend fun getItem(mediaId: String): MediaItem? = when (mediaId) {
        ROOT_ID -> rootItem
        RECENTLY_PLAYED_ID, LIKED_SONGS_ID, GENRES_ID, ARTISTS_ID, ALL_SONGS_ID ->
            getRootChildren().find { it.mediaId == mediaId }
        else -> when {
            mediaId.startsWith(GENRE_PREFIX) -> {
                genreRepository.getGenreById(mediaId.removePrefix(GENRE_PREFIX))?.toBrowsableItem()
            }
            mediaId.startsWith(ARTIST_PREFIX) -> {
                artistRepository.getArtistById(mediaId.removePrefix(ARTIST_PREFIX))?.toBrowsableItem()
            }
            else -> {
                val songs = songRepository.getSongsByIds(listOf(mediaId))
                songs.firstOrNull()?.toPlayableItem()
            }
        }
    }

    /**
     * Songs only, and playable.
     *
     * The car launcher's own search screen also shows album, artist and playlist cards, but those
     * are enrichment it can afford because it owns the tap and has detail screens to land on. A
     * voice or host-rendered search result is a thing to play, so returning a browsable card here
     * would hand Assistant something it cannot act on (spec 3.5).
     *
     * The song half must stay identical to the launcher's song section — same primitive, same
     * limit, same order. `SearchParityTest` in `:automotive` fails if they drift.
     */
    suspend fun search(query: String): List<MediaItem> =
        songRepository.searchSongs(query, DEFAULT_BROWSE_LIMIT).map { it.toPlayableItem() }

    // ── Private helpers ──

    // getSongsByIds returns songs in request order, so the source ordering is preserved.
    private suspend fun getRecentlyPlayedItems(): List<MediaItem> {
        val uid = authRepository.currentUser?.uid ?: return emptyList()
        val entries = userRepository.getRecentlyPlayed(uid, RECENTLY_PLAYED_LIMIT)
            .firstOrNull() ?: return emptyList()
        return songRepository.getSongsByIds(entries.map { it.mediaId }).map { it.toPlayableItem() }
    }

    private suspend fun getLikedSongItems(): List<MediaItem> {
        val uid = authRepository.currentUser?.uid ?: return emptyList()
        val liked = userRepository.getLikedSongs(uid).firstOrNull() ?: return emptyList()
        return songRepository.getSongsByIds(liked.map { it.mediaId }).map { it.toPlayableItem() }
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

// Content-style hints the AAOS media template reads to pick grid vs list rendering.
// Shared instances: MediaMetadata keeps the reference, and the session layer copies
// the bundle before touching it.
private val GRID_BROWSABLE_HINT = bundleOf(
    MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE to MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM,
)

private val LIST_PLAYABLE_HINT = bundleOf(
    MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE to MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
)

// ── MediaItem builders ──

private fun buildBrowsableItem(
    mediaId: String,
    title: String,
    subtitle: String?,
    artworkUri: Uri? = null,
    extras: Bundle? = null,
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
            .setExtras(extras)
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
            .setAlbumTitle(albumName.takeIf { it.isNotBlank() })
            .setArtworkUri(resolvedCoverUrl.toUri())
            .setDurationMs(durationMs.takeIf { it > 0L })
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
    extras = LIST_PLAYABLE_HINT,
)

private fun Artist.toBrowsableItem(): MediaItem = buildBrowsableItem(
    mediaId = "${MediaBrowseTree.ARTIST_PREFIX}$id",
    title = name,
    subtitle = null,
    artworkUri = imageUrl.takeIf { it.isNotBlank() }?.toUri(),
    extras = LIST_PLAYABLE_HINT,
)
