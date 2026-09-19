package com.example.nyasaplayer.core.data.fake

import com.example.nyasaplayer.core.data.local.dao.DownloadDao
import com.example.nyasaplayer.core.data.local.entity.DownloadEntity
import com.example.nyasaplayer.core.data.local.entity.DownloadStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.concurrent.atomic.AtomicInteger

/**
 * A [DownloadDao] whose one-shot startup query can be held open.
 *
 * `OfflineDownloadRepository` fills its path cache from [getAllCompletedOnce] in a coroutine it
 * launches from `init`, so a test that wants to look at the repository *while that query is still
 * running* needs the query to stop and wait. [completedLoadStarted] says it has been asked;
 * [releaseCompletedLoad] lets it answer.
 *
 * [rows] are in the database from the start — that is what makes the wait meaningful. A fake that
 * begins empty answers null whether or not the load has landed, so a test built on one proves
 * nothing about the race.
 *
 * With [gated] false the query answers straight away, for tests that care about the result rather
 * than the timing. [failuresBeforeSuccess] makes the first calls throw, which is how a load that
 * failed and has to be retried is tested.
 */
class FakeDownloadDao(
    rows: List<DownloadEntity> = emptyList(),
    private val gated: Boolean = true,
    failuresBeforeSuccess: Int = 0,
) : DownloadDao {

    private val remainingFailures = AtomicInteger(failuresBeforeSuccess)

    private val downloads = MutableStateFlow(rows)

    /** Completes when [getAllCompletedOnce] has been called. */
    val completedLoadStarted = CompletableDeferred<Unit>()

    private val completedLoadGate = CompletableDeferred<List<DownloadEntity>>()

    /** Lets the waiting [getAllCompletedOnce] answer with the completed rows it holds. */
    fun releaseCompletedLoad() {
        completedLoadGate.complete(completedRows())
    }

    override suspend fun getAllCompletedOnce(): List<DownloadEntity> {
        completedLoadStarted.complete(Unit)
        check(remainingFailures.getAndDecrement() <= 0) { "database unavailable" }
        return if (gated) completedLoadGate.await() else completedRows()
    }

    private fun completedRows() = downloads.value.filter { it.status == DownloadStatus.Completed }

    override fun getCompleted(): Flow<List<DownloadEntity>> =
        downloads.map { rows -> rows.filter { it.status == DownloadStatus.Completed } }

    override fun getAll(): Flow<List<DownloadEntity>> = downloads

    override suspend fun getByMediaId(mediaId: String): DownloadEntity? =
        downloads.value.firstOrNull { it.mediaId == mediaId }

    override fun observeByMediaId(mediaId: String): Flow<DownloadEntity?> =
        downloads.map { rows -> rows.firstOrNull { it.mediaId == mediaId } }

    override fun observeByMediaIds(mediaIds: List<String>): Flow<List<DownloadEntity>> =
        downloads.map { rows -> rows.filter { it.mediaId in mediaIds.toSet() } }

    override fun getDownloadedMediaIds(): Flow<List<String>> =
        downloads.map { rows -> rows.filter { it.status == DownloadStatus.Completed }.map { it.mediaId } }

    override fun getDownloadedCount(): Flow<Int> =
        downloads.map { rows -> rows.count { it.status == DownloadStatus.Completed } }

    override fun getTotalDownloadedSize(): Flow<Long> =
        downloads.map { rows -> rows.sumOf { it.fileSizeBytes } }

    override suspend fun upsert(download: DownloadEntity) {
        downloads.value = downloads.value.filterNot { it.mediaId == download.mediaId } + download
    }

    override suspend fun updateProgress(mediaId: String, status: DownloadStatus, progress: Int) {
        downloads.value = downloads.value.map { row ->
            if (row.mediaId == mediaId) row.copy(status = status, progress = progress) else row
        }
    }

    override suspend fun markCompleted(mediaId: String, filePath: String, fileSize: Long, downloadedAt: Long) {
        downloads.value = downloads.value.map { row ->
            if (row.mediaId == mediaId) {
                row.copy(
                    status = DownloadStatus.Completed,
                    filePath = filePath,
                    fileSizeBytes = fileSize,
                    downloadedAt = downloadedAt,
                )
            } else {
                row
            }
        }
    }

    override suspend fun resetStaleDownloads() {
        downloads.value = downloads.value.map { row ->
            if (row.status == DownloadStatus.Pending || row.status == DownloadStatus.Downloading) {
                row.copy(status = DownloadStatus.Failed)
            } else {
                row
            }
        }
    }

    override suspend fun delete(mediaId: String) {
        downloads.value = downloads.value.filterNot { it.mediaId == mediaId }
    }

    override suspend fun deleteAll() {
        downloads.value = emptyList()
    }
}
