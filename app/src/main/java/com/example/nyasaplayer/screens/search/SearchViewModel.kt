package com.example.nyasaplayer.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nyasaplayer.core.common.models.Genre
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.data.api.GenreRepository
import com.example.nyasaplayer.core.data.api.SongRepository
import com.example.nyasaplayer.util.isNetworkError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val genres: List<Genre> = emptyList(),
    val searchResults: List<Song> = emptyList(),
    val allSongs: List<Song> = emptyList(),
    val selectedGenre: Genre? = null,
    val genreSongs: List<Song> = emptyList(),
    val errorMessage: String? = null,
    val isNetworkError: Boolean = false,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val genreRepository: GenreRepository,
    private val songRepository: SongRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        _uiState.update {
            it.copy(
                isLoading = false,
                errorMessage = throwable.message ?: "An unexpected error occurred",
                isNetworkError = (throwable as? Exception)?.isNetworkError() == true,
            )
        }
    }

    init {
        loadData()
    }

    fun retry() {
        _uiState.value = SearchUiState()
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch(exceptionHandler) {
            combine(
                genreRepository.getGenres(),
                songRepository.getSongs(),
            ) { genres, songs ->
                val current = _uiState.value
                val selectedGenre = current.selectedGenre
                SearchUiState(
                    isLoading = false,
                    query = current.query,
                    genres = genres.sortedByDescending { it.popularity },
                    searchResults = if (current.query.isBlank()) {
                        emptyList()
                    } else {
                        filterSongs(songs, current.query)
                    },
                    allSongs = songs,
                    selectedGenre = selectedGenre,
                    genreSongs = if (selectedGenre != null) {
                        val ids = selectedGenre.songIds.toSet()
                        songs.filter { it.mediaId in ids }
                    } else {
                        emptyList()
                    },
                )
            }.catch { e ->
                val isNetwork = (e as? Exception)?.isNetworkError() == true
                _uiState.value = SearchUiState(
                    isLoading = false,
                    errorMessage = e.message ?: "Unknown error",
                    isNetworkError = isNetwork,
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun onQueryChange(query: String) {
        val current = _uiState.value
        _uiState.value = current.copy(
            query = query,
            searchResults = if (query.isBlank()) emptyList() else filterSongs(current.allSongs, query),
        )
    }

    fun onGenreSelected(genre: Genre) {
        val songIdSet = genre.songIds.toSet()
        _uiState.update {
            it.copy(
                selectedGenre = genre,
                genreSongs = it.allSongs.filter { song -> song.mediaId in songIdSet },
            )
        }
    }

    fun onGenreBack() {
        _uiState.update { it.copy(selectedGenre = null, genreSongs = emptyList()) }
    }

    private fun filterSongs(songs: List<Song>, query: String): List<Song> {
        val lower = query.lowercase()
        return songs.filter { song ->
            song.title.lowercase().contains(lower) ||
                song.resolvedArtistName.lowercase().contains(lower) ||
                song.albumName.lowercase().contains(lower)
        }
    }
}
