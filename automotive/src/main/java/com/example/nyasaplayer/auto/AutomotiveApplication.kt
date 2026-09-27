package com.example.nyasaplayer.auto

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import com.example.nyasaplayer.core.data.api.AuthRepository
import com.example.nyasaplayer.core.data.crash.CrashReporter
import com.example.nyasaplayer.core.data.sync.CatalogSync
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AutomotiveApplication : Application(), ImageLoaderFactory {

    @Inject
    lateinit var crashReporter: CrashReporter

    @Inject
    lateinit var catalogSync: CatalogSync

    @Inject
    lateinit var authRepository: AuthRepository

    override fun onCreate() {
        super.onCreate()
        // Outside the sign-in gate: a signed-out car's crashes need the key too.
        crashReporter.start()
        if (authRepository.isAuthenticated) {
            catalogSync.start()
        }
    }

    /**
     * The mobile app's cache policy (`NyasaPlayerApplication`), so the artwork theme's samples and
     * the covers on screen share one disk cache instead of each fetching the cover (T38 / D-T38.2).
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .respectCacheHeaders(false)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(IMAGE_CACHE_MAX_SIZE_PERCENT)
                    .build()
            }
            .crossfade(true)
            .build()

    private companion object {
        const val IMAGE_CACHE_MAX_SIZE_PERCENT = 0.10
    }
}
