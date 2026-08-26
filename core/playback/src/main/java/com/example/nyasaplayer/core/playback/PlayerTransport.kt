package com.example.nyasaplayer.core.playback

import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand

/**
 * Every transport action both surfaces perform, in one place.
 *
 * Before this existed each ViewModel opened thirteen methods with `controller ?: return` and then
 * talked to the `MediaController` itself, which is how the two surfaces drifted apart on restore
 * (see D61) and how a dead controller turned every button into a silent no-op (T11).
 *
 * **What the `Boolean` means: the controller was reached.** It does *not* mean the operation had an
 * effect. [skipToQueueItem] still ignores an out-of-range index, [removeFromQueue] still ignores the
 * current item, [clearQueue] still ignores a queue of one, and [skipNext] at the end of a
 * non-repeating queue still does nothing — those are correct refusals and they stay silent. `false`
 * means there was no player to talk to at all, which is the only case a surface should report.
 *
 * Holds a supplier rather than a controller because the controller arrives asynchronously and can
 * go away again; it also makes this class constructible in a unit test with `{ null }`.
 */
class PlayerTransport(private val controller: () -> MediaController?) {

    /** Whether the player is playing, or `null` when there is no controller to ask. */
    fun isPlaying(): Boolean? = controller()?.isPlaying

    fun play(): Boolean = withController { it.play() }

    fun pause(): Boolean = withController { it.pause() }

    fun togglePlayPause(): Boolean = withController {
        if (it.isPlaying) it.pause() else it.play()
    }

    fun skipNext(): Boolean = withController {
        if (it.hasNextMediaItem()) {
            it.seekToNextMediaItem()
        } else if (it.repeatMode == Player.REPEAT_MODE_ALL && it.mediaItemCount > 0) {
            // hasNextTrack() answers true for repeat-all without checking the queue is non-empty,
            // so the count guard is what stops a wrap onto nothing.
            it.seekTo(0, 0L)
        }
    }

    fun skipPrevious(): Boolean = withController {
        if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long): Boolean = withController { it.seekTo(positionMs) }

    fun toggleRepeatMode(): Boolean = withController {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    /**
     * Asks the service to shuffle. Each surface keeps its own optimistic flag — mobile in
     * `PlayerUiState`, the car in `PlaybackSnapshot` — until a ticket decides where `isShuffled`
     * should live.
     */
    fun toggleShuffle(): Boolean = withController {
        it.sendCustomCommand(
            SessionCommand(PlaybackCommands.CMD_TOGGLE_SHUFFLE, Bundle.EMPTY),
            Bundle.EMPTY,
        )
    }

    fun skipToQueueItem(index: Int): Boolean = withController {
        if (index in 0 until it.mediaItemCount) {
            it.seekTo(index, 0L)
            it.play()
        }
    }

    fun removeFromQueue(index: Int): Boolean = withController {
        if (index in 0 until it.mediaItemCount && index != it.currentMediaItemIndex) {
            it.removeMediaItem(index)
        }
    }

    /** Empties the queue around the current item, which stays. */
    fun clearQueue(): Boolean = withController {
        val currentIndex = it.currentMediaItemIndex
        val count = it.mediaItemCount
        if (count > 1) {
            // Remove tail first so currentIndex stays valid, then head.
            if (currentIndex < count - 1) it.removeMediaItems(currentIndex + 1, count)
            if (currentIndex > 0) it.removeMediaItems(0, currentIndex)
        }
    }

    /** Stops playback and empties the queue — mobile's dismiss. */
    fun stopAndClear(): Boolean = withController {
        it.stop()
        it.clearMediaItems()
    }

    private inline fun withController(action: (MediaController) -> Unit): Boolean {
        val mc = controller() ?: return false
        action(mc)
        return true
    }
}
