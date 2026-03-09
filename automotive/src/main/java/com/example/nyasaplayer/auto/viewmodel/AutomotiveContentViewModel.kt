package com.example.nyasaplayer.auto.viewmodel

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val RecentlyPlayedLimit = 12
private const val PopularLimit = 8

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

    init {
        loadContent()
    }

    private fun loadContent() {
        observeGenres()
        observeArtists()
        observeAlbums()
        loadRecentlyPlayed()
    }

    private fun observeGenres() {
        genreRepository.getGenres().onEach { genres ->
            _contentState.update { it.copy(genres = genres) }
        }.launchIn(viewModelScope)
    }

    private fun observeArtists() {
        artistRepository.getArtists().onEach { artists ->
            _contentState.update { it.copy(artists = artists) }
        }.launchIn(viewModelScope)
    }

    private fun observeAlbums() {
        albumRepository.getAlbums().onEach { albums ->
            _contentState.update { it.copy(albums = albums) }
        }.launchIn(viewModelScope)
    }

    private fun loadRecentlyPlayed() {
        val userId = authRepository.currentUser?.uid ?: return
        userRepository.getRecentlyPlayed(userId, RecentlyPlayedLimit).onEach { entries ->
            val songIds = entries.map { it.mediaId }
            val songs = songRepository.getSongsByIds(songIds)
            _contentState.update { it.copy(recentlyPlayed = songs) }
        }.launchIn(viewModelScope)
    }

    fun loadPopularSongs() {
        viewModelScope.launch {
            val songs = songRepository.getSongsByPopularity(PopularLimit)
            _contentState.update { it.copy(popularSongs = songs) }
        }
    }
}

data class AutomotiveContentState(
    val recentlyPlayed: List<Song> = emptyList(),
    val genres: List<Genre> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val popularSongs: List<Song> = emptyList(),
)
