package com.example.nyasaplayer.auto.viewmodel

import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import com.example.nyasaplayer.core.playback.BasePlayerStateCollector
import com.example.nyasaplayer.core.playback.PlaybackCommands
import com.example.nyasaplayer.core.playback.PlaybackSnapshot
import com.example.nyasaplayer.core.playback.PlayerError
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import java.io.IOException
import javax.inject.Inject

private const val AutoPositionPollIntervalMs = 500L

@UnstableApi
@HiltViewModel
class AutomotivePlayerViewModel @Inject constructor(
    controllerFuture: ListenableFuture<MediaController>,
    private val uxHandler: CarUxRestrictionsHandler,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AutomotiveUiState())
    val uiState: StateFlow<AutomotiveUiState> = _uiState.asStateFlow()

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
            // No like-state observation on automotive
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
)
