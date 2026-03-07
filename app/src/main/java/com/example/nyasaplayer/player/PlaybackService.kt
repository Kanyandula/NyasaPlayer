package com.example.nyasaplayer.player

import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val PersistIntervalMs = 30_000L

@UnstableApi
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var queueManager: PlaybackQueueManager

    @Inject lateinit var persistence: PlaybackStatePersistence

    private lateinit var exoPlayer: ExoPlayer
    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        exoPlayer = buildExoPlayer()
        exoPlayer.addListener(playerListener)
        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setCallback(sessionCallback)
            .build()
        addSession(mediaSession!!)
        startPersistenceLoop()
    }

    private fun buildExoPlayer(): ExoPlayer {
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
        return ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(audioAttributes, true)
            playWhenReady = false
        }
    }

    // ── MediaSession.Callback ──

    private val sessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(PlaybackCommands.CMD_SET_QUEUE, Bundle.EMPTY))
                .add(SessionCommand(PlaybackCommands.CMD_SHUFFLE_PLAY, Bundle.EMPTY))
                .add(SessionCommand(PlaybackCommands.CMD_RESTORE_STATE, Bundle.EMPTY))
                .add(SessionCommand(PlaybackCommands.CMD_TOGGLE_SHUFFLE, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                PlaybackCommands.CMD_SET_QUEUE -> handleSetQueue(args)
                PlaybackCommands.CMD_SHUFFLE_PLAY -> handleShufflePlay(args)
                PlaybackCommands.CMD_RESTORE_STATE -> handleRestoreState(args)
                PlaybackCommands.CMD_TOGGLE_SHUFFLE -> handleToggleShuffle()
                else -> return Futures.immediateFuture(
                    SessionResult(SessionError.ERROR_NOT_SUPPORTED),
                )
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    private fun handleSetQueue(args: Bundle) {
        val songsBundle = args.getBundle(PlaybackCommands.KEY_SONGS) ?: return
        val songs = songsBundle.toSongList()
        if (songs.isEmpty()) return
        val startIndex = args.getInt(PlaybackCommands.KEY_START_INDEX, 0)
        val startSong = songs.getOrNull(startIndex) ?: songs.first()

        queueManager.setQueue(songs, startSong)
        applyQueueToPlayer()
        exoPlayer.play()
    }

    private fun handleShufflePlay(args: Bundle) {
        val songsBundle = args.getBundle(PlaybackCommands.KEY_SONGS) ?: return
        val songs = songsBundle.toSongList()
        if (songs.isEmpty()) return

        queueManager.setQueueShuffled(songs)
        applyQueueToPlayer()
        exoPlayer.play()
    }

    @Suppress("ReturnCount")
    private fun handleRestoreState(args: Bundle) {
        val songsBundle = args.getBundle(PlaybackCommands.KEY_SONGS) ?: return
        val songs = songsBundle.toSongList()
        if (songs.isEmpty()) return
        val index = args.getInt(PlaybackCommands.KEY_START_INDEX, 0)
        val positionMs = args.getLong(PlaybackCommands.KEY_POSITION_MS, 0L)
        val repeatModeName = args.getString(PlaybackCommands.KEY_REPEAT_MODE, RepeatMode.Off.name)
        val repeatMode = try {
            RepeatMode.valueOf(repeatModeName)
        } catch (_: IllegalArgumentException) {
            RepeatMode.Off
        }

        queueManager.restoreQueue(songs, index)
        applyRepeatMode(repeatMode)
        applyQueueToPlayer()
        exoPlayer.seekTo(queueManager.currentIndex, positionMs)
        exoPlayer.playWhenReady = false
    }

    private fun handleToggleShuffle() {
        queueManager.toggleShuffle()
        val currentPosition = exoPlayer.currentPosition
        applyQueueToPlayer()
        exoPlayer.seekTo(queueManager.currentIndex, currentPosition)
    }

    private fun applyQueueToPlayer() {
        val mediaItems = queueManager.queue.map { it.toMediaItem() }
        exoPlayer.setMediaItems(mediaItems, queueManager.currentIndex, 0L)
        exoPlayer.prepare()
    }

    private fun applyRepeatMode(mode: RepeatMode) {
        exoPlayer.repeatMode = when (mode) {
            RepeatMode.Off -> Player.REPEAT_MODE_OFF
            RepeatMode.All -> Player.REPEAT_MODE_ALL
            RepeatMode.One -> Player.REPEAT_MODE_ONE
        }
    }

    // ── Player.Listener for auto-advance ──

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            queueManager.currentIndex = exoPlayer.currentMediaItemIndex
        }
    }

    // ── Periodic persistence ──

    private fun startPersistenceLoop() {
        serviceScope.launch {
            while (isActive) {
                delay(PersistIntervalMs)
                saveState()
            }
        }
    }

    private fun saveState() {
        if (!exoPlayer.isPlaying) return
        val song = exoPlayer.currentMediaItem?.toSong() ?: return
        persistence.save(
            serviceScope,
            song,
            exoPlayer.currentPosition,
            queueManager.queueSongIds(),
            queueManager.currentIndex,
            currentRepeatMode(),
        )
    }

    private fun saveFinalState() {
        val song = exoPlayer.currentMediaItem?.toSong() ?: return
        persistence.saveFinal(
            song,
            exoPlayer.currentPosition,
            queueManager.queueSongIds(),
            queueManager.currentIndex,
            currentRepeatMode(),
        )
    }

    private fun currentRepeatMode(): RepeatMode = when (exoPlayer.repeatMode) {
        Player.REPEAT_MODE_ONE -> RepeatMode.One
        Player.REPEAT_MODE_ALL -> RepeatMode.All
        else -> RepeatMode.Off
    }

    // ── Lifecycle ──

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        saveFinalState()
        serviceScope.cancel()
        mediaSession?.release()
        mediaSession = null
        exoPlayer.removeListener(playerListener)
        exoPlayer.release()
        super.onDestroy()
    }
}
