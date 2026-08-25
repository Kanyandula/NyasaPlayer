package com.example.nyasaplayer.core.playback

import com.example.nyasaplayer.core.common.models.PlaybackState
import com.example.nyasaplayer.core.common.models.Song
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * The restore contract, which both surfaces depend on and neither could test before T3:
 * `PlaybackStatePersistence` used to read the signed-in user through `FirebaseUser`, which no
 * fake can construct. It reads `AuthRepository.currentUserId` now (plan D-T3.2).
 */
class PlaybackStatePersistenceTest {

    private lateinit var userRepo: TestUserRepository
    private lateinit var authRepo: TestAuthRepository
    private lateinit var songRepo: TestSongRepository
    private lateinit var persistence: PlaybackStatePersistence

    @Before
    fun setUp() {
        userRepo = TestUserRepository()
        authRepo = TestAuthRepository().apply { userId = "driver-1" }
        songRepo = TestSongRepository()
        persistence = PlaybackStatePersistence(userRepo, authRepo, songRepo)
    }

    private fun song(id: String) = Song(mediaId = id, title = id.uppercase())

    private fun savedState(
        currentSongId: String,
        queueSongIds: List<String>,
        queueIndex: Int,
        positionMs: Long = 0L,
        repeatMode: String = RepeatMode.Off.name,
    ) = PlaybackState(
        currentSongId = currentSongId,
        positionMs = positionMs,
        queueSongIds = queueSongIds,
        queueIndex = queueIndex,
        repeatMode = repeatMode,
    )

    // ── Nothing to restore ──

    @Test
    fun restore_noSignedInUser_returnsNull() = runTest {
        authRepo.userId = null
        userRepo.playbackState = savedState("a", listOf("a"), 0)
        songRepo.songs.value = listOf(song("a"))

        assertNull(persistence.restore())
    }

    @Test
    fun restore_noSavedState_returnsNull() = runTest {
        songRepo.songs.value = listOf(song("a"))

        assertNull(persistence.restore())
    }

    @Test
    fun restore_blankCurrentSongId_returnsNull() = runTest {
        userRepo.playbackState = savedState("", listOf("a"), 0)
        songRepo.songs.value = listOf(song("a"))

        assertNull(persistence.restore())
    }

    @Test
    fun restore_noSavedIdStillResolves_returnsNull() = runTest {
        userRepo.playbackState = savedState("a", listOf("a", "b"), 0)
        songRepo.songs.value = listOf(song("z"))

        assertNull(persistence.restore())
    }

    // ── Queue shape ──

    @Test
    fun restore_queueKeepsSavedOrder_notRepositoryOrder() = runTest {
        userRepo.playbackState = savedState("b", listOf("c", "b", "a"), 1)
        songRepo.songs.value = listOf(song("a"), song("b"), song("c"))

        val restored = requireNotNull(persistence.restore())

        assertEquals(listOf("c", "b", "a"), restored.queue.map { it.mediaId })
    }

    @Test
    fun restore_queueIndexPastEnd_coercesToLastIndex() = runTest {
        // currentSongId is absent from the catalogue, so only the index is left to go on.
        userRepo.playbackState = savedState("gone", listOf("a", "b"), 47)
        songRepo.songs.value = listOf(song("a"), song("b"))

        val restored = requireNotNull(persistence.restore())

        assertEquals(1, restored.index)
        assertEquals("b", restored.song.mediaId)
    }

    // ── The saved song wins over the saved index (plan D-T3.8) ──

    @Test
    fun restore_songDeletedEarlierInQueue_stillResumesTheSavedSong() = runTest {
        // Saved with "d" at index 3. "b" has since left the catalogue, so the surviving queue is
        // [a, c, d, e] and the saved index now names "e". Coercion cannot catch this: index 3 is
        // still in range, just wrong.
        userRepo.playbackState = savedState("d", listOf("a", "b", "c", "d", "e"), 3)
        songRepo.songs.value = listOf(song("a"), song("c"), song("d"), song("e"))

        val restored = requireNotNull(persistence.restore())

        assertEquals("d", restored.song.mediaId)
        assertEquals(2, restored.index)
        assertEquals("d", restored.queue[restored.index].mediaId)
    }

    @Test
    fun restore_savedSongItselfDeleted_fallsBackToTheSavedIndex() = runTest {
        userRepo.playbackState = savedState("b", listOf("a", "b", "c"), 1)
        songRepo.songs.value = listOf(song("a"), song("c"))

        val restored = requireNotNull(persistence.restore())

        assertEquals(1, restored.index)
        assertEquals("c", restored.song.mediaId)
    }

    // ── Repeat mode ──

    @Test
    fun restore_unparseableRepeatMode_fallsBackToOff() = runTest {
        userRepo.playbackState = savedState("a", listOf("a"), 0, repeatMode = "Sideways")
        songRepo.songs.value = listOf(song("a"))

        assertEquals(RepeatMode.Off, requireNotNull(persistence.restore()).repeatMode)
    }

    // ── Happy path ──

    @Test
    fun restore_savedSession_restoresQueueIndexSongPositionAndMode() = runTest {
        userRepo.playbackState = savedState(
            currentSongId = "b",
            queueSongIds = listOf("a", "b", "c"),
            queueIndex = 1,
            positionMs = 42_000L,
            repeatMode = RepeatMode.All.name,
        )
        songRepo.songs.value = listOf(song("a"), song("b"), song("c"))

        val restored = requireNotNull(persistence.restore())

        assertEquals(listOf("a", "b", "c"), restored.queue.map { it.mediaId })
        assertEquals(1, restored.index)
        assertEquals("b", restored.song.mediaId)
        assertEquals(42_000L, restored.positionMs)
        assertEquals(RepeatMode.All, restored.repeatMode)
    }
}
