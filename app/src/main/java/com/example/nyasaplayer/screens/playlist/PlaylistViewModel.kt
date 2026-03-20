package com.example.nyasaplayer.screens.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nyasaplayer.core.common.models.Playlist
import com.example.nyasaplayer.core.data.SongAlreadyInPlaylistException
import com.example.nyasaplayer.core.data.api.AuthRepository
import com.example.nyasaplayer.core.data.api.PlaylistRepository
import com.example.nyasaplayer.core.data.api.SongRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val authRepository: AuthRepository,
    private val songRepository: SongRepository,
) : ViewModel() {

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _playlistCovers = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val playlistCovers: StateFlow<Map<String, List<String>>> = _playlistCovers.asStateFlow()

    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        viewModelScope.launch {
            _snackbarMessage.emit(throwable.message ?: "An unexpected error occurred")
        }
    }

    init {
        loadPlaylists()
    }

    private fun loadPlaylists() {
        val userId = authRepository.currentUser?.uid ?: return
        viewModelScope.launch(exceptionHandler) {
            playlistRepository.getPlaylists(userId)
                .catch { e ->
                    _snackbarMessage.emit(e.message ?: "Failed to load playlists")
                }
                .collect { playlists ->
                    _playlists.value = playlists
                    viewModelScope.launch { loadPlaylistCovers(playlists) }
                }
        }
    }

    private suspend fun loadPlaylistCovers(playlists: List<Playlist>) {
        val covers = mutableMapOf<String, List<String>>()
        for (playlist in playlists) {
            if (playlist.songIds.isEmpty()) continue
            try {
                val ids = playlist.songIds.take(MAX_COVER_COUNT)
                val songs = songRepository.getSongsByIds(ids)
                val urls = songs.mapNotNull { it.resolvedCoverUrl }.distinct().take(MAX_COVER_COUNT)
                if (urls.isNotEmpty()) {
                    covers[playlist.id] = urls
                }
            } catch (_: Exception) {
                // Covers are non-critical; silently skip failures
            }
        }
        _playlistCovers.value = covers
    }

    fun addSongToPlaylist(playlistId: String, mediaId: String) {
        val userId = authRepository.currentUser?.uid ?: return
        viewModelScope.launch(exceptionHandler) {
            try {
                playlistRepository.addSongToPlaylist(userId, playlistId, mediaId)
                val playlistName = _playlists.value.find { it.id == playlistId }?.name.orEmpty()
                _snackbarMessage.emit("Added to $playlistName")
            } catch (e: SongAlreadyInPlaylistException) {
                _snackbarMessage.emit(e.message ?: "Song already in playlist")
            }
        }
    }

    fun createPlaylistAndAddSong(name: String, mediaId: String) {
        val userId = authRepository.currentUser?.uid ?: return
        viewModelScope.launch(exceptionHandler) {
            val playlistId = playlistRepository.createPlaylist(userId, name)
            playlistRepository.addSongToPlaylist(userId, playlistId, mediaId)
            _snackbarMessage.emit("Added to $name")
        }
    }

    fun removeSongFromPlaylist(playlistId: String, mediaId: String) {
        val userId = authRepository.currentUser?.uid ?: return
        viewModelScope.launch(exceptionHandler) {
            playlistRepository.removeSongFromPlaylist(userId, playlistId, mediaId)
            val playlistName = _playlists.value.find { it.id == playlistId }?.name.orEmpty()
            _snackbarMessage.emit("Removed from $playlistName")
        }
    }

    companion object {
        private const val MAX_COVER_COUNT = 4
    }
}
