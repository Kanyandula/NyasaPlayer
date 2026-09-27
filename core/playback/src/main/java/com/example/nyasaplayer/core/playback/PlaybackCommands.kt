package com.example.nyasaplayer.core.playback

import android.os.Bundle
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.nyasaplayer.core.common.models.Song
import com.google.common.util.concurrent.ListenableFuture

object PlaybackCommands {
    const val CMD_SET_QUEUE = "nyasa.SET_QUEUE"
    const val CMD_SHUFFLE_PLAY = "nyasa.SHUFFLE_PLAY"
    const val CMD_RESTORE_STATE = "nyasa.RESTORE_STATE"
    const val CMD_TOGGLE_SHUFFLE = "nyasa.TOGGLE_SHUFFLE"
    const val CMD_TOGGLE_LIKE = "nyasa.TOGGLE_LIKE"

    const val KEY_SONGS = "songs"
    const val KEY_START_INDEX = "startIndex"
    const val KEY_POSITION_MS = "positionMs"
    const val KEY_REPEAT_MODE = "repeatMode"
    const val KEY_ORIGIN_KIND = "originKind"
    const val KEY_ORIGIN_ID = "originId"
    const val KEY_ORIGIN_NAME = "originName"
}

fun Bundle.putQueueOrigin(origin: QueueOrigin) {
    val (kind, id, name) = origin.toFields()
    putString(PlaybackCommands.KEY_ORIGIN_KIND, kind)
    putString(PlaybackCommands.KEY_ORIGIN_ID, id)
    putString(PlaybackCommands.KEY_ORIGIN_NAME, name)
}

/** [QueueOrigin.None] when the sender attached no origin. */
fun Bundle.getQueueOrigin(): QueueOrigin = queueOriginOf(
    getString(PlaybackCommands.KEY_ORIGIN_KIND),
    getString(PlaybackCommands.KEY_ORIGIN_ID),
    getString(PlaybackCommands.KEY_ORIGIN_NAME),
)

/**
 * Hands a [RestoredPlayback] to `PlaybackService`, which applies the queue paused.
 *
 * The returned future carries the service's verdict. The car waits for it before showing anything;
 * mobile discards it and shows the track either way, which T3 left alone as out of its scope.
 */
fun MediaController.sendRestoreState(restored: RestoredPlayback): ListenableFuture<SessionResult> {
    val args = Bundle().apply {
        putBundle(PlaybackCommands.KEY_SONGS, restored.queue.toBundle())
        putInt(PlaybackCommands.KEY_START_INDEX, restored.index)
        putLong(PlaybackCommands.KEY_POSITION_MS, restored.positionMs)
        putString(PlaybackCommands.KEY_REPEAT_MODE, restored.repeatMode.name)
        putQueueOrigin(restored.origin)
    }
    return sendCustomCommand(
        SessionCommand(PlaybackCommands.CMD_RESTORE_STATE, Bundle.EMPTY),
        args,
    )
}

/**
 * Replaces the queue and starts playing [songs] from [startIndex].
 *
 * Each surface decides separately what to show around this — mobile resolves downloaded songs to
 * local URIs first and opens the expanded player, the car opens nothing — but the command itself
 * is the same on both, which is why it is written once.
 */
fun MediaController.sendSetQueue(
    songs: List<Song>,
    startIndex: Int,
    origin: QueueOrigin = QueueOrigin.None,
): ListenableFuture<SessionResult> {
    val args = Bundle().apply {
        putBundle(PlaybackCommands.KEY_SONGS, songs.toBundle())
        putInt(PlaybackCommands.KEY_START_INDEX, startIndex)
        putQueueOrigin(origin)
    }
    return sendCustomCommand(
        SessionCommand(PlaybackCommands.CMD_SET_QUEUE, Bundle.EMPTY),
        args,
    )
}

/** Replaces the queue with a shuffled [songs] and starts playing. */
fun MediaController.sendShufflePlay(
    songs: List<Song>,
    origin: QueueOrigin = QueueOrigin.None,
): ListenableFuture<SessionResult> {
    val args = Bundle().apply {
        putBundle(PlaybackCommands.KEY_SONGS, songs.toBundle())
        putQueueOrigin(origin)
    }
    return sendCustomCommand(
        SessionCommand(PlaybackCommands.CMD_SHUFFLE_PLAY, Bundle.EMPTY),
        args,
    )
}
