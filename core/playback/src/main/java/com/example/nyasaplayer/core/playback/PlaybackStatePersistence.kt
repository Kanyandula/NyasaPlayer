package com.example.nyasaplayer.core.playback

import com.example.nyasaplayer.core.common.models.PlaybackState
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.data.api.AuthRepository
import com.example.nyasaplayer.core.data.api.SongRepository
import com.example.nyasaplayer.core.data.api.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

data class RestoredPlayback(
    val queue: List<Song>,
    val index: Int,
    val song: Song,
    val positionMs: Long,
    val repeatMode: RepeatMode,
)

@Singleton
class PlaybackStatePersistence @Inject constructor(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val songRepository: SongRepository,
) {
    private companion object {
        const val SAVE_FINAL_TIMEOUT_MS = 2000L
    }

    private val userId: String? get() = authRepository.currentUser?.uid

    fun save(
        scope: CoroutineScope,
        currentSong: Song,
        positionMs: Long,
        queueSongIds: List<String>,
        queueIndex: Int,
        repeatMode: RepeatMode,
    ) {
        val uid = userId ?: return
        scope.launch {
            try {
                userRepository.savePlaybackState(
                    uid,
                    buildState(currentSong, positionMs, queueSongIds, queueIndex, repeatMode),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
                // Silent fail — persistence is best-effort
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    fun saveFinal(
        currentSong: Song?,
        positionMs: Long,
        queueSongIds: List<String>,
        queueIndex: Int,
        repeatMode: RepeatMode,
    ) {
        val uid = userId ?: return
        val song = currentSong ?: return
        runBlocking(Dispatchers.IO) {
            withTimeoutOrNull(SAVE_FINAL_TIMEOUT_MS) {
                try {
                    userRepository.savePlaybackState(
                        uid,
                        buildState(song, positionMs, queueSongIds, queueIndex, repeatMode),
                    )
                } catch (_: Exception) {
                    // Silent fail — final save is best-effort
                }
            }
        }
    }

    @Suppress("ReturnCount")
    suspend fun restore(): RestoredPlayback? {
        return try {
            val uid = userId ?: return null
            val saved = userRepository.getPlaybackState(uid) ?: return null
            if (saved.currentSongId.isBlank()) return null

            val songs = songRepository.getSongsByIds(saved.queueSongIds)
            if (songs.isEmpty()) return null

            val songMap = songs.associateBy { it.mediaId }
            val orderedQueue = saved.queueSongIds.mapNotNull { songMap[it] }
            if (orderedQueue.isEmpty()) return null

            val restoredIndex = saved.queueIndex.coerceIn(0, orderedQueue.lastIndex)
            val restoredSong = orderedQueue[restoredIndex]

            val restoredRepeatMode = try {
                RepeatMode.valueOf(saved.repeatMode)
            } catch (_: IllegalArgumentException) {
                RepeatMode.Off
            }

            RestoredPlayback(
                queue = orderedQueue,
                index = restoredIndex,
                song = restoredSong,
                positionMs = saved.positionMs,
                repeatMode = restoredRepeatMode,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
            null
        }
    }

    private fun buildState(
        currentSong: Song,
        positionMs: Long,
        queueSongIds: List<String>,
        queueIndex: Int,
        repeatMode: RepeatMode,
    ) = PlaybackState(
        currentSongId = currentSong.mediaId,
        positionMs = positionMs,
        queueSongIds = queueSongIds,
        queueIndex = queueIndex,
        repeatMode = repeatMode.name,
        savedAt = System.currentTimeMillis(),
    )
}
