package com.example.nyasaplayer.core.playback

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
