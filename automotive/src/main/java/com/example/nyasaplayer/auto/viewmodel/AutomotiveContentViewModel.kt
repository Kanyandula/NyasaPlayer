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
private const val TAG = "AutoContentVM"
private const val DetailLoadError = "Could not load this. Check your connection and try again."
private const val AlbumMissingError = "This album is no longer available."
private const val PlaylistMissingError = "This playlist is no longer available."

@HiltViewModel
@Suppress("TooManyFunctions") // One view model backs all three tabs plus detail loading.
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

    private fun cancelContentJobs() {
        genresJob?.cancel()
        albumsJob?.cancel()
        popularSongsJob?.cancel()
        recentlyPlayedJob?.cancel()
        likedSongsJob?.cancel()
        playlistsJob?.cancel()
    }

    fun reloadUserContent() {
        val newUserId = authRepository.currentUserId
        if (newUserId == currentUserId) return
        currentUserId = newUserId
        recentlyPlayedJob?.cancel()
        likedSongsJob?.cancel()
        playlistsJob?.cancel()
        _contentState.update {
            it.copy(
                recentlyPlayed = emptyList(),
                likedSongs = emptyList(),
                favoriteArtists = emptyList(),
                playlists = emptyList(),
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
        cancelContentJobs()
        _contentState.update { it.copy(isLoading = true, errorMessage = null) }
        observeGenres()
        observeAlbums()
        loadRecentlyPlayed()
        loadPopularSongs()
        observeLikedSongs()
        observePlaylists()
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
        val userId = authRepository.currentUserId ?: return
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
                    it.copy(likedSongs = ordered, favoriteArtists = deriveFavoriteArtists(ordered))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error loading liked songs", e)
            }
        }.catch { e ->
            Log.e(TAG, "Error observing liked songs", e)
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
     */
    fun openDetail(destination: CarDestination) {
        detailJob?.cancel()
        if (destination is CarDestination.Artist) {
            _contentState.update { it.copy(detail = null) }
            return
        }
        _contentState.update { it.copy(detail = CarDetailState(destination = destination)) }
        detailJob = viewModelScope.launch(exceptionHandler) {
            val loaded = loadDetail(destination)
            _contentState.update { state ->
                // The guard belongs inside the atomic update, not in a pre-read: a
                // check-then-write pair is a race in exactly the scenario it exists to prevent.
                // Cancellation alone is not enough — a coroutine suspended in a repository call
                // resumes and can reach here before the cancellation is observed.
                if (state.detail?.destination == destination) state.copy(detail = loaded) else state
            }
        }
    }

    fun closeDetail() {
        detailJob?.cancel()
        _contentState.update { it.copy(detail = null) }
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
        val tracks = songRepository.getSongsByIds(album.songIds)
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
    val playlists: List<Playlist> = emptyList(),
    val detail: CarDetailState? = null,
    val searchQuery: String = "",
    val searchResults: List<Song> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

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
