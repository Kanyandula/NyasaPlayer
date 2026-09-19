package com.example.nyasaplayer.core.data.fake

import com.example.nyasaplayer.core.data.local.dao.DownloadDao
import com.example.nyasaplayer.core.data.local.entity.DownloadEntity
import com.example.nyasaplayer.core.data.local.entity.DownloadStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * A [DownloadDao] whose one-shot startup query can be held open.
 *
 * `OfflineDownloadRepository` fills its path cache from [getAllCompletedOnce] in a coroutine it
 * launches from `init`, so a test that wants to look at the repository *while that query is still
 * running* needs the query to stop and wait. [completedLoadStarted] says it has been asked;
 * [releaseCompletedLoad] lets it answer.
 */
class FakeDownloadDao : DownloadDao {

    private val downloads = MutableStateFlow<List<DownloadEntity>>(emptyList())

    /** Completes when [getAllCompletedOnce] has been called. */
    val completedLoadStarted = CompletableDeferred<Unit>()

    private val completedLoadGate = CompletableDeferred<List<DownloadEntity>>()

    /** Answers the waiting [getAllCompletedOnce] with [rows]. */
    fun releaseCompletedLoad(rows: List<DownloadEntity>) {
        downloads.value = rows
        completedLoadGate.complete(rows)
    }

    override suspend fun getAllCompletedOnce(): List<DownloadEntity> {
        completedLoadStarted.complete(Unit)
        return completedLoadGate.await()
    }

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
