package com.example.nyasaplayer.core.playback

import com.example.nyasaplayer.core.common.models.PlaybackState
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.data.api.DownloadRepository
import com.example.nyasaplayer.core.data.local.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The restore contract, which both surfaces depend on and neither could test before T3:
 * `PlaybackStatePersistence` used to read the signed-in user through `FirebaseUser`, which no
 * fake can construct. It reads `AuthRepository.currentUserId` now (plan D-T3.2).
 */
class PlaybackStatePersistenceTest {

    private lateinit var userRepo: TestUserRepository
    private lateinit var authRepo: TestAuthRepository
    private lateinit var songRepo: TestSongRepository
    private lateinit var downloadRepo: TestDownloadRepository
    private lateinit var persistence: PlaybackStatePersistence

    @get:Rule
    val downloadsDir = TemporaryFolder()

    @Before
    fun setUp() {
        userRepo = TestUserRepository()
        authRepo = TestAuthRepository().apply { userId = "driver-1" }
        songRepo = TestSongRepository()
        downloadRepo = TestDownloadRepository()
        persistence = PlaybackStatePersistence(userRepo, authRepo, songRepo, downloadRepo)
    }

    private fun song(id: String) =
        Song(mediaId = id, title = id.uppercase(), audioUrl = "https://cdn.example/$id.mp3")

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

    // ── Downloaded songs (A9) ──

    @Test
    fun restore_downloadedSong_comesBackPointingAtTheLocalFile() = runTest {
        val file = downloadsDir.newFile("b.audio")
        downloadRepo.paths["b"] = file.absolutePath
        userRepo.playbackState = savedState("b", listOf("a", "b"), 1)
        songRepo.songs.value = listOf(song("a"), song("b"))

        val restored = requireNotNull(persistence.restore())

        val expected = file.toURI().toString()
        assertEquals(expected, restored.song.resolvedAudioUrl)
        assertEquals(expected, restored.queue[1].resolvedAudioUrl)
        // The undownloaded neighbour is untouched, so resolution is not blanket rewriting.
        assertEquals("https://cdn.example/a.mp3", restored.queue[0].resolvedAudioUrl)
    }

    @Test
    fun restore_downloadRecordedButFileDeleted_keepsTheStreamUrl() = runTest {
        downloadRepo.paths["a"] = downloadsDir.root.resolve("gone.audio").absolutePath
        userRepo.playbackState = savedState("a", listOf("a"), 0)
        songRepo.songs.value = listOf(song("a"))

        val restored = requireNotNull(persistence.restore())

        assertEquals("https://cdn.example/a.mp3", restored.song.resolvedAudioUrl)
        assertTrue(restored.queue.single().resolvedAudioUrl.startsWith("https://"))
    }
}

/** Only [getLocalFilePath] is consulted on the restore path; the rest is never reached. */
class TestDownloadRepository : DownloadRepository {
    val paths = mutableMapOf<String, String>()

    override fun getLocalFilePath(mediaId: String): String? = paths[mediaId]

    override fun getCompletedDownloads(): Flow<List<DownloadEntity>> = flowOf(emptyList())
    override fun getAllDownloads(): Flow<List<DownloadEntity>> = flowOf(emptyList())
    override fun observeDownload(mediaId: String): Flow<DownloadEntity?> = flowOf(null)
    override fun observeDownloads(mediaIds: List<String>): Flow<List<DownloadEntity>> =
        flowOf(emptyList())
    override fun getDownloadedMediaIds(): Flow<List<String>> = flowOf(emptyList())
    override fun getDownloadedCount(): Flow<Int> = flowOf(0)
    override fun getTotalDownloadedSize(): Flow<Long> = flowOf(0L)
    override suspend fun getDownload(mediaId: String): DownloadEntity? = null
    override suspend fun addDownload(mediaId: String) = Unit
    override suspend fun updateProgress(mediaId: String, progress: Int) = Unit
    override suspend fun markCompleted(mediaId: String, filePath: String, fileSize: Long) = Unit
    override suspend fun markFailed(mediaId: String) = Unit
    override suspend fun removeDownload(mediaId: String) = Unit
    override suspend fun removeAllDownloads() = Unit
    override suspend fun resetStaleDownloads() = Unit
}
