package com.example.nyasaplayer.core.data.offline

import android.util.Log
import com.example.nyasaplayer.core.data.api.DownloadRepository
import com.example.nyasaplayer.core.data.local.dao.DownloadDao
import com.example.nyasaplayer.core.data.local.entity.DownloadEntity
import com.example.nyasaplayer.core.data.local.entity.DownloadStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

// Mirrors DownloadRepository, which carries the same suppression: this class is the one
// implementation of that interface, so it is exactly as wide as the contract it fills.
@Suppress("TooManyFunctions")
@Singleton
class OfflineDownloadRepository @Inject constructor(
    private val downloadDao: DownloadDao,
) : DownloadRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val filePathCache = ConcurrentHashMap<String, String>()

    private val indexLock = Mutex()

    @Volatile
    private var indexLoaded = false

    init {
        // Warm it at startup so the common case never waits. Callers that cannot afford a stale
        // answer await it instead of racing it (T32).
        scope.launch {
            try {
                awaitDownloadIndex()
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // Logged rather than thrown: this scope has no handler, and a failed warm-up must
                // not take the process down. The next awaitDownloadIndex retries.
                Log.w(TAG, "Could not preload the download index", e)
            }
        }
    }

    override suspend fun awaitDownloadIndex() {
        if (indexLoaded) return
        indexLock.withLock {
            if (indexLoaded) return
            downloadDao.getAllCompletedOnce().forEach { entity ->
                if (entity.filePath.isNotBlank()) {
                    filePathCache[entity.mediaId] = entity.filePath
                }
            }
            indexLoaded = true
        }
    }

    override fun getCompletedDownloads(): Flow<List<DownloadEntity>> =
        downloadDao.getCompleted()

    override fun getAllDownloads(): Flow<List<DownloadEntity>> =
        downloadDao.getAll()

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
        filePathCache[mediaId] = filePath
    }

    override suspend fun markFailed(mediaId: String) {
        downloadDao.updateProgress(mediaId, DownloadStatus.Failed, 0)
    }

    override suspend fun removeDownload(mediaId: String) {
        filePathCache.remove(mediaId)
        downloadDao.delete(mediaId)
    }

    override suspend fun removeAllDownloads() {
        filePathCache.clear()
        downloadDao.deleteAll()
    }

    override suspend fun resetStaleDownloads() {
        downloadDao.resetStaleDownloads()
    }

    override fun getLocalFilePath(mediaId: String): String? =
        filePathCache[mediaId]

    private companion object {
        const val TAG = "OfflineDownloadRepo"
    }
}
