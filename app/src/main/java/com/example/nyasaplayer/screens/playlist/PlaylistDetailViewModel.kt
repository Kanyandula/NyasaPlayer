package com.example.nyasaplayer.screens.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nyasaplayer.core.common.models.Song
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
class PlaylistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playlistRepository: PlaylistRepository,
    private val songRepository: SongRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val playlistId: String = checkNotNull(savedStateHandle["playlistId"])

    private val _uiState = MutableStateFlow(PlaylistDetailUiState())
    val uiState: StateFlow<PlaylistDetailUiState> = _uiState.asStateFlow()

    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        viewModelScope.launch {
            _snackbarMessage.emit(throwable.message ?: "An unexpected error occurred")
        }
    }

    init {
        loadPlaylistDetail()
    }

    private fun loadPlaylistDetail() {
        val userId = authRepository.currentUser?.uid ?: return
        viewModelScope.launch(exceptionHandler) {
            playlistRepository.getPlaylists(userId)
                .catch { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to load playlist",
                    )
                }
                .collect { playlists ->
                    val playlist = playlists.find { it.id == playlistId }
                    if (playlist == null) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Playlist not found",
                        )
                        return@collect
                    }
                    val songs = if (playlist.songIds.isNotEmpty()) {
                        try {
                            songRepository.getSongsByIds(playlist.songIds)
                        } catch (_: Exception) {
                            emptyList()
                        }
                    } else {
                        emptyList()
                    }
                    _uiState.value = PlaylistDetailUiState(
                        playlistName = playlist.name,
                        songs = songs,
                        isLoading = false,
                    )
                }
        }
    }

    fun removeSong(mediaId: String) {
        val userId = authRepository.currentUser?.uid ?: return
        viewModelScope.launch(exceptionHandler) {
            playlistRepository.removeSongFromPlaylist(userId, playlistId, mediaId)
            val name = _uiState.value.playlistName
            _snackbarMessage.emit("Removed from $name")
        }
    }

    fun retry() {
        _uiState.value = PlaylistDetailUiState()
        loadPlaylistDetail()
    }
}

data class PlaylistDetailUiState(
    val playlistName: String = "",
    val songs: List<Song> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)
