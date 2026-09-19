package com.example.nyasaplayer.core.data.offline

import com.example.nyasaplayer.core.data.fake.FakeDownloadDao
import com.example.nyasaplayer.core.data.local.entity.DownloadEntity
import com.example.nyasaplayer.core.data.local.entity.DownloadStatus
import kotlinx.coroutines.delay
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
 * Every case starts with the download **already in the database**. That is what makes the null in
 * the first test mean something: the row is there, the repository simply has not read it yet.
 *
 * `runBlocking`, not `runTest`: the repository loads on `Dispatchers.IO`, a real thread pool that
 * a virtual-time scheduler does not drive.
 */
class OfflineDownloadRepositoryTest {

    /**
     * **This pins the defect, not the desired behaviour.** The song is downloaded and recorded;
     * the repository says otherwise because its startup query has not come back. When T32 is
     * fixed — by waiting for the load, or by falling back to the DAO — this assertion becomes
     * `assertEquals(Path, …)`.
     */
    @Test
    fun getLocalFilePath_whileTheInitialLoadIsStillRunning_saysNotDownloaded() = runBlocking {
        val dao = FakeDownloadDao(listOf(completed(MediaId, Path)))

        val repo = OfflineDownloadRepository(dao)
        withTimeout(TimeoutMs) { dao.completedLoadStarted.await() }

        assertNull(
            "T32: the download row exists, but the cache has not been filled from it yet",
            repo.getLocalFilePath(MediaId),
        )
    }

    @Test
    fun getLocalFilePath_onceTheLoadLands_findsTheFile() = runBlocking {
        val dao = FakeDownloadDao(listOf(completed(MediaId, Path)))

        val repo = OfflineDownloadRepository(dao)
        withTimeout(TimeoutMs) { dao.completedLoadStarted.await() }
        dao.releaseCompletedLoad()

        assertEquals(Path, repo.awaitLocalFilePath(MediaId))
    }

    /**
     * A completed row whose path is blank is not cached — the guard in the load
     * (`if (entity.filePath.isNotBlank())`), which is the only branch in it.
     */
    @Test
    fun getLocalFilePath_completedRowWithNoPath_staysNull() = runBlocking {
        val dao = FakeDownloadDao(
            listOf(completed(MediaId, Path), completed(OtherMediaId, filePath = "")),
        )

        val repo = OfflineDownloadRepository(dao)
        withTimeout(TimeoutMs) { dao.completedLoadStarted.await() }
        dao.releaseCompletedLoad()
        repo.awaitLocalFilePath(MediaId)

        assertNull(repo.getLocalFilePath(OtherMediaId))
    }

    /** Polls the cache: the load completes on `Dispatchers.IO`, off this thread. */
    private suspend fun OfflineDownloadRepository.awaitLocalFilePath(mediaId: String): String? =
        withTimeout(TimeoutMs) {
            var path = getLocalFilePath(mediaId)
            while (path == null) {
                delay(PollMs)
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
        const val OtherMediaId = "170"
        const val Path = "/data/user/10/com.example.nyasaplayer/files/downloads/56.audio"
        const val TimeoutMs = 5_000L
        const val PollMs = 5L
    }
}
