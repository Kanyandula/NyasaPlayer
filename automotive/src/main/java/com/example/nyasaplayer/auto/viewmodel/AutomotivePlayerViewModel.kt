package com.example.nyasaplayer.auto.viewmodel

import android.os.Bundle
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.common.util.NetworkMonitor
import com.example.nyasaplayer.core.data.api.AuthRepository
import com.example.nyasaplayer.core.data.api.UserRepository
import com.example.nyasaplayer.core.playback.BasePlayerStateCollector
import com.example.nyasaplayer.core.playback.PlaybackCommands
import com.example.nyasaplayer.core.playback.PlaybackSnapshot
import com.example.nyasaplayer.core.playback.PlayerError
import com.example.nyasaplayer.core.playback.toBundle
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
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AutomotiveUiState())
    val uiState: StateFlow<AutomotiveUiState> = _uiState.asStateFlow()

    private val userId get() = authRepository.currentUser?.uid
    private var likeObserverJob: Job? = null

    private val stateCollector = object : BasePlayerStateCollector(
        mediaControllerFuture = controllerFuture,
        collectorScope = viewModelScope,
    ) {
        override val positionPollIntervalMs: Long = AutoPositionPollIntervalMs

        override fun onControllerConnected(controller: MediaController) {
            if (controller.isPlaying || controller.mediaItemCount > 0) {
                syncSnapshotFromPlayer(controller)
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

    // ── Playback Controls ──

    fun togglePlayPause() {
        val controller = stateCollector.controller ?: return
        if (controller.isPlaying) controller.pause() else controller.play()
    }

    fun skipNext() {
        val controller = stateCollector.controller ?: return
        if (controller.hasNextMediaItem()) {
            controller.seekToNextMediaItem()
        } else if (controller.repeatMode == Player.REPEAT_MODE_ALL && controller.mediaItemCount > 0) {
            controller.seekTo(0, 0L)
        }
    }

    fun skipPrevious() {
        val controller = stateCollector.controller ?: return
        if (controller.hasPreviousMediaItem()) {
            controller.seekToPreviousMediaItem()
        }
    }

    fun seekTo(positionMs: Long) {
        stateCollector.controller?.seekTo(positionMs)
    }

    fun toggleRepeatMode() {
        val controller = stateCollector.controller ?: return
        controller.repeatMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun toggleShuffle() {
        val controller = stateCollector.controller ?: return
        stateCollector.updateSnapshot { it.copy(isShuffled = !it.isShuffled) }
        controller.sendCustomCommand(
            SessionCommand(PlaybackCommands.CMD_TOGGLE_SHUFFLE, Bundle.EMPTY),
            Bundle.EMPTY,
        )
    }

    // ── Play Actions ──

    fun playSong(songs: List<Song>, song: Song) {
        val startIndex = songs.indexOfFirst { it.mediaId == song.mediaId }.coerceAtLeast(0)
        sendSetQueueCommand(songs, startIndex)
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
        sendShufflePlayCommand(songs)
        stateCollector.updateSnapshot {
            it.copy(
                isPlaying = true,
                isShuffled = true,
            )
        }
    }

    private fun sendSetQueueCommand(songs: List<Song>, startIndex: Int) {
        val controller = stateCollector.controller ?: return
        val args = Bundle().apply {
            putBundle(PlaybackCommands.KEY_SONGS, songs.toBundle())
            putInt(PlaybackCommands.KEY_START_INDEX, startIndex)
        }
        controller.sendCustomCommand(
            SessionCommand(PlaybackCommands.CMD_SET_QUEUE, Bundle.EMPTY),
            args,
        )
    }

    private fun sendShufflePlayCommand(songs: List<Song>) {
        val controller = stateCollector.controller ?: return
        val args = Bundle().apply {
            putBundle(PlaybackCommands.KEY_SONGS, songs.toBundle())
        }
        controller.sendCustomCommand(
            SessionCommand(PlaybackCommands.CMD_SHUFFLE_PLAY, Bundle.EMPTY),
            args,
        )
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
