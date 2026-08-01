package com.example.nyasaplayer.core.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.example.nyasaplayer.core.data.api.AuthRepository
import com.example.nyasaplayer.core.data.api.UserRepository
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.CancellationException
import javax.inject.Inject

private const val PersistIntervalMs = 30_000L
private const val TAG = "PlaybackService"

@UnstableApi
@AndroidEntryPoint
@Suppress("TooManyFunctions") // Service combines lifecycle, session callbacks, and command handlers.
class PlaybackService : MediaLibraryService() {

    @Inject lateinit var queueManager: PlaybackQueueManager

    @Inject lateinit var persistence: PlaybackStatePersistence

    @Inject lateinit var browseTree: MediaBrowseTree

    @Inject lateinit var userRepository: UserRepository

    @Inject lateinit var authRepository: AuthRepository

    private lateinit var exoPlayer: ExoPlayer
    private var mediaSession: MediaLibrarySession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Media id of the current item, driving the like button's filled/unfilled state. */
    private val currentMediaId = MutableStateFlow<String?>(null)

    /** Latest observed like state for [currentMediaId]; read when toggling so no extra fetch is needed. */
    private var isCurrentSongLiked = false

    override fun onCreate() {
        super.onCreate()
        exoPlayer = buildExoPlayer()
        exoPlayer.addListener(playerListener)
        val builder = MediaLibrarySession.Builder(this, exoPlayer, libraryCallback)
            .setCustomLayout(listOf(likeButton(isLiked = false)))
        packageManager.getLaunchIntentForPackage(packageName)?.let { launchIntent ->
            builder.setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    launchIntent,
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
        mediaSession = builder.build()
        addSession(mediaSession!!)
        startPersistenceLoop()
        observeCurrentSongLikeState()
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

    // ── MediaLibrarySession.Callback ──

    private val libraryCallback = object : MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(PlaybackCommands.CMD_SET_QUEUE, Bundle.EMPTY))
                .add(SessionCommand(PlaybackCommands.CMD_SHUFFLE_PLAY, Bundle.EMPTY))
                .add(SessionCommand(PlaybackCommands.CMD_RESTORE_STATE, Bundle.EMPTY))
                .add(SessionCommand(PlaybackCommands.CMD_TOGGLE_SHUFFLE, Bundle.EMPTY))
                .add(SessionCommand(PlaybackCommands.CMD_TOGGLE_LIKE, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        @Suppress("TooGenericExceptionCaught")
        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            return try {
                when (customCommand.customAction) {
                    PlaybackCommands.CMD_SET_QUEUE -> handleSetQueue(args)
                    PlaybackCommands.CMD_SHUFFLE_PLAY -> handleShufflePlay(args)
                    PlaybackCommands.CMD_RESTORE_STATE -> handleRestoreState(args)
                    PlaybackCommands.CMD_TOGGLE_SHUFFLE -> handleToggleShuffle()
                    // Only async handler: completes its own future off the dispatch path.
                    PlaybackCommands.CMD_TOGGLE_LIKE -> return handleToggleLikeAsync()
                    else -> return Futures.immediateFuture(
                        SessionResult(SessionError.ERROR_NOT_SUPPORTED),
                    )
                }
                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            } catch (e: Exception) {
                Log.e(TAG, "onCustomCommand failed: ${customCommand.customAction}", e)
                Futures.immediateFuture(SessionResult(e.toSessionErrorCode()))
            }
        }

        // ── Browse tree callbacks ──

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(browseTree.rootItem, params))

        @Suppress("TooGenericExceptionCaught")
        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            serviceScope.launch {
                try {
                    val children = browseTree.getChildren(parentId)
                    val paged = children.drop(page * pageSize).take(pageSize)
                    future.set(LibraryResult.ofItemList(ImmutableList.copyOf(paged), params))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "onGetChildren failed for $parentId", e)
                    future.set(LibraryResult.ofError(e.toSessionErrorCode()))
                }
            }
            return future
        }

        @Suppress("TooGenericExceptionCaught")
        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val future = SettableFuture.create<LibraryResult<MediaItem>>()
            serviceScope.launch {
                try {
                    val item = browseTree.getItem(mediaId)
                    if (item != null) {
                        future.set(LibraryResult.ofItem(item, null))
                    } else {
                        future.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "onGetItem failed for $mediaId", e)
                    future.set(LibraryResult.ofError(e.toSessionErrorCode()))
                }
            }
            return future
        }

        @Suppress("TooGenericExceptionCaught")
        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> {
            val future = SettableFuture.create<LibraryResult<Void>>()
            serviceScope.launch {
                try {
                    val results = browseTree.search(query)
                    session.notifySearchResultChanged(browser, query, results.size, params)
                    future.set(LibraryResult.ofVoid())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "onSearch failed for query: $query", e)
                    future.set(LibraryResult.ofError(e.toSessionErrorCode()))
                }
            }
            return future
        }

        @Suppress("TooGenericExceptionCaught")
        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            serviceScope.launch {
                try {
                    val results = browseTree.search(query)
                    val paged = results.drop(page * pageSize).take(pageSize)
                    future.set(LibraryResult.ofItemList(ImmutableList.copyOf(paged), params))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "onGetSearchResult failed for query: $query", e)
                    future.set(LibraryResult.ofError(e.toSessionErrorCode()))
                }
            }
            return future
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

    // ── Like button ──

    /**
     * The template's like affordance. Media3 resolves [CommandButton.ICON_HEART_FILLED] to a bundled
     * drawable, which the legacy `PlaybackStateCompat` custom action the AAOS template reads needs.
     */
    private fun likeButton(isLiked: Boolean): CommandButton = CommandButton.Builder(
        if (isLiked) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED,
    )
        .setSessionCommand(SessionCommand(PlaybackCommands.CMD_TOGGLE_LIKE, Bundle.EMPTY))
        .setDisplayName(if (isLiked) "Unlike" else "Like")
        .build()

    /**
     * Pushes a fresh custom layout whenever the current song or its like state changes, so the
     * button also reflects likes made on the phone.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeCurrentSongLikeState() {
        serviceScope.launch {
            currentMediaId
                .flatMapLatest { likedFlow(it) }
                .distinctUntilChanged()
                .collect { isLiked ->
                    isCurrentSongLiked = isLiked
                    mediaSession?.setCustomLayout(listOf(likeButton(isLiked)))
                }
        }
    }

    private fun likedFlow(mediaId: String?): Flow<Boolean> {
        val uid = authRepository.currentUser?.uid
        if (uid.isNullOrBlank() || mediaId.isNullOrBlank()) return flowOf(false)
        return userRepository.isLiked(uid, mediaId).catch { e ->
            Log.e(TAG, "isLiked failed for $mediaId", e)
            emit(false)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun handleToggleLikeAsync(): ListenableFuture<SessionResult> {
        val mediaId = exoPlayer.currentMediaItem?.mediaId
        val uid = authRepository.currentUser?.uid
        if (uid.isNullOrBlank() || mediaId.isNullOrBlank()) {
            return Futures.immediateFuture(SessionResult(SessionError.ERROR_INVALID_STATE))
        }
        // Writes are idempotent, so the observed state is enough to pick a direction; the isLiked
        // flow then pushes the updated button.
        val liked = isCurrentSongLiked
        val future = SettableFuture.create<SessionResult>()
        serviceScope.launch {
            try {
                if (liked) userRepository.unlikeSong(uid, mediaId) else userRepository.likeSong(uid, mediaId)
                future.set(SessionResult(SessionResult.RESULT_SUCCESS))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "CMD_TOGGLE_LIKE failed", e)
                future.set(SessionResult(e.toSessionErrorCode()))
            }
        }
        return future
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
            currentMediaId.value = mediaItem?.mediaId
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) saveState()
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

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
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

@UnstableApi
private fun Throwable.toSessionErrorCode(): Int = when (this) {
    is IOException -> SessionError.ERROR_IO
    else -> SessionError.ERROR_UNKNOWN
}
