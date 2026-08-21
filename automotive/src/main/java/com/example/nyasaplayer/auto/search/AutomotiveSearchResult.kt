package com.example.nyasaplayer.auto.search

import com.example.nyasaplayer.core.common.models.Album
import com.example.nyasaplayer.core.common.models.Artist
import com.example.nyasaplayer.core.common.models.Playlist
import com.example.nyasaplayer.core.common.models.Song

/**
 * Where one result sits against results of every other type.
 *
 * [matchQuality] leads and lower is better — an exact album name beats a song that merely has the
 * query in its album field, which is why a single type-ordered list would be wrong. [typePriority]
 * only breaks ties between equally good matches.
 */
data class SearchRank(
    val matchQuality: Int,
    val typePriority: Int,
    val popularity: Int,
    val sortTitle: String,
)

/** Best first: match quality, then type, then popularity, then title, then id. */
internal val ResultOrder: Comparator<AutomotiveSearchResult> = compareBy(
    { it.rank.matchQuality },
    { it.rank.typePriority },
    { -it.rank.popularity },
    { it.rank.sortTitle },
    { it.stableId },
)

/**
 * One search hit, with its type intact.
 *
 * The type survives all the way to the tap handler because each one goes somewhere different: a
 * song plays, the other three open a detail screen (spec 3.6).
 */
sealed interface AutomotiveSearchResult {
    /** Type-prefixed so two entities that share an id cannot collide in a list key. */
    val stableId: String
    val title: String
    val subtitle: String
    val artworkUrl: String
    val rank: SearchRank

    data class SongResult(val song: Song, override val rank: SearchRank) : AutomotiveSearchResult {
        override val stableId: String get() = "song:${song.mediaId}"
        override val title: String get() = song.title
        override val subtitle: String get() = song.resolvedArtistName
        override val artworkUrl: String get() = song.resolvedCoverUrl
    }

    data class AlbumResult(val album: Album, override val rank: SearchRank) : AutomotiveSearchResult {
        override val stableId: String get() = "album:${album.id}"
        override val title: String get() = album.name
        override val subtitle: String get() = album.artistName
        override val artworkUrl: String get() = album.imageUrl
    }

    data class ArtistResult(
        val artist: Artist,
        override val rank: SearchRank,
    ) : AutomotiveSearchResult {
        override val stableId: String get() = "artist:${artist.id}"
        override val title: String get() = artist.name
        override val subtitle: String get() = "${artist.songCount} songs"
        override val artworkUrl: String get() = artist.imageUrl
    }

    data class PlaylistResult(
        val playlist: Playlist,
        override val rank: SearchRank,
    ) : AutomotiveSearchResult {
        override val stableId: String get() = "playlist:${playlist.id}"
        override val title: String get() = playlist.name
        override val subtitle: String get() = "${playlist.songIds.size} songs"

        /** Playlists carry no artwork of their own; detail uses its first track's (spec 5). */
        override val artworkUrl: String get() = ""
    }
}

/**
 * One committed query's results.
 *
 * [featured] is the best result across all four types and is left out of its own section, so the
 * driver never sees the same card twice.
 */
data class AutomotiveSearchResults(
    val query: String = "",
    val featured: AutomotiveSearchResult? = null,
    val songs: List<AutomotiveSearchResult.SongResult> = emptyList(),
    val albums: List<AutomotiveSearchResult.AlbumResult> = emptyList(),
    val artists: List<AutomotiveSearchResult.ArtistResult> = emptyList(),
    val playlists: List<AutomotiveSearchResult.PlaylistResult> = emptyList(),
) {
    /**
     * Song results in rendered order, featured card first when it is a song.
     *
     * Held rather than computed on read so the identity is stable: `rememberVisible` keys on the
     * list, and a fresh one per recomposition would re-cap twice a second while music plays.
     */
    val songQueue: List<Song> =
        (listOfNotNull(featured as? AutomotiveSearchResult.SongResult) + songs).map { it.song }

    val isEmpty: Boolean
        get() = featured == null &&
            songs.isEmpty() &&
            albums.isEmpty() &&
            artists.isEmpty() &&
            playlists.isEmpty()
}
