package com.example.nyasaplayer.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DownloadStatus {
    Pending,
    Downloading,
    Completed,
    Failed,
}

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey
    @ColumnInfo(name = "media_id")
    val mediaId: String,
    val status: DownloadStatus = DownloadStatus.Pending,
    val progress: Int = 0,
    @ColumnInfo(name = "file_path")
    val filePath: String = "",
    @ColumnInfo(name = "file_size_bytes")
    val fileSizeBytes: Long = 0L,
    @ColumnInfo(name = "downloaded_at")
    val downloadedAt: Long = 0L,
)
