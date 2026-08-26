package com.example.nyasaplayer.auto.viewmodel

import android.util.Log
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
import com.example.nyasaplayer.core.playback.PlaybackSnapshot
import com.example.nyasaplayer.core.playback.PlaybackStatePersistence
import com.example.nyasaplayer.core.playback.PlayerError
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

private const val AutoPositionPollIntervalMs = 500L
private const val TAG = "AutoPlayerVM"

@UnstableApi
@HiltViewModel
@Suppress("TooManyFunctions")
class AutomotivePlayerViewModel @Inject constructor(
    controllerFuture: ListenableFuture<MediaController>,
    private val uxHandler: CarUxRestrictionsHandler,
    private val persistence: PlaybackStatePersistence,
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AutomotiveUiState())
    val uiState: StateFlow<AutomotiveUiState> = _uiState.asStateFlow()

    private val userId get() = authRepository.currentUserId
    private var likeObserverJob: Job? = null

    private val stateCollector = object : BasePlayerStateCollector(
        mediaControllerFuture = controllerFuture,
        collectorScope = viewModelScope,
    ) {
        override val positionPollIntervalMs: Long = AutoPositionPollIntervalMs

        override fun onControllerConnected(controller: MediaController) {
            if (controller.isPlaying || controller.mediaItemCount > 0) {
                syncSnapshotFromPlayer(controller)
            } else {
                restorePreviousSession()
            }
        }

        override fun onCurrentSongChanged(mediaItem: MediaItem) {
            mediaItem.mediaId.takeIf { it.isNotEmpty() }?.let { observeCurrentSongLikeState(it) }
        }

        override fun onPlaybackError(error: PlaybackException) {
            val isNetwork = error.cause is IOException
            _uiState.update {
                it.copy(
                    error = PlayerError(
                        title = if (isNetwork) "No Connection" else "Playback Error",
                        message = if (isNetwork) {
                            "Check your vehicle's internet connection"
                        } else {
                            error.message ?: "Playback error"
                        },
                        // The current item is what failed, so Retry re-attempting it via
                        // togglePlayPause() acts on the thing the error is actually about.
                        isRetryable = true,
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

        /**
         * A command found no connected player. `CarErrorOverlay` renders this above everything and
         * blocks the controls underneath, so repeated taps cannot stack it.
         */
        override fun onPlayerUnavailable() {
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
    }

    init {
        stateCollector.connectController()
        uxHandler.connect()
        observePlaybackSnapshot()
        observeUxRestrictions()
        observeNetworkState()
    }

    private fun observePlaybackSnapshot() {
        stateCollector.playbackState.onEach { snapshot ->
            _uiState.update { it.copy(playback = snapshot) }
        }.catch { /* Snapshot flow is internal — errors are non-fatal */ }
            .launchIn(viewModelScope)
    }

    private fun observeUxRestrictions() {
        uxHandler.restrictions.onEach { restrictions ->
            _uiState.update { it.copy(restrictions = restrictions) }
        }.catch { /* Restrictions flow is internal — errors are non-fatal */ }
            .launchIn(viewModelScope)
    }

    private fun observeNetworkState() {
        networkMonitor.isOnline.onEach { online ->
            _uiState.update { it.copy(isOffline = !online) }
        }.catch { /* Network state flow is internal — errors are non-fatal */ }
            .launchIn(viewModelScope)
    }

    // ── Playback State Restore ──

    /**
     * Brings back the session the driver left, after the process was killed with the car's player
     * empty. Paused, never playing: the service applies the queue with `playWhenReady = false`.
     *
     * A failed or absent restore does nothing at all — the player stays empty, which is what it
     * would have been anyway. An error overlay here would put a dialog in front of a driver for
     * something they cannot act on (spec D-T3.5).
     */
    private fun restorePreviousSession() {
        viewModelScope.launch {
            val restored = stateCollector.restoreIfIdle { persistence.restore() } ?: return@launch
            // A non-null result means the session is on screen, so the heart can follow it.
            observeCurrentSongLikeState(restored.song.mediaId)
        }
    }

    // ── Playback Controls ──

    fun togglePlayPause() {
        stateCollector.transport.togglePlayPause()
    }

    fun skipNext() {
        stateCollector.transport.skipNext()
    }

    fun skipPrevious() {
        stateCollector.transport.skipPrevious()
    }

    fun seekTo(positionMs: Long) {
        stateCollector.transport.seekTo(positionMs)
    }

    fun toggleRepeatMode() {
        stateCollector.transport.toggleRepeatMode()
    }

    fun toggleShuffle() {
        if (stateCollector.transport.toggleShuffle()) {
            stateCollector.updateSnapshot { it.copy(isShuffled = !it.isShuffled) }
        }
    }

    // ── Play Actions ──

    fun playSong(songs: List<Song>, song: Song) {
        val startIndex = songs.indexOfFirst { it.mediaId == song.mediaId }.coerceAtLeast(0)
        // Nothing is painted as playing unless the command reached a connected player (T11).
        if (!stateCollector.transport.setQueue(songs, startIndex)) return
        stateCollector.updateSnapshot {
            it.copy(
                currentSong = song,
                isPlaying = true,
                isShuffled = false,
            )
        }
    }

    fun shufflePlay(songs: List<Song>) {
        if (songs.isEmpty()) return
        if (!stateCollector.transport.shufflePlay(songs)) return
        stateCollector.updateSnapshot {
            it.copy(
                currentSong = songs.first(),
                isPlaying = true,
                isShuffled = true,
            )
        }
    }

    // ── Queue Management ──

    fun skipToQueueItem(index: Int) {
        stateCollector.transport.queue.skipToQueueItem(index)
    }

    fun removeFromQueue(index: Int) {
        stateCollector.transport.queue.removeFromQueue(index)
    }

    fun clearQueue() {
        stateCollector.transport.queue.clearQueue()
    }

    // ── Like / Unlike ──

    @Suppress("TooGenericExceptionCaught")
    fun toggleLike() {
        val uid = userId ?: return
        val mediaId = _uiState.value.playback.currentSong?.mediaId ?: return
        val wasLiked = _uiState.value.isCurrentSongLiked
        _uiState.update { it.copy(isCurrentSongLiked = !wasLiked) }
        viewModelScope.launch {
            try {
                if (wasLiked) {
                    userRepository.unlikeSong(uid, mediaId)
                } else {
                    userRepository.likeSong(uid, mediaId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Couldn't update like status", e)
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

    @Suppress("TooGenericExceptionCaught")
    private fun observeCurrentSongLikeState(mediaId: String) {
        likeObserverJob?.cancel()
        val uid = userId ?: return
        likeObserverJob = viewModelScope.launch {
            try {
                userRepository.isLiked(uid, mediaId).collect { liked ->
                    _uiState.update { it.copy(isCurrentSongLiked = liked) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error observing like state", e)
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Surfaces the case where a genre's tap resolves to zero actual songs — whether the genre
     * is genuinely empty or its `songIds` just disagree with the `Song.genreIds` reverse index
     * tracks are actually resolved by. Routed through the same error overlay as playback errors
     * rather than a new channel: this is a failure to start playback, same as any other.
     */
    fun reportEmptyGenrePlayback() {
        _uiState.update {
            it.copy(
                error = PlayerError(
                    title = "Nothing to Play",
                    message = "This genre doesn't have any songs available yet.",
                ),
            )
        }
    }

    /**
     * Surfaces a failed like/unlike write. Non-retryable: Retry would act on the transport, not on
     * the write that failed — see the PlayerError.isRetryable KDoc.
     */
    fun reportUnlikeFailed() {
        _uiState.update {
            it.copy(
                error = PlayerError(
                    title = "Couldn't Save",
                    message = "Your change to this song wasn't saved. Check your connection.",
                    isPlaybackError = false,
                ),
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        stateCollector.releaseController()
        uxHandler.disconnect()
    }
}

data class AutomotiveUiState(
    val playback: PlaybackSnapshot = PlaybackSnapshot(),
    val restrictions: UxRestrictionState = UxRestrictionState(),
    val error: PlayerError? = null,
    val isCurrentSongLiked: Boolean = false,
    val isOffline: Boolean = false,
)
