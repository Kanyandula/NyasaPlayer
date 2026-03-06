package com.example.nyasaplayer.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.nyasaplayer.models.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class PlayerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exoPlayer: ExoPlayer,
) {
    val player: Player get() = exoPlayer

    private fun buildMediaItem(song: Song): MediaItem = MediaItem.Builder()
        .setUri(Uri.parse(song.resolvedAudioUrl))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(song.title)
                .setSubtitle(song.subtitle)
                .setArtist(song.resolvedArtistName)
                .setArtworkUri(Uri.parse(song.resolvedCoverUrl))
                .build(),
        )
        .build()

    private fun ensureServiceStarted() {
        context.startService(Intent(context, PlaybackService::class.java))
    }

    fun play(song: Song) {
        ensureServiceStarted()
        exoPlayer.setMediaItem(buildMediaItem(song))
        exoPlayer.prepare()
        exoPlayer.play()
    }

    fun prepareOnly(song: Song) {
        exoPlayer.setMediaItem(buildMediaItem(song))
        exoPlayer.prepare()
    }

    fun resume() {
        ensureServiceStarted()
        exoPlayer.play()
    }

    fun pause() {
        exoPlayer.pause()
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
    }

    fun stop() {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        context.stopService(Intent(context, PlaybackService::class.java))
    }

    fun setRepeatMode(mode: RepeatMode) {
        exoPlayer.repeatMode = when (mode) {
            RepeatMode.Off -> Player.REPEAT_MODE_OFF
            RepeatMode.All -> Player.REPEAT_MODE_OFF // queue wrap handled in ViewModel
            RepeatMode.One -> Player.REPEAT_MODE_ONE
        }
    }

    val isPlaying: Boolean get() = exoPlayer.isPlaying
    val currentPosition: Long get() = exoPlayer.currentPosition
    val duration: Long get() = exoPlayer.duration.coerceAtLeast(0L)
}
