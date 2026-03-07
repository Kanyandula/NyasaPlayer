package com.example.nyasaplayer.core.data.local

import androidx.room.TypeConverter
import com.example.nyasaplayer.core.data.local.entity.DownloadStatus

class DownloadStatusConverter {
    @TypeConverter
    fun fromDownloadStatus(status: DownloadStatus): String = status.name

    @TypeConverter
    fun toDownloadStatus(value: String): DownloadStatus =
        try {
            DownloadStatus.valueOf(value)
        } catch (_: IllegalArgumentException) {
            DownloadStatus.Pending
        }
}
