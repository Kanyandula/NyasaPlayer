package com.example.nyasaplayer.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.common.util.NetworkMonitor
import com.example.nyasaplayer.core.data.api.AuthRepository
import com.example.nyasaplayer.core.data.api.UserRepository
import com.example.nyasaplayer.core.playback.BasePlayerStateCollector
import com.example.nyasaplayer.core.playback.PlaybackStatePersistence
import com.example.nyasaplayer.core.playback.PlayerError
import com.example.nyasaplayer.core.playback.PlayerMode
import com.example.nyasaplayer.core.playback.PlayerUiState
import com.example.nyasaplayer.core.playback.toSong
import com.example.nyasaplayer.download.SongDownloadManager
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

private const val MobilePositionPollIntervalMs = 250L

@UnstableApi
@HiltViewModel
@Suppress("TooManyFunctions")
class PlayerViewModel @Inject constructor(
    private val controllerFuture: ListenableFuture<MediaController>,
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val persistence: PlaybackStatePersistence,
    private val networkMonitor: NetworkMonitor,
    val downloadManager: SongDownloadManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        PlayerUiState(isOffline = !networkMonitor.isOnline.value),
    )
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        _uiState.update {
            it.copy(
                error = PlayerError(
                    title = "Playback Error",
                    message = throwable.message ?: "An unexpected error occurred",
                ),
            )
        }
    }

    private var likeObserverJob: Job? = null

    private val userId: String? get() = authRepository.currentUser?.uid
    private val isOnline: Boolean get() = networkMonitor.isOnline.value

    private val stateCollector = object : BasePlayerStateCollector(
        mediaControllerFuture = controllerFuture,
        collectorScope = viewModelScope,
    ) {
        override val positionPollIntervalMs: Long = MobilePositionPollIntervalMs

        override fun onControllerConnected(controller: MediaController) {
            if (controller.isPlaying || controller.mediaItemCount > 0) {
                syncFromLivePlayer(controller)
            } else {
                restorePlaybackState()
            }
        }

        override fun onCurrentSongChanged(mediaItem: MediaItem) {
            val song = mediaItem.toSong()
            observeCurrentSongLikeState(song.mediaId)
        }

        override fun onPlaybackError(error: PlaybackException) {
            val isNetwork = error.cause is IOException
            _uiState.update {
                it.copy(
                    isPlaying = false,
                    error = PlayerError(
                        title = if (isNetwork) "Offline" else "Playback Error",
                        message = if (isNetwork) {
                            "This song isn't available offline"
                        } else {
                            error.message ?: "Playback error"
                        },
                    ),
                )
            }
        }

        override fun onControllerConnectionFailed() {
            _uiState.update {
                it.copy(
                    error = PlayerError(
                        title = "Player Error",
                        message = "Could not connect to playback service",
                        isPlaybackError = false,
                    ),
                )
            }
        }

        override fun onPlayerUnavailable() = reportPlayerUnavailable()
    }

    /**
     * Says that the player is gone, once, in the words the connection failure already uses.
     *
     * Only when nothing else is showing: the snackbar clears the error after displaying it, so
     * raising it per tap would re-trigger a message the user is already reading.
     */
    private fun reportPlayerUnavailable() {
        if (_uiState.value.error != null) return
        _uiState.update {
            it.copy(
                error = PlayerError(
                    title = "Player Error",
                    message = "Could not connect to playback service",
                    isPlaybackError = false,
                ),
            )
        }
    }

    init {
        stateCollector.connectController()
        observePlaybackSnapshot()
        observeNetworkState()
    }

    private fun observePlaybackSnapshot() {
        stateCollector.playbackState.onEach { snapshot ->
            _uiState.update {
                it.copy(
                    currentSong = snapshot.currentSong ?: it.currentSong,
                    isPlaying = snapshot.isPlaying,
                    currentPositionMs = snapshot.currentPositionMs,
                    durationMs = snapshot.durationMs,
                    isBuffering = snapshot.isBuffering,
                    hasPrevious = snapshot.hasPrevious,
                    hasNext = snapshot.hasNext,
                    repeatMode = snapshot.repeatMode,
                )
            }
            handleOfflineBuffering(snapshot.isBuffering)
        }.launchIn(viewModelScope)
    }

    private fun handleOfflineBuffering(isBuffering: Boolean) {
        if (isBuffering && !isOnline) {
            stateCollector.transport.pause()
            _uiState.update {
                it.copy(
                    isBuffering = false,
                    isPlaying = false,
                    error = PlayerError(
                        title = "Offline",
                        message = "Connection lost. Download songs for offline playback.",
                    ),
                )
            }
        }
    }

    // ── Playback Actions ──

    fun playSong(songs: List<Song>, song: Song) {
        val isDownloaded = downloadManager.getLocalFileUri(song.mediaId) != null
        if (!isOnline && !isDownloaded) {
            showOfflineError(song)
            return
        }
        val resolvedSongs = songs.map { resolveSongUri(it) }
        val resolvedSong = resolveSongUri(song)
        val startIndex = resolvedSongs.indexOfFirst { it.mediaId == song.mediaId }.coerceAtLeast(0)
        // Nothing is painted as playing unless the command reached a connected player (T11).
        if (!stateCollector.transport.setQueue(resolvedSongs, startIndex)) return
        _uiState.update {
            it.copy(
                playerMode = PlayerMode.Expanded,
                currentSong = resolvedSong,
                isPlaying = true,
                isShuffled = false,
            )
        }
        observeCurrentSongLikeState(song.mediaId)
        logRecentlyPlayedSafe(song.mediaId)
    }

    private fun resolveSongUri(song: Song): Song {
        val localUri = downloadManager.getLocalFileUri(song.mediaId) ?: return song
        return song.copy(audioUrl = localUri, songUrl = localUri)
    }

    fun shufflePlay(songs: List<Song>) {
        if (songs.isEmpty()) return
        val resolvedSongs = songs.map { resolveSongUri(it) }
        val hasPlayable = isOnline || resolvedSongs.zip(songs).any { (resolved, original) ->
            resolved.audioUrl != original.audioUrl
        }
        if (!hasPlayable) {
            showOfflineError(songs.first())
            return
        }
        if (!stateCollector.transport.shufflePlay(resolvedSongs)) return
        _uiState.update {
            it.copy(
                playerMode = PlayerMode.Expanded,
                currentSong = resolvedSongs.first(),
                isPlaying = true,
                isShuffled = true,
            )
        }
    }

    fun toggleShuffle() {
        if (stateCollector.transport.toggleShuffle()) {
            _uiState.update { it.copy(isShuffled = !it.isShuffled) }
        }
    }

    fun skipNext() {
        stateCollector.transport.skipNext()
    }

    fun skipPrevious() {
        stateCollector.transport.skipPrevious()
    }

    fun toggleRepeatMode() {
        stateCollector.transport.toggleRepeatMode()
    }

    fun togglePlayPause() {
        val transport = stateCollector.transport
        // Reads the live player, as this did before the transport existed: the snapshot's
        // isPlaying lags a listener callback behind it.
        val isPlaying = transport.isPlaying() ?: run {
            // The query is deliberately silent, so this is the one path that must speak for itself.
            reportPlayerUnavailable()
            return
        }
        if (isPlaying) {
            transport.pause()
            return
        }
        val currentMediaId = _uiState.value.currentSong?.mediaId
        val isDownloaded = currentMediaId != null &&
            downloadManager.getLocalFileUri(currentMediaId) != null
        if (!isOnline && !isDownloaded) {
            _uiState.update {
                it.copy(
                    error = PlayerError(
                        title = "Offline",
                        message = "Can't stream while offline. Download songs for offline playback.",
                    ),
                )
            }
            return
        }
        transport.play()
    }

    fun seekTo(positionMs: Long) {
        stateCollector.transport.seekTo(positionMs)
        _uiState.update { it.copy(currentPositionMs = positionMs) }
    }

    fun expand() {
        _uiState.update { it.copy(playerMode = PlayerMode.Expanded) }
    }

    fun collapse() {
        _uiState.update { it.copy(playerMode = PlayerMode.Mini) }
    }

    fun dismiss() {
        stateCollector.transport.stopAndClear()
        _uiState.update { PlayerUiState() }
    }

    // ── Custom Commands ──

    // ── Offline Handling ──

    private fun showOfflineError(song: Song) {
        _uiState.update {
            it.copy(
                playerMode = PlayerMode.Expanded,
                currentSong = song,
                isPlaying = false,
                isBuffering = false,
                currentPositionMs = 0L,
                durationMs = song.durationMs,
                error = PlayerError(
                    title = "Offline",
                    message = "This song isn't available offline",
                ),
            )
        }
    }

    private fun observeNetworkState() {
        networkMonitor.isOnline.onEach { online ->
            _uiState.update { it.copy(isOffline = !online) }
        }.launchIn(viewModelScope)
    }

    // ── Like / Unlike ──

    fun toggleLike() {
        val uid = userId ?: return
        val mediaId = _uiState.value.currentSong?.mediaId ?: return
        val wasLiked = _uiState.value.isCurrentSongLiked
        _uiState.update { it.copy(isCurrentSongLiked = !wasLiked) }
        viewModelScope.launch(exceptionHandler) {
            try {
                if (wasLiked) {
                    userRepository.unlikeSong(uid, mediaId)
                } else {
                    userRepository.likeSong(uid, mediaId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
            ) {
                _uiState.update {
                    it.copy(
                        isCurrentSongLiked = wasLiked,
                        error = PlayerError(
                            title = "Sync Error",
                            message = "Couldn't update like status",
                            isPlaybackError = false,
                        ),
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun logRecentlyPlayedSafe(mediaId: String) {
        userId?.let { uid ->
            viewModelScope.launch(exceptionHandler) {
                try {
                    userRepository.logRecentlyPlayed(uid, mediaId)
                } catch (e: CancellationException) {
                    throw e
                } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
                    // Silent fail — non-critical
                }
            }
        }
    }

    private fun observeCurrentSongLikeState(mediaId: String) {
        likeObserverJob?.cancel()
        val uid = userId ?: return
        likeObserverJob = viewModelScope.launch(exceptionHandler) {
            try {
                userRepository.isLiked(uid, mediaId).collect { liked ->
                    _uiState.update { it.copy(isCurrentSongLiked = liked) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
                // Silent fail — like state is non-critical
            }
        }
    }

    // ── Playback State Persistence ──

    private fun syncFromLivePlayer(controller: MediaController) {
        stateCollector.syncSnapshotFromPlayer(controller)
        _uiState.update { it.copy(playerMode = PlayerMode.Mini) }
        val song = controller.currentMediaItem?.toSong()
        if (song != null) {
            observeCurrentSongLikeState(song.mediaId)
        }
    }

    private fun restorePlaybackState() {
        viewModelScope.launch(exceptionHandler) {
            try {
                // The collector publishes the restored session; everything below it is mobile's.
                // A null result means nothing was restored — no saved session, a player that
                // filled up while the read was in flight, or a command the service refused — and
                // the mini player stays down rather than rising over a player that has nothing.
                val restored = stateCollector.restoreIfIdle { persistence.restore() }
                    ?: return@launch
                _uiState.update { it.copy(playerMode = PlayerMode.Mini) }
                observeCurrentSongLikeState(restored.song.mediaId)
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
            ) {
                _uiState.update {
                    it.copy(
                        error = PlayerError(
                            title = "Restore Error",
                            message = "Couldn't restore previous session",
                            isPlaybackError = false,
                        ),
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stateCollector.releaseController()
    }
}
