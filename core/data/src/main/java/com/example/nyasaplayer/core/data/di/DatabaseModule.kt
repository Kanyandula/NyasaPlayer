package com.example.nyasaplayer.core.data.di

import android.content.Context
import androidx.room.Room
import com.example.nyasaplayer.core.data.local.NyasaDatabase
import com.example.nyasaplayer.core.data.local.dao.ArtistDao
import com.example.nyasaplayer.core.data.local.dao.DownloadDao
import com.example.nyasaplayer.core.data.local.dao.GenreDao
import com.example.nyasaplayer.core.data.local.dao.SongDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NyasaDatabase =
        Room.databaseBuilder(context, NyasaDatabase::class.java, "nyasa_player.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideSongDao(database: NyasaDatabase): SongDao = database.songDao()

    @Provides
    @Singleton
    fun provideArtistDao(database: NyasaDatabase): ArtistDao = database.artistDao()

    @Provides
    @Singleton
    fun provideGenreDao(database: NyasaDatabase): GenreDao = database.genreDao()

    @Provides
    @Singleton
    fun provideDownloadDao(database: NyasaDatabase): DownloadDao = database.downloadDao()
}
