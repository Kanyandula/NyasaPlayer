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
import com.example.nyasaplayer.core.playback.ControllerConnection
import com.example.nyasaplayer.core.playback.PlaybackSnapshot
import com.example.nyasaplayer.core.playback.PlaybackStatePersistence
import com.example.nyasaplayer.core.playback.PlayerError
import com.example.nyasaplayer.core.playback.isPlayableNow
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
private const val NoConnectionTitle = "No Connection"
private const val NoConnectionMessage = "Check your vehicle's internet connection"

/**
 * The offline error, one shape for the slow path and the fast one (A8). `isPlaybackError = false`
 * selects the overlay's Wi-Fi-off icon. Retry only when the failing thing is the current item —
 * see [PlayerError.isRetryable].
 */
private fun noConnectionError(isRetryable: Boolean) = PlayerError(
    title = NoConnectionTitle,
    message = NoConnectionMessage,
    isPlaybackError = false,
    isRetryable = isRetryable,
)

@UnstableApi
@HiltViewModel
@Suppress("TooManyFunctions")
class AutomotivePlayerViewModel @Inject constructor(
    connection: ControllerConnection,
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
    private val isOnline: Boolean get() = networkMonitor.isOnline.value

    private val stateCollector = object : BasePlayerStateCollector(
        connection = connection,
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
                    // The current item is what failed, so Retry re-attempting it via
                    // togglePlayPause() acts on the thing the error is actually about.
                    error = if (isNetwork) {
                        noConnectionError(isRetryable = true)
                    } else {
                        PlayerError(
                            title = "Playback Error",
                            message = error.message ?: "Playback error",
                            isRetryable = true,
                        )
                    },
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
         * A command found no connected player **and rebuilding the connection failed** (T14). A
         * controller that can be replaced is replaced silently; this is the last resort.
         *
         * `CarErrorOverlay` renders it above everything and blocks the controls underneath, so
         * repeated taps cannot stack it.
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
            stopIfStreamingOffline(snapshot)
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
            if (!online) stopIfStreamingOffline(_uiState.value.playback)
        }.catch { /* Network state flow is internal — errors are non-fatal */ }
            .launchIn(viewModelScope)
    }

    /**
     * Buffering, trying to play, offline, and not a local file: the stream cannot recover, so stop
     * it now rather than after ExoPlayer's timeout (A8). `playWhenReady` is what keeps a
     * restored-but-paused session — which buffers too — from raising anything (T3, D-T3.5).
     */
    private fun stopIfStreamingOffline(snapshot: PlaybackSnapshot) {
        if (isOnline || !snapshot.isBuffering || !snapshot.playWhenReady) return
        if (snapshot.currentSong?.isPlayableNow(isOnline = false) == true) return
        stateCollector.transport.pause()
        _uiState.update { it.copy(error = noConnectionError(isRetryable = true)) }
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
        val transport = stateCollector.transport
        // Reads the live player: the snapshot's isPlaying lags a listener callback behind it.
        val isPlaying = transport.isPlaying() ?: run {
            // Silent query, no rebuild. Ask for the toggle and let it fail, so play reconnects like
            // every other control (T14).
            transport.togglePlayPause()
            return
        }
        if (isPlaying) {
            transport.pause()
            return
        }
        val current = _uiState.value.playback.currentSong
        if (current != null && !current.isPlayableNow(isOnline)) {
            _uiState.update { it.copy(error = noConnectionError(isRetryable = true)) }
            return
        }
        transport.play()
    }

    fun skipNext() {
        stateCollector.transport.skipNext()
    }

    /**
     * The overlay's Skip next. An errored player is idle, so the skip is followed by a play, which
     * Media3 turns into prepare-then-play on an idle player (`Util.handlePlayButtonAction`).
     */
    fun skipNextAfterError() {
        clearError()
        val transport = stateCollector.transport
        if (transport.skipNext()) transport.play()
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

    /** True only when the queue reached a connected player — the caller opens the full player on it. */
    fun playSong(songs: List<Song>, song: Song): Boolean {
        // The tapped song is what starts. It was never queued, so Retry would act on someone else's
        // queue — hence no Retry (PlayerError.isRetryable).
        if (!song.isPlayableNow(isOnline)) {
            _uiState.update { it.copy(error = noConnectionError(isRetryable = false)) }
            return false
        }
        val startIndex = songs.indexOfFirst { it.mediaId == song.mediaId }.coerceAtLeast(0)
        // Nothing is painted as playing unless the command reached a connected player (T11).
        if (!stateCollector.transport.setQueue(songs, startIndex)) return false
        stateCollector.updateSnapshot {
            it.copy(
                currentSong = song,
                isPlaying = true,
                isShuffled = false,
            )
        }
        return true
    }

    /** True only when the shuffle reached a connected player. */
    fun shufflePlay(songs: List<Song>): Boolean {
        if (songs.isEmpty()) return false
        if (songs.none { it.isPlayableNow(isOnline) }) {
            _uiState.update { it.copy(error = noConnectionError(isRetryable = false)) }
            return false
        }
        if (!stateCollector.transport.shufflePlay(songs)) return false
        stateCollector.updateSnapshot {
            it.copy(
                currentSong = songs.first(),
                isPlaying = true,
                isShuffled = true,
            )
        }
        return true
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
