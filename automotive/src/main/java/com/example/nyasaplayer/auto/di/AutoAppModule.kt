package com.example.nyasaplayer.auto.di

import android.content.Context
import coil.imageLoader
import com.example.nyasaplayer.auto.artwork.ArtworkThemeExtractor
import com.example.nyasaplayer.auto.artwork.PaletteArtworkThemeExtractor
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AutoAppModule {

    @Provides
    @Singleton
    fun provideApplicationContext(@ApplicationContext context: Context): Context = context

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    /** Singleton so its cover cache outlives any one ViewModel; shares the app's image loader. */
    @Provides
    @Singleton
    fun provideArtworkThemeExtractor(@ApplicationContext context: Context): ArtworkThemeExtractor =
        PaletteArtworkThemeExtractor(context, context.imageLoader)
}
