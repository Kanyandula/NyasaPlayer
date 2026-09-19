package com.example.nyasaplayer.core.data.api

import com.example.nyasaplayer.core.data.local.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow

@Suppress("TooManyFunctions")
interface DownloadRepository {
    fun getCompletedDownloads(): Flow<List<DownloadEntity>>

    /** Every download whatever its status, active work first — the car's Downloads screen (A9). */
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    // Used by future per-song and batch download progress UI
    fun observeDownload(mediaId: String): Flow<DownloadEntity?>
    fun observeDownloads(mediaIds: List<String>): Flow<List<DownloadEntity>>
    fun getDownloadedMediaIds(): Flow<List<String>>
    fun getDownloadedCount(): Flow<Int>
    fun getTotalDownloadedSize(): Flow<Long>
    suspend fun getDownload(mediaId: String): DownloadEntity?
    suspend fun addDownload(mediaId: String)
    suspend fun updateProgress(mediaId: String, progress: Int)
    suspend fun markCompleted(mediaId: String, filePath: String, fileSize: Long)
    suspend fun markFailed(mediaId: String)
    suspend fun removeDownload(mediaId: String)
    suspend fun removeAllDownloads()
    suspend fun resetStaleDownloads()
    fun getLocalFilePath(mediaId: String): String?

    /**
     * Returns once the downloaded-path index is loaded, so [getLocalFilePath] can answer.
     *
     * The index is read from the database once and kept in memory. Callers that can wait — the
     * restore path and the media session's `onAddMediaItems`, both `suspend` — must call this
     * before resolving, or they risk asking during the window after process start when the index
     * is still loading and every song looks undownloaded (T32).
     *
     * Cheap and idempotent once loaded. A load that failed is retried on the next call.
     */
    suspend fun awaitDownloadIndex()
}
