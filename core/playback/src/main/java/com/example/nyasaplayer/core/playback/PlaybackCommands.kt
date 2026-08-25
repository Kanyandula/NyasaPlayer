package com.example.nyasaplayer.core.playback

import android.os.Bundle
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
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
}

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
    }
    return sendCustomCommand(
        SessionCommand(PlaybackCommands.CMD_RESTORE_STATE, Bundle.EMPTY),
        args,
    )
}
