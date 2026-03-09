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

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Uncaught error in content loading", throwable)
    }

    init {
        loadContent()
    }

    private fun loadContent() {
        observeGenres()
        observeArtists()
        observeAlbums()
        loadRecentlyPlayed()
        loadPopularSongs()
    }

    private fun observeGenres() {
        genreRepository.getGenres().onEach { genres ->
            _contentState.update { it.copy(genres = genres) }
        }.catch { e ->
            Log.e(TAG, "Error observing genres", e)
        }.launchIn(viewModelScope)
    }

    private fun observeArtists() {
        artistRepository.getArtists().onEach { artists ->
            _contentState.update { it.copy(artists = artists) }
        }.catch { e ->
            Log.e(TAG, "Error observing artists", e)
        }.launchIn(viewModelScope)
    }

    private fun observeAlbums() {
        albumRepository.getAlbums().onEach { albums ->
            _contentState.update { it.copy(albums = albums) }
        }.catch { e ->
            Log.e(TAG, "Error observing albums", e)
        }.launchIn(viewModelScope)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun loadRecentlyPlayed() {
        val userId = authRepository.currentUser?.uid ?: return
        userRepository.getRecentlyPlayed(userId, RecentlyPlayedLimit).onEach { entries ->
            try {
                val songIds = entries.map { it.mediaId }
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
)
