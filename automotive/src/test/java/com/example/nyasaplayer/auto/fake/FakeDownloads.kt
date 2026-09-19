package com.example.nyasaplayer.auto.fake

import com.example.nyasaplayer.core.data.api.DownloadRepository
import com.example.nyasaplayer.core.data.download.SongDownloads
import com.example.nyasaplayer.core.data.local.entity.DownloadEntity
import com.example.nyasaplayer.core.data.local.entity.DownloadStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Download bookkeeping, in memory.
 *
 * [rows] is what the DAO would emit, so a test sets the state it wants and the ViewModel reads it
 * through the same path production does.
 */
class FakeDownloadRepository : DownloadRepository {
    val rows = MutableStateFlow<List<DownloadEntity>>(emptyList())

    /** The local path a completed download reports; absent means the file is not on disk. */
    val paths = mutableMapOf<String, String>()

    override fun getAllDownloads(): Flow<List<DownloadEntity>> = rows

    override fun getCompletedDownloads(): Flow<List<DownloadEntity>> =
        rows.map { list -> list.filter { it.status == DownloadStatus.Completed } }

    override fun observeDownload(mediaId: String): Flow<DownloadEntity?> =
        rows.map { list -> list.firstOrNull { it.mediaId == mediaId } }

    override fun observeDownloads(mediaIds: List<String>): Flow<List<DownloadEntity>> =
        rows.map { list -> list.filter { it.mediaId in mediaIds } }

    override fun getDownloadedMediaIds(): Flow<List<String>> =
        rows.map { list -> list.filter { it.status == DownloadStatus.Completed }.map { it.mediaId } }

    override fun getDownloadedCount(): Flow<Int> =
        rows.map { list -> list.count { it.status == DownloadStatus.Completed } }

    override fun getTotalDownloadedSize(): Flow<Long> =
        rows.map { list -> list.filter { it.status == DownloadStatus.Completed }.sumOf { it.fileSizeBytes } }

    override suspend fun getDownload(mediaId: String): DownloadEntity? =
        rows.value.firstOrNull { it.mediaId == mediaId }

    override fun getLocalFilePath(mediaId: String): String? = paths[mediaId]

    override suspend fun awaitDownloadIndex() = Unit

    override suspend fun addDownload(mediaId: String) {
        rows.value = rows.value + DownloadEntity(mediaId = mediaId)
    }

    override suspend fun updateProgress(mediaId: String, progress: Int) {
        rows.value = rows.value.map {
            if (it.mediaId == mediaId) it.copy(status = DownloadStatus.Downloading, progress = progress) else it
        }
    }

    override suspend fun markCompleted(mediaId: String, filePath: String, fileSize: Long) {
        rows.value = rows.value.map {
            if (it.mediaId == mediaId) {
                it.copy(status = DownloadStatus.Completed, filePath = filePath, fileSizeBytes = fileSize)
            } else {
                it
            }
        }
    }

    override suspend fun markFailed(mediaId: String) {
        rows.value = rows.value.map {
            if (it.mediaId == mediaId) it.copy(status = DownloadStatus.Failed) else it
        }
    }

    override suspend fun removeDownload(mediaId: String) {
        rows.value = rows.value.filterNot { it.mediaId == mediaId }
    }

    override suspend fun removeAllDownloads() {
        rows.value = emptyList()
    }

    override suspend fun resetStaleDownloads() {
        rows.value = rows.value.map {
            if (it.status == DownloadStatus.Pending || it.status == DownloadStatus.Downloading) {
                it.copy(status = DownloadStatus.Failed)
            } else {
                it
            }
        }
    }
}

/** Records what the screen asked for, so a test can assert a mutation was — or was not — started. */
class FakeSongDownloads : SongDownloads {
    val started = mutableListOf<String>()
    val removed = mutableListOf<String>()
    val retried = mutableListOf<String>()
    var removedAll = 0
        private set

    override fun downloadSong(mediaId: String) {
        started += mediaId
    }

    override fun removeDownload(mediaId: String) {
        removed += mediaId
    }

    override fun removeAllDownloads() {
        removedAll++
    }

    override fun retryDownload(mediaId: String) {
        retried += mediaId
    }
}
