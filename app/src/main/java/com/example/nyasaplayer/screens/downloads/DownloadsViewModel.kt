package com.example.nyasaplayer.screens.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.data.api.DownloadRepository
import com.example.nyasaplayer.core.data.api.SongRepository
import com.example.nyasaplayer.download.SongDownloadManager
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
import kotlin.coroutines.cancellation.CancellationException

data class DownloadsUiState(
    val isLoading: Boolean = true,
    val songs: List<Song> = emptyList(),
    val downloadCount: Int = 0,
    val totalSizeBytes: Long = 0L,
    val errorMessage: String? = null,
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository,
    private val songRepository: SongRepository,
    private val downloadManager: SongDownloadManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        _uiState.update {
            it.copy(
                isLoading = false,
                errorMessage = throwable.message ?: "An unexpected error occurred",
            )
        }
    }

    init {
        loadDownloads()
    }

    fun retry() {
        _uiState.value = DownloadsUiState()
        loadDownloads()
    }

    fun removeDownload(mediaId: String) {
        downloadManager.removeDownload(mediaId)
    }

    fun removeAllDownloads() {
        downloadManager.removeAllDownloads()
    }

    private fun loadDownloads() {
        viewModelScope.launch(exceptionHandler) {
            combine(
                downloadRepository.getCompletedDownloads(),
                downloadRepository.getTotalDownloadedSize(),
            ) { downloads, totalSize ->
                downloads to totalSize
            }.catch { e ->
                _uiState.value = DownloadsUiState(
                    isLoading = false,
                    errorMessage = e.message ?: "Unknown error",
                )
            }.collect { (downloads, totalSize) ->
                try {
                    val mediaIds = downloads.map { it.mediaId }
                    val songs = if (mediaIds.isNotEmpty()) {
                        val songList = songRepository.getSongsByIds(mediaIds)
                        val songMap = songList.associateBy { it.mediaId }
                        mediaIds.mapNotNull { songMap[it] }
                    } else {
                        emptyList()
                    }
                    _uiState.value = DownloadsUiState(
                        isLoading = false,
                        songs = songs,
                        downloadCount = downloads.size,
                        totalSizeBytes = totalSize,
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    _uiState.value = DownloadsUiState(
                        isLoading = false,
                        errorMessage = e.message ?: "Unknown error",
                    )
                }
            }
        }
    }
}
