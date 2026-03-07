package com.example.nyasaplayer.core.data.offline

import com.example.nyasaplayer.core.data.api.DownloadRepository
import com.example.nyasaplayer.core.data.local.dao.DownloadDao
import com.example.nyasaplayer.core.data.local.entity.DownloadEntity
import com.example.nyasaplayer.core.data.local.entity.DownloadStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineDownloadRepository @Inject constructor(
    private val downloadDao: DownloadDao,
) : DownloadRepository {

    override fun getCompletedDownloads(): Flow<List<DownloadEntity>> =
        downloadDao.getCompleted()

    override fun observeDownload(mediaId: String): Flow<DownloadEntity?> =
        downloadDao.observeByMediaId(mediaId)

    override fun observeDownloads(mediaIds: List<String>): Flow<List<DownloadEntity>> =
        downloadDao.observeByMediaIds(mediaIds)

    override fun getDownloadedMediaIds(): Flow<List<String>> =
        downloadDao.getDownloadedMediaIds()

    override fun getDownloadedCount(): Flow<Int> =
        downloadDao.getDownloadedCount()

    override fun getTotalDownloadedSize(): Flow<Long> =
        downloadDao.getTotalDownloadedSize()

    override suspend fun getDownload(mediaId: String): DownloadEntity? =
        downloadDao.getByMediaId(mediaId)

    override suspend fun addDownload(mediaId: String) {
        downloadDao.upsert(DownloadEntity(mediaId = mediaId))
    }

    override suspend fun updateProgress(mediaId: String, progress: Int) {
        downloadDao.updateProgress(mediaId, DownloadStatus.Downloading, progress)
    }

    override suspend fun markCompleted(mediaId: String, filePath: String, fileSize: Long) {
        downloadDao.markCompleted(
            mediaId = mediaId,
            filePath = filePath,
            fileSize = fileSize,
            downloadedAt = System.currentTimeMillis(),
        )
    }

    override suspend fun markFailed(mediaId: String) {
        downloadDao.updateProgress(mediaId, DownloadStatus.Failed, 0)
    }

    override suspend fun removeDownload(mediaId: String) {
        downloadDao.delete(mediaId)
    }

    override suspend fun removeAllDownloads() {
        downloadDao.deleteAll()
    }

    override fun getLocalFilePath(mediaId: String): String? =
        runBlocking {
            val download = downloadDao.getByMediaId(mediaId)
            if (download?.status == DownloadStatus.Completed) download.filePath else null
        }
}
