package com.example.nyasaplayer.auto.artwork

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CancellationException

private const val TAG = "ArtworkTheme"

/** Palette only needs a hue: a thumbnail is plenty, and far cheaper than the displayed cover. */
private const val SampleSizePx = 128
private const val CacheEntries = 64

/** The car's theme for a cover. A seam so the player ViewModel can be tested without Coil (T07). */
fun interface ArtworkThemeExtractor {
    suspend fun themeFor(coverUrl: String?): ArtworkTheme
}

/**
 * Loads a small software copy of the cover through the app's shared [ImageLoader] and derives the
 * theme from its Palette. Never throws for bad or missing artwork: anything short of a usable cover
 * is [ArtworkThemeDefaults].
 */
class PaletteArtworkThemeExtractor(
    private val context: Context,
    private val imageLoader: ImageLoader,
    private val paletteDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ArtworkThemeExtractor {

    // ponytail: in-memory only. A process restart re-derives, which costs one thumbnail decode.
    private val cache = LruCache<String, ArtworkTheme>(CacheEntries)

    override suspend fun themeFor(coverUrl: String?): ArtworkTheme {
        if (coverUrl.isNullOrBlank()) return ArtworkThemeDefaults.theme
        cache.get(coverUrl)?.let { return it }
        // Failures are not cached, so a cover that failed offline is tried again once back online.
        val bitmap = loadSample(coverUrl) ?: return ArtworkThemeDefaults.theme
        return withContext(paletteDispatcher) { artworkThemeFrom(Palette.from(bitmap).generate().swatches()) }
            .also { cache.put(coverUrl, it) }
    }

    @Suppress("TooGenericExceptionCaught") // a cover that fails for any reason is the default theme
    private suspend fun loadSample(coverUrl: String): Bitmap? = try {
        val request = ImageRequest.Builder(context)
            .data(coverUrl)
            .size(SampleSizePx)
            // Palette reads pixels, which a hardware bitmap does not allow.
            .allowHardware(false)
            .build()
        (imageLoader.execute(request) as? SuccessResult)?.drawable?.toBitmap()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Artwork sample failed for $coverUrl", e)
        null
    }
}

private fun Palette.swatches() = ArtworkSwatches(
    vibrant = vibrantSwatch?.rgb,
    muted = mutedSwatch?.rgb,
    darkVibrant = darkVibrantSwatch?.rgb,
    darkMuted = darkMutedSwatch?.rgb,
    dominant = dominantSwatch?.rgb,
)
