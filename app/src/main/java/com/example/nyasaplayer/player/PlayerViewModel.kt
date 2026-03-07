package com.example.nyasaplayer.player

import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.common.util.NetworkMonitor
import com.example.nyasaplayer.core.data.api.AuthRepository
import com.example.nyasaplayer.core.data.api.UserRepository
import com.example.nyasaplayer.download.SongDownloadManager
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.ExecutionException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

private const val PositionUpdateIntervalMs = 250L

@UnstableApi
@HiltViewModel
@Suppress("TooManyFunctions")
class PlayerViewModel @Inject constructor(
    private val controllerFuture: ListenableFuture<MediaController>,
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val persistence: PlaybackStatePersistence,
    private val networkMonitor: NetworkMonitor,
    val downloadManager: SongDownloadManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        PlayerUiState(isOffline = !networkMonitor.isOnline.value),
    )
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        _uiState.update {
            it.copy(
                error = PlayerError(
                    title = "Playback Error",
                    message = throwable.message ?: "An unexpected error occurred",
                ),
            )
        }
    }

    private var mediaController: MediaController? = null
    private var likeObserverJob: Job? = null

    private val userId: String? get() = authRepository.currentUser?.uid
    private val isOnline: Boolean get() = networkMonitor.isOnline.value

    init {
        connectController()
        observeNetworkState()
    }

    private fun connectController() {
        controllerFuture.addListener(
            {
                try {
                    val controller = controllerFuture.get()
                    mediaController = controller
                    controller.addListener(controllerListener)
                    startPositionPolling()
                    restorePlaybackState()
                } catch (_: ExecutionException) {
                    _uiState.update {
                        it.copy(
                            error = PlayerError(
                                title = "Player Error",
                                message = "Could not connect to playback service",
                                isPlaybackError = false,
                            ),
                        )
                    }
                }
            },
            MoreExecutors.directExecutor(),
        )
    }

    // ── MediaController.Listener ──

    private val controllerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (mediaItem == null) return
            updateCurrentSong(mediaItem)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val controller = mediaController ?: return
            if (playbackState == Player.STATE_BUFFERING && !isOnline) {
                controller.pause()
                _uiState.update {
                    it.copy(
                        isBuffering = false,
                        isPlaying = false,
                        error = PlayerError(
                            title = "Offline",
                            message = "Connection lost. Download songs for offline playback.",
                        ),
                    )
                }
                return
            }
            _uiState.update {
                it.copy(
                    isBuffering = playbackState == Player.STATE_BUFFERING,
                    isPlaying = controller.isPlaying,
                )
            }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            val mode = repeatMode.toAppRepeatMode()
            _uiState.update {
                it.copy(
                    repeatMode = mode,
                    hasNext = hasNextTrack(mode),
                )
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val isNetwork = error.cause is IOException
            _uiState.update {
                it.copy(
                    isPlaying = false,
                    error = PlayerError(
                        title = if (isNetwork) "Offline" else "Playback Error",
                        message = if (isNetwork) {
                            "This song isn't available offline"
                        } else {
                            error.message ?: "Playback error"
                        },
                    ),
                )
            }
        }
    }

    private fun updateCurrentSong(mediaItem: MediaItem) {
        val song = mediaItem.toSong()
        val controller = mediaController ?: return
        _uiState.update {
            it.copy(
                currentSong = song,
                durationMs = song.durationMs,
                hasPrevious = controller.hasPreviousMediaItem(),
                hasNext = hasNextTrack(it.repeatMode),
            )
        }
        observeCurrentSongLikeState(song.mediaId)
    }

    // ── Playback Actions ──

    fun playSong(songs: List<Song>, song: Song) {
        val isDownloaded = downloadManager.getLocalFileUri(song.mediaId) != null
        if (!isOnline && !isDownloaded) {
            showOfflineError(song)
            return
        }
        val resolvedSongs = songs.map { resolveSongUri(it) }
        val resolvedSong = resolveSongUri(song)
        val startIndex = resolvedSongs.indexOfFirst { it.mediaId == song.mediaId }.coerceAtLeast(0)
        sendSetQueueCommand(resolvedSongs, startIndex)
        _uiState.update {
            it.copy(
                playerMode = PlayerMode.Expanded,
                currentSong = resolvedSong,
                isPlaying = true,
                isShuffled = false,
            )
        }
        observeCurrentSongLikeState(song.mediaId)
        logRecentlyPlayedSafe(song.mediaId)
    }

    private fun resolveSongUri(song: Song): Song {
        val localUri = downloadManager.getLocalFileUri(song.mediaId) ?: return song
        return song.copy(audioUrl = localUri, songUrl = localUri)
    }

    fun shufflePlay(songs: List<Song>) {
        if (songs.isEmpty()) return
        val resolvedSongs = songs.map { resolveSongUri(it) }
        val hasPlayable = resolvedSongs.any {
            isOnline || downloadManager.getLocalFileUri(it.mediaId) != null
        }
        if (!hasPlayable) {
            showOfflineError(songs.first())
            return
        }
        sendShufflePlayCommand(resolvedSongs)
        _uiState.update {
            it.copy(
                playerMode = PlayerMode.Expanded,
                currentSong = resolvedSongs.first(),
                isPlaying = true,
                isShuffled = true,
            )
        }
    }

    fun toggleShuffle() {
        val controller = mediaController ?: return
        _uiState.update { it.copy(isShuffled = !it.isShuffled) }
        controller.sendCustomCommand(
            SessionCommand(PlaybackCommands.CMD_TOGGLE_SHUFFLE, Bundle.EMPTY),
            Bundle.EMPTY,
        )
    }

    fun skipNext() {
        val controller = mediaController ?: return
        if (controller.hasNextMediaItem()) {
            controller.seekToNextMediaItem()
        }
    }

    fun skipPrevious() {
        val controller = mediaController ?: return
        if (controller.hasPreviousMediaItem()) {
            controller.seekToPreviousMediaItem()
        }
    }

    fun toggleRepeatMode() {
        val controller = mediaController ?: return
        controller.repeatMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            val currentMediaId = _uiState.value.currentSong?.mediaId
            val isDownloaded = currentMediaId != null &&
                downloadManager.getLocalFileUri(currentMediaId) != null
            if (!isOnline && !isDownloaded) {
                _uiState.update {
                    it.copy(
                        error = PlayerError(
                            title = "Offline",
                            message = "Can't stream while offline. Download songs for offline playback.",
                        ),
                    )
                }
                return
            }
            controller.play()
        }
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        _uiState.update { it.copy(currentPositionMs = positionMs) }
    }

    fun expand() {
        _uiState.update { it.copy(playerMode = PlayerMode.Expanded) }
    }

    fun collapse() {
        _uiState.update { it.copy(playerMode = PlayerMode.Mini) }
    }

    fun dismiss() {
        mediaController?.stop()
        mediaController?.clearMediaItems()
        _uiState.update { PlayerUiState() }
    }

    // ── Custom Commands ──

    private fun sendSetQueueCommand(songs: List<Song>, startIndex: Int) {
        val controller = mediaController ?: return
        val args = Bundle().apply {
            putBundle(PlaybackCommands.KEY_SONGS, songs.toBundle())
            putInt(PlaybackCommands.KEY_START_INDEX, startIndex)
        }
        controller.sendCustomCommand(
            SessionCommand(PlaybackCommands.CMD_SET_QUEUE, Bundle.EMPTY),
            args,
        )
    }

    private fun sendShufflePlayCommand(songs: List<Song>) {
        val controller = mediaController ?: return
        val args = Bundle().apply {
            putBundle(PlaybackCommands.KEY_SONGS, songs.toBundle())
        }
        controller.sendCustomCommand(
            SessionCommand(PlaybackCommands.CMD_SHUFFLE_PLAY, Bundle.EMPTY),
            args,
        )
    }

    private fun sendRestoreCommand(restored: RestoredPlayback) {
        val controller = mediaController ?: return
        val args = Bundle().apply {
            putBundle(PlaybackCommands.KEY_SONGS, restored.queue.toBundle())
            putInt(PlaybackCommands.KEY_START_INDEX, restored.index)
            putLong(PlaybackCommands.KEY_POSITION_MS, restored.positionMs)
            putString(PlaybackCommands.KEY_REPEAT_MODE, restored.repeatMode.name)
        }
        controller.sendCustomCommand(
            SessionCommand(PlaybackCommands.CMD_RESTORE_STATE, Bundle.EMPTY),
            args,
        )
    }

    // ── Offline Handling ──

    private fun showOfflineError(song: Song) {
        _uiState.update {
            it.copy(
                playerMode = PlayerMode.Expanded,
                currentSong = song,
                isPlaying = false,
                isBuffering = false,
                currentPositionMs = 0L,
                durationMs = song.durationMs,
                error = PlayerError(
                    title = "Offline",
                    message = "This song isn't available offline",
                ),
            )
        }
    }

    private fun observeNetworkState() {
        networkMonitor.isOnline.onEach { online ->
            _uiState.update { it.copy(isOffline = !online) }
        }.launchIn(viewModelScope)
    }

    // ── Like / Unlike ──

    fun toggleLike() {
        val uid = userId ?: return
        val mediaId = _uiState.value.currentSong?.mediaId ?: return
        val wasLiked = _uiState.value.isCurrentSongLiked
        _uiState.update { it.copy(isCurrentSongLiked = !wasLiked) }
        viewModelScope.launch(exceptionHandler) {
            try {
                if (wasLiked) {
                    userRepository.unlikeSong(uid, mediaId)
                } else {
                    userRepository.likeSong(uid, mediaId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
            ) {
                _uiState.update {
                    it.copy(
                        isCurrentSongLiked = wasLiked,
                        error = PlayerError(
                            title = "Sync Error",
                            message = "Couldn't update like status",
                            isPlaybackError = false,
                        ),
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun logRecentlyPlayedSafe(mediaId: String) {
        userId?.let { uid ->
            viewModelScope.launch(exceptionHandler) {
                try {
                    userRepository.logRecentlyPlayed(uid, mediaId)
                } catch (e: CancellationException) {
                    throw e
                } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
                    // Silent fail — non-critical
                }
            }
        }
    }

    private fun observeCurrentSongLikeState(mediaId: String) {
        likeObserverJob?.cancel()
        val uid = userId ?: return
        likeObserverJob = viewModelScope.launch(exceptionHandler) {
            try {
                userRepository.isLiked(uid, mediaId).collect { liked ->
                    _uiState.update { it.copy(isCurrentSongLiked = liked) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
                // Silent fail — like state is non-critical
            }
        }
    }

    // ── Playback State Persistence ──

    private fun restorePlaybackState() {
        viewModelScope.launch(exceptionHandler) {
            try {
                val restored = persistence.restore() ?: return@launch
                sendRestoreCommand(restored)
                _uiState.update {
                    it.copy(
                        playerMode = PlayerMode.Mini,
                        currentSong = restored.song,
                        isPlaying = false,
                        currentPositionMs = restored.positionMs,
                        hasPrevious = restored.index > 0,
                        hasNext = restored.index < restored.queue.lastIndex,
                        repeatMode = restored.repeatMode,
                    )
                }
                observeCurrentSongLikeState(restored.song.mediaId)
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
            ) {
                _uiState.update {
                    it.copy(
                        error = PlayerError(
                            title = "Restore Error",
                            message = "Couldn't restore previous session",
                            isPlaybackError = false,
                        ),
                    )
                }
            }
        }
    }

    // ── Position Polling ──

    private fun startPositionPolling() {
        viewModelScope.launch(exceptionHandler) {
            while (true) {
                delay(PositionUpdateIntervalMs)
                val controller = mediaController ?: continue
                if (controller.isPlaying || _uiState.value.playerMode != PlayerMode.Hidden) {
                    _uiState.update {
                        it.copy(
                            currentPositionMs = controller.currentPosition,
                            durationMs = controller.duration.coerceAtLeast(0L),
                        )
                    }
                }
            }
        }
    }

    // ── Helpers ──

    private fun hasNextTrack(repeatMode: RepeatMode): Boolean {
        val controller = mediaController ?: return false
        return controller.hasNextMediaItem() ||
            repeatMode == RepeatMode.All
    }

    private fun Int.toAppRepeatMode(): RepeatMode = when (this) {
        Player.REPEAT_MODE_ALL -> RepeatMode.All
        Player.REPEAT_MODE_ONE -> RepeatMode.One
        else -> RepeatMode.Off
    }

    override fun onCleared() {
        super.onCleared()
        MediaController.releaseFuture(controllerFuture)
    }
}
