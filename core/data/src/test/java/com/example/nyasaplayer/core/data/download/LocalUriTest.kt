package com.example.nyasaplayer.core.data.download

import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.data.api.DownloadRepository
import com.example.nyasaplayer.core.data.local.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Local-URI resolution, which both surfaces and the restore path share (A9).
 *
 * A file on disk is the point of the whole feature, so these use a real one rather than stubbing
 * the existence check away.
 */
class LocalUriTest {

    @get:Rule
    val downloadsDir = TemporaryFolder()

    private val repository = StubDownloadRepository()

    private fun song(id: String) = Song(
        mediaId = id,
        title = id.uppercase(),
        audioUrl = "https://cdn.example/$id.mp3",
        songUrl = "https://cdn.example/$id-legacy.mp3",
    )

    @Test
    fun `a song with no download record is returned untouched`() {
        val original = song("a")

        assertSame(original, repository.resolveLocalUri(original))
        assertNull(repository.localUriFor("a"))
    }

    @Test
    fun `a downloaded song points at its file on both url fields`() {
        val file = downloadsDir.newFile("a.audio")
        repository.paths["a"] = file.absolutePath

        val resolved = repository.resolveLocalUri(song("a"))

        val expected = file.toURI().toString()
        assertTrue(expected.startsWith("file:"))
        // Both, because resolvedAudioUrl falls back to songUrl and the offline rule reads the
        // resolved value: leaving songUrl on the stream is a song that looks unplayable offline.
        assertEquals(expected, resolved.audioUrl)
        assertEquals(expected, resolved.songUrl)
        assertEquals(expected, resolved.resolvedAudioUrl)
    }

    @Test
    fun `a recorded download whose file is gone falls back to the stream`() {
        repository.paths["a"] = downloadsDir.root.resolve("never-written.audio").absolutePath

        val resolved = repository.resolveLocalUri(song("a"))

        assertNull(repository.localUriFor("a"))
        assertEquals("https://cdn.example/a.mp3", resolved.audioUrl)
        assertEquals("https://cdn.example/a-legacy.mp3", resolved.songUrl)
    }

    @Test
    fun `resolution leaves everything but the urls alone`() {
        val file = downloadsDir.newFile("a.audio")
        repository.paths["a"] = file.absolutePath

        val original = song("a").copy(artistName = "Jimi Hendrix", durationMs = 170_000L)
        val resolved = repository.resolveLocalUri(original)

        assertEquals(original.mediaId, resolved.mediaId)
        assertEquals(original.title, resolved.title)
        assertEquals("Jimi Hendrix", resolved.artistName)
        assertEquals(170_000L, resolved.durationMs)
    }
}

/** Only [getLocalFilePath] is consulted by the resolver; nothing else is reached. */
private class StubDownloadRepository : DownloadRepository {
    val paths = mutableMapOf<String, String>()

    override fun getLocalFilePath(mediaId: String): String? = paths[mediaId]

    override fun getAllDownloads(): Flow<List<DownloadEntity>> = flowOf(emptyList())
    override fun getCompletedDownloads(): Flow<List<DownloadEntity>> = flowOf(emptyList())
    override fun observeDownload(mediaId: String): Flow<DownloadEntity?> = flowOf(null)
    override fun observeDownloads(mediaIds: List<String>): Flow<List<DownloadEntity>> = flowOf(emptyList())
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
