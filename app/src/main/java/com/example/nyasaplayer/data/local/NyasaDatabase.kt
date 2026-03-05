package com.example.nyasaplayer.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.nyasaplayer.data.local.dao.ArtistDao
import com.example.nyasaplayer.data.local.dao.GenreDao
import com.example.nyasaplayer.data.local.dao.SongDao
import com.example.nyasaplayer.data.local.entity.ArtistEntity
import com.example.nyasaplayer.data.local.entity.GenreEntity
import com.example.nyasaplayer.data.local.entity.SongEntity

@Database(
    entities = [SongEntity::class, ArtistEntity::class, GenreEntity::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(StringListConverter::class)
abstract class NyasaDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun artistDao(): ArtistDao
    abstract fun genreDao(): GenreDao
}
