package com.example.nyasaplayer.core.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.nyasaplayer.core.data.local.entity.DownloadEntity
import com.example.nyasaplayer.core.data.local.entity.DownloadStatus
import kotlinx.coroutines.flow.Flow

@Suppress("TooManyFunctions")
@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads WHERE status = 'Completed' ORDER BY downloaded_at DESC")
    fun getCompleted(): Flow<List<DownloadEntity>>

    /**
     * Every download whatever its status, active work first.
     *
     * The car's Downloads screen renders in-progress and failed rows, which [getCompleted] cannot
     * see. `downloaded_at` is 0 until a download completes, so ordering by it alone would bury the
     * row the driver is waiting on at the bottom of the list.
     */
    @Query(
        "SELECT * FROM downloads ORDER BY CASE status " +
            "WHEN 'Downloading' THEN 0 WHEN 'Pending' THEN 1 WHEN 'Failed' THEN 2 ELSE 3 END, " +
            "downloaded_at DESC",
    )
    fun getAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE media_id = :mediaId")
    suspend fun getByMediaId(mediaId: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE media_id = :mediaId")
    fun observeByMediaId(mediaId: String): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads WHERE media_id IN (:mediaIds)")
    fun observeByMediaIds(mediaIds: List<String>): Flow<List<DownloadEntity>>

    @Query("SELECT media_id FROM downloads WHERE status = 'Completed'")
    fun getDownloadedMediaIds(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM downloads WHERE status = 'Completed'")
    fun getDownloadedCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(file_size_bytes), 0) FROM downloads WHERE status = 'Completed'")
    fun getTotalDownloadedSize(): Flow<Long>

    @Upsert
    suspend fun upsert(download: DownloadEntity)

    @Query("UPDATE downloads SET status = :status, progress = :progress WHERE media_id = :mediaId")
    suspend fun updateProgress(mediaId: String, status: DownloadStatus, progress: Int)

    @Query(
        "UPDATE downloads SET status = 'Completed', file_path = :filePath, " +
            "file_size_bytes = :fileSize, downloaded_at = :downloadedAt WHERE media_id = :mediaId",
    )
    suspend fun markCompleted(
        mediaId: String,
        filePath: String,
        fileSize: Long,
        downloadedAt: Long,
    )

    @Query("SELECT * FROM downloads WHERE status = 'Completed'")
    suspend fun getAllCompletedOnce(): List<DownloadEntity>

    @Query("UPDATE downloads SET status = 'Failed' WHERE status IN ('Pending', 'Downloading')")
    suspend fun resetStaleDownloads()

    @Query("DELETE FROM downloads WHERE media_id = :mediaId")
    suspend fun delete(mediaId: String)

    @Query("DELETE FROM downloads")
    suspend fun deleteAll()
}
