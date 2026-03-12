package com.example.nyasaplayer.auto.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nyasaplayer.core.common.models.Album
import com.example.nyasaplayer.core.common.models.Artist
import com.example.nyasaplayer.core.common.models.Genre
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.data.api.AlbumRepository
import com.example.nyasaplayer.core.data.api.ArtistRepository
import com.example.nyasaplayer.core.data.api.AuthRepository
import com.example.nyasaplayer.core.data.api.GenreRepository
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

@HiltViewModel
class AutomotiveContentViewModel @Inject constructor(
    private val songRepository: SongRepository,
    private val genreRepository: GenreRepository,
    private val artistRepository: ArtistRepository,
    private val albumRepository: AlbumRepository,
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _contentState = MutableStateFlow(AutomotiveContentState())
    val contentState: StateFlow<AutomotiveContentState> = _contentState.asStateFlow()

    private var searchJob: Job? = null
    private var recentlyPlayedJob: Job? = null
    private var likedSongsJob: Job? = null
    private var currentUserId: String? = null

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Uncaught error in content loading", throwable)
    }

    init {
        loadContent()
    }

    fun reloadUserContent() {
        val newUserId = authRepository.currentUser?.uid
        if (newUserId == currentUserId) return
        currentUserId = newUserId
        recentlyPlayedJob?.cancel()
        likedSongsJob?.cancel()
        _contentState.update { it.copy(recentlyPlayed = emptyList(), likedSongs = emptyList()) }
        loadRecentlyPlayed()
        observeLikedSongs()
    }

    private fun loadContent() {
        _contentState.update { it.copy(isLoading = true, errorMessage = null) }
        observeGenres()
        observeArtists()
        observeAlbums()
        loadRecentlyPlayed()
        loadPopularSongs()
        observeLikedSongs()
    }

    private fun observeGenres() {
        genreRepository.getGenres().onEach { genres ->
            _contentState.update { it.copy(genres = genres, isLoading = false) }
        }.catch { e ->
            Log.e(TAG, "Error observing genres", e)
            _contentState.update { it.copy(isLoading = false, errorMessage = "Failed to load content") }
        }.launchIn(viewModelScope)
    }

    private fun observeArtists() {
        artistRepository.getArtists().onEach { artists ->
            _contentState.update { it.copy(artists = artists, isLoading = false) }
        }.catch { e ->
            Log.e(TAG, "Error observing artists", e)
            _contentState.update { it.copy(isLoading = false, errorMessage = "Failed to load content") }
        }.launchIn(viewModelScope)
    }

    private fun observeAlbums() {
        albumRepository.getAlbums().onEach { albums ->
            _contentState.update { it.copy(albums = albums, isLoading = false) }
        }.catch { e ->
            Log.e(TAG, "Error observing albums", e)
            _contentState.update { it.copy(isLoading = false, errorMessage = "Failed to load content") }
        }.launchIn(viewModelScope)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun loadRecentlyPlayed() {
        val userId = authRepository.currentUser?.uid ?: return
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
        val userId = authRepository.currentUser?.uid ?: return
        likedSongsJob = userRepository.getLikedSongs(userId).onEach { likedEntries ->
            try {
                val songIds = likedEntries.map { it.mediaId }.distinct()
                val songMap = if (songIds.isNotEmpty()) {
                    songRepository.getSongsByIds(songIds).associateBy { it.mediaId }
                } else {
                    emptyMap()
                }
                val ordered = songIds.mapNotNull { songMap[it] }
                _contentState.update { it.copy(likedSongs = ordered) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error loading liked songs", e)
            }
        }.catch { e ->
            Log.e(TAG, "Error observing liked songs", e)
        }.launchIn(viewModelScope)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun loadPopularSongs() {
        viewModelScope.launch(exceptionHandler) {
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

    @Suppress("TooGenericExceptionCaught")
    suspend fun getSongsByArtist(artistId: String): List<Song> = try {
        songRepository.getSongsByArtist(artistId).firstOrNull() ?: emptyList()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Error loading songs for artist $artistId", e)
        emptyList()
    }

    @Suppress("TooGenericExceptionCaught")
    suspend fun getSongsByAlbum(albumId: String): List<Song> = try {
        val album = albumRepository.getAlbumById(albumId) ?: return emptyList()
        songRepository.getSongsByIds(album.songIds)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Error loading songs for album $albumId", e)
        emptyList()
    }
}

data class AutomotiveContentState(
    val recentlyPlayed: List<Song> = emptyList(),
    val genres: List<Genre> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val popularSongs: List<Song> = emptyList(),
    val likedSongs: List<Song> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<Song> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)
