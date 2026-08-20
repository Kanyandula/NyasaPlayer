@file:Suppress(
    // 19 functions before A4, against detekt's thresholdInFiles of 20. The class-level suppression
    // does not cover the file threshold. This class now owns search, genres, albums, playlists,
    // recently-played, liked songs, popular, detail and favourites — the next slice to touch it
    // should split it rather than suppress again. See spec D23.
    "TooManyFunctions",
)

package com.example.nyasaplayer.auto.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nyasaplayer.auto.ui.navigation.CarDestination
import com.example.nyasaplayer.core.common.models.Album
import com.example.nyasaplayer.core.common.models.Genre
import com.example.nyasaplayer.core.common.models.Playlist
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.data.api.AlbumRepository
import com.example.nyasaplayer.core.data.api.AuthRepository
import com.example.nyasaplayer.core.data.api.GenreRepository
import com.example.nyasaplayer.core.data.api.PlaylistRepository
import com.example.nyasaplayer.core.data.api.SongRepository
import com.example.nyasaplayer.core.data.api.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

private const val RecentlyPlayedLimit = 12
private const val PopularLimit = 8
private const val SearchLimit = 50
private const val SearchDebounceMs = 300L
private const val FavouritesLoadError = "Couldn't load your favourites."
private const val TAG = "AutoContentVM"
private const val DetailLoadError = "Could not load this. Check your connection and try again."
private const val AlbumMissingError = "This album is no longer available."
private const val PlaylistMissingError = "This playlist is no longer available."

