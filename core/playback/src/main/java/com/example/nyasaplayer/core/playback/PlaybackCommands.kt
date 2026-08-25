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
 * Both surfaces restore, so both need this exact bundle; the keys live one object up, and a second
 * hand-built copy of them is how a wire format starts drifting.
 *
 * The returned future carries the service's verdict. The car waits for it before showing anything,
 * because a caller that shows the restored track without checking can end up displaying a session
 * the player never received. Mobile discards it and shows the track either way — pre-existing
 * behaviour that T3 deliberately did not change, since mobile restore is out of its scope.
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
