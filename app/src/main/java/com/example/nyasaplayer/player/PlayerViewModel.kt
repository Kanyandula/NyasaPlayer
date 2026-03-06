package com.example.nyasaplayer.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.common.util.NetworkMonitor
import com.example.nyasaplayer.core.data.api.AuthRepository
import com.example.nyasaplayer.core.data.api.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

private const val PositionUpdateIntervalMs = 250L

@UnstableApi
@HiltViewModel
@Suppress("TooManyFunctions") // ViewModel centralises all playback actions
class PlayerViewModel @Inject constructor(
    private val playerManager: PlayerManager,
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val queueManager: PlaybackQueueManager,
    private val persistence: PlaybackStatePersistence,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState(isOffline = !networkMonitor.isOnline.value))
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

    init {
        startObserving()
        observeNetworkState()
    }

    fun playSong(songs: List<Song>, song: Song) {
        val resolved = queueManager.setQueue(songs, song)
        if (!isOnline) {
            showOfflineError(resolved)
            return
        }
        playerManager.play(resolved)
        _uiState.update {
            it.copy(
                playerMode = PlayerMode.Expanded,
                currentSong = resolved,
                isPlaying = true,
                hasPrevious = queueManager.hasPrevious,
                hasNext = queueManager.hasNext(_uiState.value.repeatMode),
                isShuffled = false,
            )
        }
        observeCurrentSongLikeState(resolved.mediaId)
        logRecentlyPlayedSafe(resolved.mediaId)
    }

    fun shufflePlay(songs: List<Song>) {
        if (songs.isEmpty()) return
        val first = queueManager.setQueueShuffled(songs) ?: return
        if (!isOnline) {
            showOfflineError(first)
            return
        }
        playerManager.play(first)
        _uiState.update {
            it.copy(
                playerMode = PlayerMode.Expanded,
                currentSong = first,
                isPlaying = true,
                hasPrevious = queueManager.hasPrevious,
                hasNext = queueManager.hasNext(_uiState.value.repeatMode),
                isShuffled = true,
            )
        }
        observeCurrentSongLikeState(first.mediaId)
        logRecentlyPlayedSafe(first.mediaId)
    }

    fun toggleShuffle() {
        queueManager.toggleShuffle()
        _uiState.update {
            it.copy(
                isShuffled = queueManager.isShuffled,
                hasPrevious = queueManager.hasPrevious,
                hasNext = queueManager.hasNext(_uiState.value.repeatMode),
            )
        }
    }

    fun skipNext() {
        val song = queueManager.skipNext(_uiState.value.repeatMode) ?: return
        playSongAtCurrentIndex(song)
    }

    fun skipPrevious() {
        val song = queueManager.skipPrevious() ?: return
        playSongAtCurrentIndex(song)
    }

    private fun playSongAtCurrentIndex(song: Song) {
        if (!isOnline) {
            showOfflineError(song)
            return
        }
        playerManager.play(song)
        _uiState.update {
            it.copy(
                currentSong = song,
                isPlaying = true,
                currentPositionMs = 0L,
                durationMs = song.durationMs,
                hasPrevious = queueManager.hasPrevious,
                hasNext = queueManager.hasNext(_uiState.value.repeatMode),
            )
        }
        observeCurrentSongLikeState(song.mediaId)
        logRecentlyPlayedSafe(song.mediaId)
    }

    fun toggleRepeatMode() {
        val newMode = when (_uiState.value.repeatMode) {
            RepeatMode.Off -> RepeatMode.All
            RepeatMode.All -> RepeatMode.One
            RepeatMode.One -> RepeatMode.Off
        }
        playerManager.setRepeatMode(newMode)
        _uiState.update {
            it.copy(
                repeatMode = newMode,
                hasNext = queueManager.hasNext(newMode),
            )
        }
    }

    fun togglePlayPause() {
        if (playerManager.isPlaying) {
            playerManager.pause()
            _uiState.update { it.copy(isPlaying = false) }
            persistence.save(
                viewModelScope,
                _uiState.value.currentSong ?: return,
                playerManager.currentPosition,
                queueManager.queueSongIds(),
                queueManager.currentIndex,
                _uiState.value.repeatMode,
            )
        } else {
            if (!isOnline) {
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
            playerManager.resume()
            _uiState.update { it.copy(isPlaying = true) }
        }
    }

    fun seekTo(positionMs: Long) {
        playerManager.seekTo(positionMs)
        _uiState.update { it.copy(currentPositionMs = positionMs) }
    }

    fun expand() {
        _uiState.update { it.copy(playerMode = PlayerMode.Expanded) }
    }

    fun collapse() {
        _uiState.update { it.copy(playerMode = PlayerMode.Mini) }
    }

    fun dismiss() {
        playerManager.stop()
        _uiState.update { PlayerUiState() }
    }

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
        // Optimistic UI update to prevent double-tap flicker
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

    fun restorePlaybackState() {
        viewModelScope.launch(exceptionHandler) {
            try {
                val restored = persistence.restore() ?: return@launch

                queueManager.restoreQueue(restored.queue, restored.index)
                playerManager.setRepeatMode(restored.repeatMode)
                playerManager.prepareOnly(restored.song)
                playerManager.seekTo(restored.positionMs)

                _uiState.update {
                    it.copy(
                        playerMode = PlayerMode.Mini,
                        currentSong = restored.song,
                        isPlaying = false,
                        currentPositionMs = restored.positionMs,
                        hasPrevious = queueManager.hasPrevious,
                        hasNext = queueManager.hasNext(restored.repeatMode),
                        repeatMode = restored.repeatMode,
                    )
                }
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

    private fun startObserving() {
        playerManager.player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_BUFFERING && !isOnline) {
                    playerManager.pause()
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
                    return
                }
                _uiState.update {
                    it.copy(
                        isBuffering = playbackState == Player.STATE_BUFFERING,
                        isPlaying = playerManager.isPlaying,
                    )
                }
                if (playbackState == Player.STATE_ENDED) {
                    val repeatMode = _uiState.value.repeatMode
                    if (repeatMode != RepeatMode.One) {
                        val nextSong = queueManager.skipNext(repeatMode)
                        if (nextSong != null) {
                            playSongAtCurrentIndex(nextSong)
                        } else {
                            _uiState.update { it.copy(isPlaying = false) }
                        }
                    }
                    // RepeatMode.One is handled natively by ExoPlayer's REPEAT_MODE_ONE
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.update { it.copy(isPlaying = isPlaying) }
            }

            override fun onPlayerError(error: PlaybackException) {
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
        })

        viewModelScope.launch(exceptionHandler) {
            while (true) {
                delay(PositionUpdateIntervalMs)
                if (playerManager.isPlaying || _uiState.value.playerMode != PlayerMode.Hidden) {
                    _uiState.update {
                        it.copy(
                            currentPositionMs = playerManager.currentPosition,
                            durationMs = playerManager.duration,
                        )
                    }
                    val currentSong = _uiState.value.currentSong
                    if (playerManager.isPlaying && currentSong != null) {
                        persistence.saveIfThrottleElapsed(
                            viewModelScope,
                            currentSong,
                            playerManager.currentPosition,
                            queueManager.queueSongIds(),
                            queueManager.currentIndex,
                            _uiState.value.repeatMode,
                        )
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        persistence.saveFinal(
            _uiState.value.currentSong,
            playerManager.currentPosition,
            queueManager.queueSongIds(),
            queueManager.currentIndex,
            _uiState.value.repeatMode,
        )
    }
}
