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

        val songs = songRepository.searchSongs(query, SongLimit).map { song ->
            SongResult(
                song = song,
                rank = rankOf(
                    primary = song.title,
                    needle = needle,
                    typePriority = SongPriority,
                    popularity = song.popularity,
                ),
            )
        }
        val albums = albumRepository.searchAlbums(query, SecondaryLimit).map { album ->
            AlbumResult(
                album = album,
                rank = rankOf(
                    primary = album.name,
                    needle = needle,
                    typePriority = AlbumPriority,
                    popularity = album.popularity,
                ),
            )
        }
        val artists = artistRepository.searchArtists(query, SecondaryLimit).map { artist ->
            ArtistResult(
                artist = artist,
                rank = rankOf(
                    primary = artist.name,
                    needle = needle,
                    typePriority = ArtistPriority,
                    popularity = artist.popularity,
                ),
            )
        }
        val playlists = searchPlaylists(query, needle)

        val featured = (songs + albums + artists + playlists).minWithOrNull(ResultOrder)
        return AutomotiveSearchResults(
            query = query,
            featured = featured,
            songs = songs.ranked(featured),
            albums = albums.ranked(featured),
            artists = artists.ranked(featured),
            playlists = playlists.ranked(featured),
        )
    }

    /**
     * Signed out means no playlists to match, not a broken search: the other three sections are
     * catalogue-wide and still have honest answers.
     */
    private suspend fun searchPlaylists(query: String, needle: String): List<PlaylistResult> {
        val userId = authRepository.currentUserId ?: return emptyList()
        return playlistRepository.searchPlaylists(userId, query, SecondaryLimit).map { playlist ->
            PlaylistResult(
                playlist = playlist,
                rank = rankOf(
                    primary = playlist.name,
                    needle = needle,
                    typePriority = PlaylistPriority,
                    popularity = 0,
                ),
            )
        }
    }
}

/**
 * A result whose primary field does not contain the query matched on a secondary field — the
 * repository would not have returned it otherwise — so it ranks below every primary match.
 */
private fun rankOf(
    primary: String,
    needle: String,
    typePriority: Int,
    popularity: Int,
): SearchRank {
    val lowercasePrimary = primary.lowercase()
    return SearchRank(
        matchQuality = when {
            lowercasePrimary == needle -> ExactMatch
            lowercasePrimary.startsWith(needle) -> PrefixMatch
            lowercasePrimary.contains(needle) -> SubstringMatch
            else -> SecondaryFieldMatch
        },
        typePriority = typePriority,
        popularity = popularity,
        sortTitle = lowercasePrimary,
    )
}

/**
 * Minus the featured card, which renders above the sections rather than inside one.
 *
 * Deliberately not re-sorted: each repository already returns its own type in a deterministic
 * match order, and re-ranking songs here would order them differently from
 * `MediaBrowseTree.search()`, which serves Assistant the repository's order (spec 3.5). The
 * cross-type comparator is only for choosing which card is featured.
 */
private fun <T : AutomotiveSearchResult> List<T>.ranked(featured: AutomotiveSearchResult?): List<T> =
    filterNot { it.stableId == featured?.stableId }
