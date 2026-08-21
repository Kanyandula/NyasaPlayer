package com.example.nyasaplayer.auto.search

import com.example.nyasaplayer.auto.search.AutomotiveSearchResult.AlbumResult
import com.example.nyasaplayer.auto.search.AutomotiveSearchResult.ArtistResult
import com.example.nyasaplayer.auto.search.AutomotiveSearchResult.PlaylistResult
import com.example.nyasaplayer.auto.search.AutomotiveSearchResult.SongResult
import com.example.nyasaplayer.core.data.api.AlbumRepository
import com.example.nyasaplayer.core.data.api.ArtistRepository
import com.example.nyasaplayer.core.data.api.AuthRepository
import com.example.nyasaplayer.core.data.api.PlaylistRepository
import com.example.nyasaplayer.core.data.api.SongRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Matches `MediaBrowseTree`'s browse limit. The two song paths must not fork on limit, trimming
 * or ordering (spec 3.5), and this is the launcher half of that.
 */
private const val SongLimit = 50

/**
 * Smaller than the song limit and not the thing that protects the driver: the cumulative driving
 * cap trims what is actually rendered (spec 3.6). This only bounds the ranking work.
 */
private const val SecondaryLimit = 20

private const val ExactMatch = 0
private const val PrefixMatch = 1
private const val SubstringMatch = 2
private const val SecondaryFieldMatch = 3

private const val SongPriority = 0
private const val AlbumPriority = 1
private const val ArtistPriority = 2
private const val PlaylistPriority = 3

/**
 * Runs one committed query across songs, albums, artists and playlists and ranks the answers into
 * one typed result set.
 *
 * It talks to the repositories, never to `AutomotiveContentState`: filtering whatever the tabs
 * happened to have collected would turn cache timing into false "no results" (spec 3.3).
 *
 * Failures are not caught here. A partial result set would be a wrong answer wearing the shape of
 * a right one, so the ViewModel's existing all-or-error handling stays the only policy (R8).
 */
@Singleton
class AutomotiveCatalogSearch @Inject constructor(
    private val songRepository: SongRepository,
    private val albumRepository: AlbumRepository,
    private val artistRepository: ArtistRepository,
    private val playlistRepository: PlaylistRepository,
    private val authRepository: AuthRepository,
) {

    // ponytail: four sequential reads. Three are Room and land in microseconds; only playlists
    // leaves the device. Wrap them in async if that one read ever shows up in a trace.
    suspend fun search(rawQuery: String): AutomotiveSearchResults {
        val query = rawQuery.trim()
        if (query.isEmpty()) return AutomotiveSearchResults()
        val needle = query.lowercase()

        val songs = songRepository.searchSongs(query, SongLimit).map(::SongResult)
        val albums = albumRepository.searchAlbums(query, SecondaryLimit).map(::AlbumResult)
        val artists = artistRepository.searchArtists(query, SecondaryLimit).map(::ArtistResult)
        val playlists = searchPlaylists(query)

        val featured = (songs + albums + artists + playlists).minWithOrNull(bestFirst(needle))
        return AutomotiveSearchResults(
            query = query,
            featured = featured,
            songs = songs.withoutFeatured(featured),
            albums = albums.withoutFeatured(featured),
            artists = artists.withoutFeatured(featured),
            playlists = playlists.withoutFeatured(featured),
        )
    }

    /**
     * Signed out means no playlists to match, not a broken search: the other three sections are
     * catalogue-wide and still have honest answers.
     */
    private suspend fun searchPlaylists(query: String): List<PlaylistResult> {
        val userId = authRepository.currentUserId ?: return emptyList()
        return playlistRepository.searchPlaylists(userId, query, SecondaryLimit).map(::PlaylistResult)
    }
}

/** Best first: match quality, then type, then popularity, then title, then id. */
private fun bestFirst(needle: String): Comparator<AutomotiveSearchResult> = compareBy(
    { it.matchQuality(needle) },
    { it.typePriority },
    { -it.popularity },
    { it.title.lowercase() },
    { it.stableId },
)

/**
 * A result whose primary field does not contain the query matched on a secondary field — the
 * repository would not have returned it otherwise — so it ranks below every primary match.
 *
 * [AutomotiveSearchResult.title] is that primary field for all four types.
 */
private fun AutomotiveSearchResult.matchQuality(needle: String): Int {
    val name = title.lowercase()
    return when {
        name == needle -> ExactMatch
        name.startsWith(needle) -> PrefixMatch
        name.contains(needle) -> SubstringMatch
        else -> SecondaryFieldMatch
    }
}

/** Only breaks ties between equally good matches; it never outranks match quality. */
private val AutomotiveSearchResult.typePriority: Int
    get() = when (this) {
        is SongResult -> SongPriority
        is AlbumResult -> AlbumPriority
        is ArtistResult -> ArtistPriority
        is PlaylistResult -> PlaylistPriority
    }

private val AutomotiveSearchResult.popularity: Int
    get() = when (this) {
        is SongResult -> song.popularity
        is AlbumResult -> album.popularity
        is ArtistResult -> artist.popularity
        // Playlists are the driver's own; the model has no popularity to rank them by.
        is PlaylistResult -> 0
    }

/**
 * The section, minus the featured card that renders above it.
 *
 * Deliberately not re-sorted: each repository already returns its own type in a deterministic
 * match order, and re-ranking songs here would order them differently from
 * `MediaBrowseTree.search()`, which serves Assistant the repository's order (spec 3.5). The
 * comparator above is only for choosing which single card is featured.
 */
private fun <T : AutomotiveSearchResult> List<T>.withoutFeatured(
    featured: AutomotiveSearchResult?,
): List<T> = filterNot { it.stableId == featured?.stableId }
