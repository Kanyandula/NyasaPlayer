package com.example.nyasaplayer.core.data.offline

import com.example.nyasaplayer.core.data.fake.FakeDownloadDao
import com.example.nyasaplayer.core.data.local.entity.DownloadEntity
import com.example.nyasaplayer.core.data.local.entity.DownloadStatus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * T32: the window between process start and the path cache being filled.
 *
 * `OfflineDownloadRepository` answers `getLocalFilePath` from an in-memory map that it fills from
 * a one-shot query launched in `init`. Nothing waits for that query, so a caller arriving first is
 * told the song is not downloaded. On a device the window is too narrow to hit by hand — a car
 * pass on 2026-09-19 failed to provoke it, including after a reboot — so this is the evidence.
 *
 * `runBlocking`, not `runTest`: the repository loads on `Dispatchers.IO`, a real thread pool that
 * a virtual-time scheduler does not drive.
 */
class OfflineDownloadRepositoryTest {

    /**
     * **This pins the defect, not the desired behaviour.** When T32 is fixed, `getLocalFilePath`
     * either waits for the load or answers from the DAO, and this assertion becomes
     * `assertEquals(Path, …)` — flipping it is the point.
     */
    @Test
    fun getLocalFilePath_whileTheInitialLoadIsStillRunning_saysNotDownloaded() = runBlocking {
        val dao = FakeDownloadDao()

        val repo = OfflineDownloadRepository(dao)
        withTimeout(TimeoutMs) { dao.completedLoadStarted.await() }

        assertNull(
            "T32: a downloaded song reads as undownloaded until the startup query lands",
            repo.getLocalFilePath(MediaId),
        )
    }

    @Test
    fun getLocalFilePath_onceTheLoadLands_findsTheFile() = runBlocking {
        val dao = FakeDownloadDao()

        val repo = OfflineDownloadRepository(dao)
        withTimeout(TimeoutMs) { dao.completedLoadStarted.await() }
        dao.releaseCompletedLoad(listOf(completed(MediaId, Path)))

        assertEquals(Path, repo.awaitLocalFilePath(MediaId))
    }

    /** A song with no download row stays null after the load, so the cache is not blanket-filling. */
    @Test
    fun getLocalFilePath_songWithNoDownload_staysNull() = runBlocking {
        val dao = FakeDownloadDao()

        val repo = OfflineDownloadRepository(dao)
        withTimeout(TimeoutMs) { dao.completedLoadStarted.await() }
        dao.releaseCompletedLoad(listOf(completed(MediaId, Path)))
        repo.awaitLocalFilePath(MediaId)

        assertNull(repo.getLocalFilePath("someone-else"))
    }

    /** Polls the cache: the load completes on `Dispatchers.IO`, off this thread. */
    private suspend fun OfflineDownloadRepository.awaitLocalFilePath(mediaId: String): String? =
        withTimeout(TimeoutMs) {
            var path = getLocalFilePath(mediaId)
            while (path == null) {
                kotlinx.coroutines.delay(PollMs)
                path = getLocalFilePath(mediaId)
            }
            path
        }

    private fun completed(mediaId: String, filePath: String) = DownloadEntity(
        mediaId = mediaId,
        status = DownloadStatus.Completed,
        filePath = filePath,
        fileSizeBytes = 1_024L,
        downloadedAt = 1L,
    )

    private companion object {
        const val MediaId = "56"
        const val Path = "/data/user/10/com.example.nyasaplayer/files/downloads/56.audio"
        const val TimeoutMs = 5_000L
        const val PollMs = 5L
    }
}
