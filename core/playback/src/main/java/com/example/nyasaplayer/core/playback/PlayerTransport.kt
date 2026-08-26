package com.example.nyasaplayer.core.playback

import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import com.example.nyasaplayer.core.common.models.Song

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
 * **What available means: non-null and connected.** The collector assigns its controller once and
 * never clears it, so after a session dies the field still holds a `MediaController` — asking only
 * whether it is null would sail straight into a dead session and report success (T11).
 *
 * `true` does not promise the command reached the service. The session can vanish between the check
 * and the call; it means the controller was connected when the transport attempted it.
 *
 * Holds a supplier rather than a controller because the controller arrives asynchronously and can
 * go away again; it also makes this class constructible in a unit test with `{ null }`.
 *
 * @param onUnavailable raised once per *command* that found no connected controller. Queries do not
 * raise it: a user-facing error belongs to a user action, not to reading state.
 */
class PlayerTransport(
    private val controller: () -> MediaController?,
    private val onUnavailable: () -> Unit = {},
) {

    /**
     * Whether the player is playing, or `null` when no connected controller can be asked.
     *
     * Deliberately silent — `null` is the caller's cue to report, and mobile's `togglePlayPause`
     * does exactly that. Reporting here as well would raise two errors for one tap.
     */
    fun isPlaying(): Boolean? = controller().connectedOrNull()?.isPlaying

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

    /** Queue mutations, split out because the parent class sits on detekt's function ceiling. */
    val queue: QueueTransport = QueueTransport(controller, onUnavailable)

    /** Stops playback and empties the queue — mobile's dismiss. */
    fun stopAndClear(): Boolean = withController {
        it.stop()
        it.clearMediaItems()
    }

    /** Queue-setting entry points, behind the same availability check as the transport controls. */
    fun setQueue(songs: List<Song>, startIndex: Int): Boolean = withController {
        it.sendSetQueue(songs, startIndex)
    }

    fun shufflePlay(songs: List<Song>): Boolean = withController { it.sendShufflePlay(songs) }

    private inline fun withController(action: (MediaController) -> Unit): Boolean =
        controller().dispatch(onUnavailable, action)
}

/**
 * The availability predicate, in one place: a controller is usable only while it is connected.
 *
 * File-level rather than a member because `PlayerTransport` sits exactly on detekt's 16-function
 * class threshold, and a suppression is not how this project answers that (D23).
 */
/**
 * The queue mutations, over the same availability rule and the same report as its parent.
 *
 * Its own class only because `PlayerTransport` reached detekt's 16-function class limit; the
 * grouping is at least honest — these three are the operations that change what is queued rather
 * than what is playing.
 */
class QueueTransport(
    private val controller: () -> MediaController?,
    private val onUnavailable: () -> Unit = {},
) {

    fun skipToQueueItem(index: Int): Boolean = controller().dispatch(onUnavailable) {
        if (index in 0 until it.mediaItemCount) {
            it.seekTo(index, 0L)
            it.play()
        }
    }

    fun removeFromQueue(index: Int): Boolean = controller().dispatch(onUnavailable) {
        if (index in 0 until it.mediaItemCount && index != it.currentMediaItemIndex) {
            it.removeMediaItem(index)
        }
    }

    /** Empties the queue around the current item, which stays. */
    fun clearQueue(): Boolean = controller().dispatch(onUnavailable) {
        val currentIndex = it.currentMediaItemIndex
        val count = it.mediaItemCount
        if (count > 1) {
            // Remove tail first so currentIndex stays valid, then head.
            if (currentIndex < count - 1) it.removeMediaItems(currentIndex + 1, count)
            if (currentIndex > 0) it.removeMediaItems(0, currentIndex)
        }
    }
}

private fun MediaController?.connectedOrNull(): MediaController? = this?.takeIf { it.isConnected }

/**
 * Runs [action] against this controller if it is connected; otherwise reports and does nothing.
 *
 * File-level for the same reason as [connectedOrNull] — the class is at the function ceiling. The
 * next operation added to `PlayerTransport` will not fit, and that is the moment to split the queue
 * operations out rather than to suppress the rule.
 */
private inline fun MediaController?.dispatch(
    onUnavailable: () -> Unit,
    action: (MediaController) -> Unit,
): Boolean {
    val mc = connectedOrNull()
    if (mc == null) {
        onUnavailable()
        return false
    }
    action(mc)
    return true
}
