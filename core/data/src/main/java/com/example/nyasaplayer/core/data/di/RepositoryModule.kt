package com.example.nyasaplayer.core.data.di

import com.example.nyasaplayer.core.data.FirebaseAuthRepository
import com.example.nyasaplayer.core.data.FirebaseHomeFeedRepository
import com.example.nyasaplayer.core.data.FirebaseUserRepository
import com.example.nyasaplayer.core.data.api.ArtistRepository
import com.example.nyasaplayer.core.data.api.AuthRepository
import com.example.nyasaplayer.core.data.api.GenreRepository
import com.example.nyasaplayer.core.data.api.HomeFeedRepository
import com.example.nyasaplayer.core.data.api.SongRepository
import com.example.nyasaplayer.core.data.api.UserRepository
import com.example.nyasaplayer.core.data.offline.OfflineArtistRepository
import com.example.nyasaplayer.core.data.offline.OfflineGenreRepository
import com.example.nyasaplayer.core.data.offline.OfflineSongRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSongRepository(impl: OfflineSongRepository): SongRepository

    @Binds
    @Singleton
    abstract fun bindArtistRepository(impl: OfflineArtistRepository): ArtistRepository

    @Binds
    @Singleton
    abstract fun bindGenreRepository(impl: OfflineGenreRepository): GenreRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: FirebaseAuthRepository): AuthRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(impl: FirebaseUserRepository): UserRepository

    @Binds
    @Singleton
    abstract fun bindHomeFeedRepository(impl: FirebaseHomeFeedRepository): HomeFeedRepository
}