@HiltViewModel
class AutomotiveContentViewModel @Inject constructor(
    private val songRepository: SongRepository,
    private val genreRepository: GenreRepository,
    private val albumRepository: AlbumRepository,
    private val playlistRepository: PlaylistRepository,
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _contentState = MutableStateFlow(AutomotiveContentState())
    val contentState: StateFlow<AutomotiveContentState> = _contentState.asStateFlow()

    private var searchJob: Job? = null
    private var recentlyPlayedJob: Job? = null
    private var likedSongsJob: Job? = null
    private var genresJob: Job? = null
    private var albumsJob: Job? = null
    private var playlistsJob: Job? = null
    private var detailJob: Job? = null
    private var detailToken = 0
    private var popularSongsJob: Job? = null
    private var currentUserId: String? = null

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Uncaught error in content loading", throwable)
    }

    init {
        loadContent()
    }

    /**
     * Retry after a load failure.
     *
     * Distinct from [reloadUserContent], which early-returns when the signed-in user has not
     * changed — it is a user-switch hook, so wiring an error state's Retry button to it would
     * produce a no-op.
     */
    fun retryLoad() {
        loadContent()
    }

    private fun cancelCatalogueJobs() {
        genresJob?.cancel()
        albumsJob?.cancel()
        popularSongsJob?.cancel()
    }

    private fun cancelUserJobs() {
        recentlyPlayedJob?.cancel()
        likedSongsJob?.cancel()
        playlistsJob?.cancel()
    }

    fun reloadUserContent() {
        val newUserId = authRepository.currentUserId
        if (newUserId == currentUserId) return
        // A null id on recreation is auth not having restored yet, not a switch to nobody.
        // Acting on it would reflow Favourites under a driver who never left the screen; the
        // real sign-out path tears the session down through AutomotiveAuthViewModel.
        if (newUserId == null) return
        currentUserId = newUserId
        recentlyPlayedJob?.cancel()
        likedSongsJob?.cancel()
        playlistsJob?.cancel()
        _contentState.update {
            it.copy(
                recentlyPlayed = emptyList(),
                likedSongs = emptyList(),
                likedSongsLoaded = false,
                favoriteArtists = emptyList(),
                playlists = emptyList(),
                // A previous account's freeze must not survive a sign-out.
                favourites = null,
                pendingUnlikes = emptySet(),
                // Account-scoped, so it goes with the account. The shared errorMessage does
                // not: a catalogue failure belongs to the process, and switching user is no
                // evidence it recovered.
                favouritesError = null,
            )
        }
        loadRecentlyPlayed()
        observeLikedSongs()
        observePlaylists()
    }

    private fun loadContent() {
        // Cancel before relaunching. loadContent() used to be reachable only from init, so
        // this was latent; retryLoad() puts it behind a button a driver may tap repeatedly,
        // and each call otherwise leaks a Room collector and a Firestore snapshot listener
        // for the life of the ViewModel.
        cancelCatalogueJobs()
        _contentState.update {
            // likedSongsLoaded too: without it Favourites keeps whatever it was showing —
            // including a false "no favourites yet" — for the whole reload.
            it.copy(
                isLoading = true,
                errorMessage = null,
                favouritesError = null,
                likedSongsLoaded = false,
            )
        }
        observeGenres()
        observeAlbums()
        loadPopularSongs()

        // The user-scoped collectors are only torn down when there is a user to restart them
        // for. Retrying while signed out would otherwise kill three working collectors and
        // leave the screens they feed with no source and no error to retry from.
        if (authRepository.currentUserId != null) {
            cancelUserJobs()
            loadRecentlyPlayed()
            observeLikedSongs()
            observePlaylists()
        } else {
            _contentState.update {
                it.copy(likedSongsLoaded = true, favouritesError = FavouritesLoadError)
            }
        }
    }

    private fun observeGenres() {
        genresJob = genreRepository.getGenres().onEach { genres ->
            _contentState.update { it.copy(genres = genres, isLoading = false) }
        }.catch { e ->
            Log.e(TAG, "Error observing genres", e)
            _contentState.update { it.copy(isLoading = false, errorMessage = "Failed to load content") }
        }.launchIn(viewModelScope)
    }

    private fun observeAlbums() {
        albumsJob = albumRepository.getAlbums().onEach { albums ->
            _contentState.update { it.copy(albums = albums, isLoading = false) }
        }.catch { e ->
            Log.e(TAG, "Error observing albums", e)
            _contentState.update { it.copy(isLoading = false, errorMessage = "Failed to load content") }
        }.launchIn(viewModelScope)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun loadRecentlyPlayed() {
        val userId = authRepository.currentUserId ?: return
        currentUserId = userId
        recentlyPlayedJob = userRepository.getRecentlyPlayed(userId, RecentlyPlayedLimit).onEach { entries ->
            try {
                val songIds = entries.map { it.mediaId }.distinct()
                val songMap = songRepository.getSongsByIds(songIds).associateBy { it.mediaId }
                val ordered = songIds.mapNotNull { songMap[it] }
                _contentState.update { it.copy(recentlyPlayed = ordered) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error loading recently played songs", e)
            }
        }.catch { e ->
            Log.e(TAG, "Error observing recently played", e)
        }.launchIn(viewModelScope)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun observeLikedSongs() {
        // No user means no collector will ever run, so the load has to be declared over here or
        // Favourites keeps its skeleton for the session — with no error, and so no Retry.
        val userId = authRepository.currentUserId ?: run {
            _contentState.update {
                it.copy(likedSongsLoaded = true, favouritesError = FavouritesLoadError)
            }
            return
        }
        likedSongsJob = userRepository.getLikedSongs(userId).onEach { likedEntries ->
            try {
                val songIds = likedEntries.map { it.mediaId }.distinct()
                val songMap = if (songIds.isNotEmpty()) {
                    songRepository.getSongsByIds(songIds).associateBy { it.mediaId }
                } else {
                    emptyMap()
                }
                val ordered = songIds.mapNotNull { songMap[it] }
                _contentState.update {
                    it.copy(
                        likedSongs = ordered,
                        favoriteArtists = deriveFavoriteArtists(ordered),
                        likedSongsLoaded = true,
                        // A good emission retires the previous failure; otherwise the message
                        // outlives its cause and Favourites shows an error over real songs.
                        favouritesError = null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error loading liked songs", e)
                // The load is over either way. Leaving the flag false would strand Favourites
                // on its skeleton for the rest of the session. The failure goes on the
                // dedicated channel: Home, Browse and Library read the shared field, and a
                // liked-songs failure is not evidence that the catalogue failed.
                _contentState.update {
                    it.copy(likedSongsLoaded = true, favouritesError = FavouritesLoadError)
                }
            }
        }.catch { e ->
            Log.e(TAG, "Error observing liked songs", e)
            _contentState.update {
                it.copy(likedSongsLoaded = true, favouritesError = FavouritesLoadError)
            }
        }.launchIn(viewModelScope)
    }

    private fun observePlaylists() {
        val userId = authRepository.currentUserId ?: return
        playlistsJob = playlistRepository.getPlaylists(userId).onEach { playlists ->
            _contentState.update { it.copy(playlists = playlists) }
        }.catch { e ->
            Log.e(TAG, "Error observing playlists", e)
        }.launchIn(viewModelScope)
    }

    private fun deriveFavoriteArtists(likedSongs: List<Song>): List<FavoriteArtist> =
        likedSongs
            .filter { it.artistId.isNotBlank() }
            .groupBy { it.artistId }
            .map { (artistId, songs) ->
                FavoriteArtist(
                    artistId = artistId,
                    artistName = songs.first().resolvedArtistName,
                    coverUrl = songs.first().resolvedCoverUrl,
                    likedCount = songs.size,
                )
            }
            .sortedByDescending { it.likedCount }

    @Suppress("TooGenericExceptionCaught")
    private fun loadPopularSongs() {
        popularSongsJob = viewModelScope.launch(exceptionHandler) {
            try {
                val songs = songRepository.getSongsByPopularity(PopularLimit)
                _contentState.update { it.copy(popularSongs = songs) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error loading popular songs", e)
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _contentState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _contentState.update { it.copy(searchResults = emptyList()) }
            return
        }
        searchJob = viewModelScope.launch(exceptionHandler) {
            delay(SearchDebounceMs)
            @Suppress("TooGenericExceptionCaught")
            try {
                val results = songRepository.searchSongs(query, SearchLimit)
                _contentState.update { it.copy(searchResults = results) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error searching songs", e)
                _contentState.update { it.copy(searchResults = emptyList()) }
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _contentState.update { it.copy(searchQuery = "", searchResults = emptyList()) }
    }

    @Suppress("TooGenericExceptionCaught")
    suspend fun getSongsByGenre(genreId: String): List<Song> = try {
        songRepository.getSongsByGenre(genreId).firstOrNull() ?: emptyList()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Error loading songs for genre $genreId", e)
        emptyList()
    }

    /**
     * Load the content behind [destination] into `contentState.detail`.
     *
     * Driven from one `LaunchedEffect(drillDown)` in the shell, which means this fires **once**
     * per destination and never re-runs when data arrives later. Everything it reads therefore
     * comes from a repository call, not from `_contentState` — including the call that follows
     * process death, when the observed flows have not emitted yet (D17).
     *
     * Guarded against redundant reloads: `drillDown` survives activity recreation (a night-mode
     * `uiMode` flip, say) via `rememberSaveable`, and `LaunchedEffect(drillDown)` fires again on
     * every fresh composition — including recreation. Without this guard an already-settled
     * detail would flash back to its skeleton and re-hit Room/Firestore for no reason. A
     * mismatched destination, a still-loading state, or a settled error all fall through and
     * reload, so retry after a failure keeps working.
     */
    fun openDetail(destination: CarDestination) {
        val settled = _contentState.value.detail
        val isSettledForDestination = settled != null &&
            settled.destination == destination &&
            !settled.isLoading &&
            settled.errorMessage == null
        if (isSettledForDestination) {
            return
        }
        detailJob?.cancel()
        val token = ++detailToken
        if (destination is CarDestination.Artist) {
            _contentState.update { it.copy(detail = null) }
            return
        }
        _contentState.update { it.copy(detail = CarDetailState(destination = destination)) }
        detailJob = viewModelScope.launch(exceptionHandler) {
            val loaded = loadDetail(destination)
            _contentState.update { state ->
                // Rejects the result of a stale coroutine that resumed anyway. cancel() is not
                // enough: a continuation parked in a Firestore or Room callback can be resumed
                // by that callback without ever observing the cancellation, and then run on to
                // here. [token] is what actually decides it — [destination] alone would let a
                // stale load of album A overwrite a later, successful load of the same album A.
                val isCurrent = token == detailToken && state.detail?.destination == destination
                if (isCurrent) state.copy(detail = loaded) else state
            }
        }
    }

    fun closeDetail() {
        detailJob?.cancel()
        detailToken++
        _contentState.update { it.copy(detail = null) }
    }

    /**
     * Marks a visit to the Favourites tab.
     *
     * Deliberately does not freeze (spec D19) and deliberately clears nothing (spec D20). The
     * effect that calls this re-runs on every Activity recreation — a night-mode flip mid-drive —
     * and anything cleared here would silently reconcile the driver's held-back rows.
     */
    fun openFavourites() {
        _contentState.update {
            when {
                // D20: a freeze already being held is never disturbed, nor the pends behind it.
                it.favourites != null -> it
                // Nothing loaded yet, so there is nothing honest to freeze — an entry freeze
                // taken here would snapshot Firestore's empty cached snapshot, which is the
                // objection D19 chose the first-unlike freeze to avoid.
                !it.likedSongsLoaded -> it.copy(pendingUnlikes = emptySet())
                // Loaded: freeze on entry, so another device's like or unlike cannot move rows
                // under the driver mid-visit. Unlike is the common cause, not the only one.
                else -> it.copy(favourites = it.likedSongs, pendingUnlikes = emptySet())
            }
        }
    }

    /** Ends the visit. The next unlike starts a new freeze. */
    fun closeFavourites() {
        _contentState.update { it.copy(favourites = null, pendingUnlikes = emptySet()) }
    }

    /**
     * Toggles one song's liked state, optimistically.
     *
     * [freeze] is the caller's screen contract, not a preference. `CarFavouritesScreen` passes
     * true: the first unlike of a visit freezes the list as it stands *before* the removal lands,
     * so the row cannot move under the driver (spec D19). `CarArtistLikedSongsScreen` passes
     * false: its list is deliberately live and removes rows immediately (spec D25), and a freeze
     * taken there would otherwise leak into the next Favourites visit — the effect that calls
     * [closeFavourites] is keyed on the tab, and the drill-down never leaves the Library tab.
     * A `freeze = false` call still records the pending unlike and still performs the write.
     *
     * Returns false when the write failed, having already reverted the optimistic change; the
     * caller surfaces the error. A freeze already taken is deliberately *not* released on
     * failure — the list still must not reflow under the driver's finger.
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun toggleFavourite(mediaId: String, freeze: Boolean): Boolean {
        val userId = authRepository.currentUserId ?: return false
        val wasPending = mediaId in _contentState.value.pendingUnlikes
        _contentState.update { state ->
            state.copy(
                // Freeze on the first unlike only. Never re-freeze: a second call must not
                // recapture a list the live flow has already changed.
                favourites = if (freeze) state.favourites ?: state.likedSongs else state.favourites,
                pendingUnlikes = if (wasPending) {
                    state.pendingUnlikes - mediaId
                } else {
                    state.pendingUnlikes + mediaId
                },
            )
        }
        return try {
            if (wasPending) {
                userRepository.likeSong(userId, mediaId)
            } else {
                userRepository.unlikeSong(userId, mediaId)
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling favourite $mediaId", e)
            _contentState.update { state ->
                state.copy(
                    pendingUnlikes = if (wasPending) {
                        state.pendingUnlikes + mediaId
                    } else {
                        state.pendingUnlikes - mediaId
                    },
                )
            }
            false
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun loadDetail(destination: CarDestination): CarDetailState = try {
        when (destination) {
            is CarDestination.Album -> loadAlbumDetail(destination)
            is CarDestination.Playlist -> loadPlaylistDetail(destination)
            // Filtered out by openDetail; a when over a sealed interface must be exhaustive.
            is CarDestination.Artist -> CarDetailState(destination, isLoading = false)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Error loading detail for $destination", e)
        CarDetailState(destination = destination, isLoading = false, errorMessage = DetailLoadError)
    }

    private suspend fun loadAlbumDetail(destination: CarDestination.Album): CarDetailState {
        val album = albumRepository.getAlbumById(destination.albumId)
            ?: return CarDetailState(destination, isLoading = false, errorMessage = AlbumMissingError)
        // Album songIds come straight from synced Firestore data with no dedupe guarantee,
        // unlike PlaylistRepository which already guards against duplicates. A duplicated id
        // would otherwise crash CarDetailScreen's LazyColumn, keyed on mediaId.
        val tracks = songRepository.getSongsByIds(album.songIds).distinctBy { it.mediaId }
        return CarDetailState(
            destination = destination,
            title = album.name,
            subtitle = album.artistName,
            artworkUrl = album.imageUrl,
            tracks = tracks,
            isLoading = false,
        )
    }

    private suspend fun loadPlaylistDetail(destination: CarDestination.Playlist): CarDetailState {
        val userId = authRepository.currentUserId
            ?: return CarDetailState(destination, isLoading = false, errorMessage = PlaylistMissingError)
        // first(), not contentState.playlists: this suspends until Firestore's first emission
        // rather than racing it. Playlist has no getPlaylistById to read one-shot (D17).
        val playlist = playlistRepository.getPlaylists(userId).first()
            .firstOrNull { it.id == destination.playlistId }
            ?: return CarDetailState(destination, isLoading = false, errorMessage = PlaylistMissingError)
        val tracks = songRepository.getSongsByIds(playlist.songIds)
        return CarDetailState(
            destination = destination,
            title = playlist.name,
            // Playlist has no cover field; artwork is the first resolved track's, same
            // derivation deriveFavoriteArtists() uses for artist avatars.
            artworkUrl = tracks.firstOrNull()?.resolvedCoverUrl.orEmpty(),
            tracks = tracks,
            isLoading = false,
        )
    }
}

data class FavoriteArtist(
    val artistId: String,
    val artistName: String,
    val coverUrl: String,
    val likedCount: Int,
) : java.io.Serializable

data class AutomotiveContentState(
    val recentlyPlayed: List<Song> = emptyList(),
    val genres: List<Genre> = emptyList(),
    val favoriteArtists: List<FavoriteArtist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val popularSongs: List<Song> = emptyList(),
    val likedSongs: List<Song> = emptyList(),
    // Liked songs alone: [isLoading] is flipped false by the first genres or albums emission,
    // both from Room, while liked songs still need a Firestore round trip plus a song resolve.
    // Favourites reads this instead, or it offers "No favourites yet" — and a CTA off the tab —
    // to a driver who does have favourites, which §4.1 of the spec rules out.
    val likedSongsLoaded: Boolean = false,
    val playlists: List<Playlist> = emptyList(),
    val favourites: List<Song>? = null,
    val pendingUnlikes: Set<String> = emptySet(),
    val detail: CarDetailState? = null,
    val searchQuery: String = "",
    val searchResults: List<Song> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    /** Liked-songs failures only. Kept off [errorMessage], which Home/Browse/Library render. */
    val favouritesError: String? = null,
) {
    /** What screen 8 binds, so the screen and its tests read one derivation rather than two. */
    val favouritesLoading: Boolean get() = !likedSongsLoaded
}

/**
 * One loaded detail screen — album or playlist.
 *
 * Artist detail is deliberately absent: its track list is a live filter over `likedSongs`, and
 * snapshotting it here would freeze the screen against unlikes performed on it (D16).
 */
data class CarDetailState(
    val destination: CarDestination,
    val title: String = "",
    val subtitle: String = "",
    val artworkUrl: String = "",
    val tracks: List<Song> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)
