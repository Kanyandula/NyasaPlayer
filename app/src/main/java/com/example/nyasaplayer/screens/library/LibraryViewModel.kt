package com.example.nyasaplayer.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nyasaplayer.data.AuthRepository
import com.example.nyasaplayer.data.SongRepository
import com.example.nyasaplayer.data.UserRepository
import com.example.nyasaplayer.models.Song
import com.example.nyasaplayer.util.NetworkMonitor
import com.example.nyasaplayer.util.isNetworkError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

data class LibraryUiState(
    val isLoading: Boolean = true,
    val songs: List<Song> = emptyList(),
    val errorMessage: String? = null,
    val isNetworkError: Boolean = false,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val songRepository: SongRepository,
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        loadLikedSongs()
    }

    fun retry() {
        _uiState.value = LibraryUiState()
        loadLikedSongs()
    }

    private fun loadLikedSongs() {
        val userId = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            if (!networkMonitor.isOnline.value) {
                _uiState.value = LibraryUiState(
                    isLoading = false,
                    errorMessage = "No internet connection",
                    isNetworkError = true,
                )
                return@launch
            }
            userRepository.getLikedSongs(userId)
                .catch { e ->
                    val isNetwork = (e as? Exception)?.isNetworkError() == true
                    _uiState.value = LibraryUiState(
                        isLoading = false,
                        errorMessage = e.message ?: "Unknown error",
                        isNetworkError = isNetwork,
                    )
                }
                .collectLatest { likedSongs ->
                    try {
                        val orderedSongs = if (likedSongs.isNotEmpty()) {
                            val mediaIds = likedSongs.map { it.mediaId }
                            val songs = songRepository.getSongsByIds(mediaIds)
                            val songMap = songs.associateBy { it.mediaId }
                            mediaIds.mapNotNull { songMap[it] }
                        } else {
                            emptyList()
                        }
                        _uiState.value = LibraryUiState(isLoading = false, songs = orderedSongs)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                        _uiState.value = LibraryUiState(
                            isLoading = false,
                            errorMessage = e.message ?: "Unknown error",
                            isNetworkError = e.isNetworkError(),
                        )
                    }
                }
        }
    }
}
