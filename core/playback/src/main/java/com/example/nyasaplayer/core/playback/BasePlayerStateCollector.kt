package com.example.nyasaplayer.core.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.example.nyasaplayer.core.common.models.Song
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
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutionException
import kotlin.coroutines.resume

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
                } catch (_: java.util.concurrent.CancellationException) {
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
                    currentQueueIndex = mc.currentMediaItemIndex,
                )
            }
            onCurrentSongChanged(mediaItem)
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            val mc = controller ?: return
            val queue = readQueue(mc)
            _playbackState.update {
                it.copy(
                    queue = queue,
                    queueSize = mc.mediaItemCount,
                    currentQueueIndex = mc.currentMediaItemIndex,
                )
            }
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
        val queue = readQueue(mc)
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
                queue = queue,
                currentQueueIndex = mc.currentMediaItemIndex,
            )
        }
    }

    private fun readQueue(mc: MediaController): List<Song> =
        (0 until mc.mediaItemCount).map { mc.getMediaItemAt(it).toSong() }

    // ── Restore ──

    /**
     * Restores the previous session, but only onto a player that is still empty.
     *
     * [restore] is a network read on both surfaces, so the player can be empty when this is called
     * and playing by the time it returns — the driver started something from the OEM template, or
     * tapped play in the app. `PlaybackService` applies a restore queue unconditionally, so sending
     * one then would replace what is playing and pause it. Empty *then* does not mean empty *now*.
     *
     * Returns the restored session only when the whole sequence succeeded: something to restore,
     * a still-idle player, a command the service acknowledged, and a published snapshot. A caller
     * holding a non-null result therefore knows the session is on screen, which is what its
     * follow-up work — a like observer, a mini player — should key off. Every failure is a silent
     * null: an unrestorable session is not something the driver can act on.
     */
    suspend fun restoreIfIdle(restore: suspend () -> RestoredPlayback?): RestoredPlayback? {
        val mc = controller ?: return null
        val restored = restore() ?: return null
        if (mc.mediaItemCount > 0) return null
        if (mc.sendRestoreState(restored).awaitResultCode() != SessionResult.RESULT_SUCCESS) {
            return null
        }
        applyRestored(restored)
        return restored
    }

    /**
     * Awaits a session command's result code, mapping every failure to [SessionError.ERROR_UNKNOWN].
     *
     * Deliberately value-returning rather than throwing: a command that fails is a restore that
     * does not happen, and turning it into an exception would put a crash path where the callers
     * have always had a silent no-op. Cancellation still propagates, through the coroutine.
     */
    private suspend fun ListenableFuture<SessionResult>.awaitResultCode(): Int =
        suspendCancellableCoroutine { continuation ->
            addListener(
                {
                    // The listener runs only once the future is done, so this does not block.
                    val code = try {
                        get().resultCode
                    } catch (_: ExecutionException) {
                        SessionError.ERROR_UNKNOWN
                    } catch (_: java.util.concurrent.CancellationException) {
                        SessionError.ERROR_UNKNOWN
                    }
                    if (continuation.isActive) continuation.resume(code)
                },
                MoreExecutors.directExecutor(),
            )
            continuation.invokeOnCancellation { cancel(false) }
        }

    /**
     * Publishes a session the service has just restored, paused and seeked but not playing.
     *
     * Every value comes from [restored] and none from the controller. A `SessionResult` says the
     * *session* applied the queue; it does not say the controller has caught up, so asking the
     * controller here — [hasNextTrack], say — would let a one-message-loop lag answer for an empty
     * player and show the driver nothing. The controller's own callbacks refine this moments later.
     *
     * Both surfaces call this, which is the point: a restored session looks the same on each.
     */
    fun applyRestored(restored: RestoredPlayback) {
        _playbackState.update {
            it.copy(
                currentSong = restored.song,
                isPlaying = false,
                currentPositionMs = restored.positionMs,
                // The position poller only runs while playing, so nothing else fills the
                // scrubber's total on a restored-and-paused session.
                durationMs = restored.song.durationMs,
                hasPrevious = restored.index > 0,
                hasNext = restored.index < restored.queue.lastIndex ||
                    restored.repeatMode == RepeatMode.All,
                repeatMode = restored.repeatMode,
                queue = restored.queue,
                queueSize = restored.queue.size,
                currentQueueIndex = restored.index,
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
