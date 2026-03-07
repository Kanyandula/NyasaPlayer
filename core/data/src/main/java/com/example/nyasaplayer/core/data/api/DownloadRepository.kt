package com.example.nyasaplayer.core.data.api

import com.example.nyasaplayer.core.data.local.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow

@Suppress("TooManyFunctions")
interface DownloadRepository {
    fun getCompletedDownloads(): Flow<List<DownloadEntity>>

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
}
