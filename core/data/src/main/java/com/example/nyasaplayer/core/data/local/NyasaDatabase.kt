package com.example.nyasaplayer.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.nyasaplayer.core.data.local.dao.AlbumDao
import com.example.nyasaplayer.core.data.local.dao.ArtistDao
import com.example.nyasaplayer.core.data.local.dao.DownloadDao
import com.example.nyasaplayer.core.data.local.dao.GenreDao
import com.example.nyasaplayer.core.data.local.dao.SongDao
import com.example.nyasaplayer.core.data.local.entity.AlbumEntity
import com.example.nyasaplayer.core.data.local.entity.ArtistEntity
import com.example.nyasaplayer.core.data.local.entity.DownloadEntity
import com.example.nyasaplayer.core.data.local.entity.GenreEntity
import com.example.nyasaplayer.core.data.local.entity.SongEntity

@Database(
    entities = [
        SongEntity::class,
        ArtistEntity::class,
        GenreEntity::class,
        DownloadEntity::class,
        AlbumEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(StringListConverter::class, DownloadStatusConverter::class)
abstract class NyasaDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun artistDao(): ArtistDao
    abstract fun genreDao(): GenreDao
    abstract fun downloadDao(): DownloadDao
    abstract fun albumDao(): AlbumDao
}
