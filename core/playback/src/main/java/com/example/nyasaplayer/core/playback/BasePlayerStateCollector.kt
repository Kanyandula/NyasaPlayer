package com.example.nyasaplayer.core.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutionException

abstract class BasePlayerStateCollector(
    private val mediaControllerFuture: ListenableFuture<MediaController>,
    private val collectorScope: CoroutineScope,
) {
    private val _playbackState = MutableStateFlow(PlaybackSnapshot())
    val playbackState: StateFlow<PlaybackSnapshot> = _playbackState.asStateFlow()

    @Volatile
    var controller: MediaController? = null
        private set

    protected abstract val positionPollIntervalMs: Long

    protected open fun onControllerConnected(controller: MediaController) {}
    protected open fun onCurrentSongChanged(mediaItem: MediaItem) {}
    protected open fun onPlaybackError(error: PlaybackException) {}
    protected open fun onControllerConnectionFailed() {}

    fun connectController() {
        mediaControllerFuture.addListener(
            {
                try {
                    val mc = mediaControllerFuture.get()
                    controller = mc
                    mc.addListener(controllerListener)
                    startPositionPolling()
                    onControllerConnected(mc)
                } catch (_: ExecutionException) {
                    onControllerConnectionFailed()
                }
            },
            MoreExecutors.directExecutor(),
        )
    }

    fun releaseController() {
        MediaController.releaseFuture(mediaControllerFuture)
    }

    // ── Controller Listener ──

    private val controllerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (mediaItem == null) return
            val song = mediaItem.toSong()
            val mc = controller ?: return
            _playbackState.update {
                it.copy(
                    currentSong = song,
                    durationMs = song.durationMs,
                    hasPrevious = mc.hasPreviousMediaItem(),
                    hasNext = hasNextTrack(it.repeatMode),
                    queueSize = mc.mediaItemCount,
                )
            }
            onCurrentSongChanged(mediaItem)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playbackState.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val mc = controller ?: return
            _playbackState.update {
                it.copy(
                    isBuffering = playbackState == Player.STATE_BUFFERING,
                    isPlaying = mc.isPlaying,
                )
            }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            val mode = repeatMode.toAppRepeatMode()
            _playbackState.update {
                it.copy(
                    repeatMode = mode,
                    hasNext = hasNextTrack(mode),
                )
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _playbackState.update {
                it.copy(
                    isPlaying = false,
                    isBuffering = false,
                )
            }
            onPlaybackError(error)
        }
    }

    // ── Position Polling ──

    private fun startPositionPolling() {
        collectorScope.launch {
            while (isActive) {
                val mc = controller
                if (mc != null && mc.isPlaying) {
                    _playbackState.update {
                        it.copy(
                            currentPositionMs = mc.currentPosition,
                            durationMs = mc.duration.coerceAtLeast(0L),
                        )
                    }
                }
                delay(positionPollIntervalMs)
            }
        }
    }

    // ── Helpers ──

    fun hasNextTrack(repeatMode: RepeatMode): Boolean {
        val mc = controller ?: return false
        return mc.hasNextMediaItem() || repeatMode == RepeatMode.All
    }

    fun syncSnapshotFromPlayer(mc: MediaController) {
        val mediaItem = mc.currentMediaItem ?: return
        val song = mediaItem.toSong()
        _playbackState.update {
            it.copy(
                currentSong = song,
                isPlaying = mc.isPlaying,
                currentPositionMs = mc.currentPosition,
                durationMs = mc.duration.coerceAtLeast(0L),
                hasPrevious = mc.hasPreviousMediaItem(),
                hasNext = hasNextTrack(mc.repeatMode.toAppRepeatMode()),
                repeatMode = mc.repeatMode.toAppRepeatMode(),
                queueSize = mc.mediaItemCount,
            )
        }
    }

    fun updateSnapshot(transform: (PlaybackSnapshot) -> PlaybackSnapshot) {
        _playbackState.update(transform)
    }
}

fun Int.toAppRepeatMode(): RepeatMode = when (this) {
    Player.REPEAT_MODE_ALL -> RepeatMode.All
    Player.REPEAT_MODE_ONE -> RepeatMode.One
    else -> RepeatMode.Off
}
