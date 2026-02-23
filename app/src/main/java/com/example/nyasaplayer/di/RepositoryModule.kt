package com.example.nyasaplayer.di

import com.example.nyasaplayer.data.FirebaseArtistRepository
import com.example.nyasaplayer.data.FirebaseAuthRepository
import com.example.nyasaplayer.data.FirebaseGenreRepository
import com.example.nyasaplayer.data.FirebaseHomeFeedRepository
import com.example.nyasaplayer.data.FirebaseSongRepository
import com.example.nyasaplayer.data.FirebaseUserRepository
import com.example.nyasaplayer.data.api.ArtistRepository
import com.example.nyasaplayer.data.api.AuthRepository
import com.example.nyasaplayer.data.api.GenreRepository
import com.example.nyasaplayer.data.api.HomeFeedRepository
import com.example.nyasaplayer.data.api.SongRepository
import com.example.nyasaplayer.data.api.UserRepository
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
    abstract fun bindSongRepository(impl: FirebaseSongRepository): SongRepository

    @Binds
    @Singleton
    abstract fun bindArtistRepository(impl: FirebaseArtistRepository): ArtistRepository

    @Binds
    @Singleton
    abstract fun bindGenreRepository(impl: FirebaseGenreRepository): GenreRepository

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
