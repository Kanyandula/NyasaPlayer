package com.example.nyasaplayer.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nyasaplayer.data.AuthRepository
import com.example.nyasaplayer.data.HomeFeedRepository
import com.example.nyasaplayer.data.SongRepository
import com.example.nyasaplayer.data.UserRepository
import com.example.nyasaplayer.models.HomeFeed
import com.example.nyasaplayer.models.RecentlyPlayedEntry
import com.example.nyasaplayer.models.Song
import com.example.nyasaplayer.util.NetworkMonitor
import com.example.nyasaplayer.util.isNetworkError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ResolvedSection(
    val id: String = "",
    val title: String = "",
    val type: String = "",
    val songs: List<Song> = emptyList(),
)

data class ForYouUiState(
    val isLoading: Boolean = true,
    val sections: List<ResolvedSection> = emptyList(),
    val allSongs: List<Song> = emptyList(),
    val recentlyPlayed: List<Song> = emptyList(),
    val photoUrl: String = "",
    val errorMessage: String? = null,
    val isNetworkError: Boolean = false,
)

@HiltViewModel
class ForYouViewModel @Inject constructor(
    private val songRepository: SongRepository,
    private val homeFeedRepository: HomeFeedRepository,
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForYouUiState())
    val uiState: StateFlow<ForYouUiState> = _uiState.asStateFlow()

    init {
        loadFeed()
    }

    fun retry() {
        _uiState.value = ForYouUiState()
        loadFeed()
    }

    private fun loadFeed() {
        viewModelScope.launch {
            if (!networkMonitor.isOnline.value) {
                _uiState.value = ForYouUiState(
                    isLoading = false,
                    errorMessage = "No internet connection",
                    isNetworkError = true,
                )
                return@launch
            }
            val userId = authRepository.currentUser?.uid
            val recentlyPlayedFlow = if (userId != null) {
                userRepository.getRecentlyPlayed(userId)
            } else {
                flowOf(emptyList())
            }
            combine(
                songRepository.getSongs(),
                homeFeedRepository.getHomeFeed(),
                recentlyPlayedFlow,
            ) { songs, homeFeed, recentEntries ->
                buildUiState(songs, homeFeed, recentEntries)
            }.catch { e ->
                val isNetwork = (e as? Exception)?.isNetworkError() == true
                _uiState.value = ForYouUiState(
                    isLoading = false,
                    errorMessage = e.message ?: "Unknown error",
                    isNetworkError = isNetwork,
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    private fun buildUiState(
        songs: List<Song>,
        homeFeed: HomeFeed?,
        recentEntries: List<RecentlyPlayedEntry>,
    ): ForYouUiState {
        if (songs.isEmpty()) return ForYouUiState(isLoading = true)

        val songMap = songs.associateBy { it.mediaId }
        val sections = if (homeFeed != null && homeFeed.sections.isNotEmpty()) {
            homeFeed.sections.map { section ->
                val sectionSongs = if (section.songIds.isEmpty()) {
                    songs
                } else {
                    section.songIds.mapNotNull { songMap[it] }
                }
                ResolvedSection(
                    id = section.id,
                    title = section.title,
                    type = section.type,
                    songs = sectionSongs,
                )
            }
        } else {
            listOf(
                ResolvedSection(id = "all_songs", title = "All Songs", type = "list", songs = songs),
            )
        }
        val recentSongs = recentEntries
            .mapNotNull { songMap[it.mediaId] }
            .distinctBy { it.mediaId }

        return ForYouUiState(
            isLoading = false,
            sections = sections,
            allSongs = songs,
            recentlyPlayed = recentSongs,
            photoUrl = authRepository.currentUser?.photoUrl?.toString().orEmpty(),
        )
    }
}
