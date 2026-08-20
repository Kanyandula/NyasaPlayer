package com.example.nyasaplayer.auto.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.data.api.SongRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

private const val SearchLimit = 50
private const val MaxRecentQueries = 5
private const val TAG = "AutoSearchVM"
private const val SearchErrorMessage = "Couldn't search right now. Check your connection."

/**
 * Owns search for the car launcher. Split out of `AutomotiveContentViewModel` rather than added
 * to it (A6 D-record): that class already owns nine content areas and carries a
 * `TooManyFunctions` suppression.
 *
 * Search runs on **explicit submit**, never per keystroke — a driver mid-word should not be
 * generating storage work.
 */
@HiltViewModel
class AutomotiveSearchViewModel @Inject constructor(
    private val songRepository: SongRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AutomotiveSearchUiState())
    val uiState: StateFlow<AutomotiveSearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    // Rapid submits race: a slow first query can return after a fast second one. Only the newest
    // token may write results, so a stale answer is dropped rather than replacing the new one.
    private var latestToken = 0

    /** Draft text only. Submitting is what searches. */
    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun submitSearch() = runSearch(_uiState.value.query)

    fun selectRecentQuery(query: String) = runSearch(query)

    /** Re-runs the last committed query, which survives a failure so Retry has something to run. */
    fun retrySearch() = runSearch(_uiState.value.submittedQuery)

    /**
     * Leaves the results view for the search view without losing the draft query.
     *
     * Distinct from [clearQuery], which throws the query away too. Dropping `submittedQuery` is
     * what closes results — the sheet has no separate "showing results" flag to fall out of sync
     * with — so an in-flight search is cancelled here for the same reason it is there.
     */
    fun backToSearch() {
        searchJob?.cancel()
        latestToken++
        _uiState.update {
            it.copy(
                submittedQuery = "",
                results = emptyList(),
                isLoading = false,
                errorMessage = null,
            )
        }
    }

    fun clearQuery() {
        searchJob?.cancel()
        latestToken++
        _uiState.update {
            AutomotiveSearchUiState(recentQueries = it.recentQueries, isEditing = it.isEditing)
        }
    }

    /** Whether an editable field is active — the thing driving restrictions care about. */
    fun setEditing(isEditing: Boolean) {
        _uiState.update { it.copy(isEditing = isEditing) }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun runSearch(rawQuery: String) {
        val query = rawQuery.trim()
        searchJob?.cancel()
        val token = ++latestToken

        if (query.isEmpty()) {
            _uiState.update { it.copy(isLoading = false, errorMessage = null) }
            return
        }

        _uiState.update {
            it.copy(
                query = query,
                submittedQuery = query,
                isLoading = true,
                errorMessage = null,
                // Not kept as "previous content during refresh": the header names the new query,
                // so the old query's songs sitting under it would be a wrong answer, not a stale
                // one. It also keeps the failure state honest — an error never shows rows.
                results = emptyList(),
                // Committing the search closes the editor. Results are allowed while driving;
                // an active field is not, so a stale flag here would have the gate evict the
                // driver from a results list they are allowed to be looking at.
                isEditing = false,
            )
        }
        searchJob = viewModelScope.launch {
            try {
                val results = songRepository.searchSongs(query, SearchLimit)
                if (token != latestToken) return@launch
                _uiState.update {
                    it.copy(
                        results = results,
                        isLoading = false,
                        recentQueries = it.recentQueries.withRecent(query),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Search failed for \"$query\"", e)
                if (token != latestToken) return@launch
                _uiState.update { it.copy(isLoading = false, errorMessage = SearchErrorMessage) }
            }
        }
    }
}

private fun List<String>.withRecent(query: String): List<String> =
    (listOf(query) + filterNot { it.equals(query, ignoreCase = true) }).take(MaxRecentQueries)

data class AutomotiveSearchUiState(
    val query: String = "",
    val submittedQuery: String = "",
    val results: List<Song> = emptyList(),
    val recentQueries: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isEditing: Boolean = false,
)
