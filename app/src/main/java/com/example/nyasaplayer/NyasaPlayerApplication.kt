package com.example.nyasaplayer

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import com.example.nyasaplayer.data.sync.FirebaseSyncManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NyasaPlayerApplication : Application(), ImageLoaderFactory {

    @Inject
    lateinit var firebaseSyncManager: FirebaseSyncManager

    override fun onCreate() {
        super.onCreate()
        firebaseSyncManager.start()
    }

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
        const val IMAGE_CACHE_MAX_SIZE_PERCENT = 0.05
    }
}
