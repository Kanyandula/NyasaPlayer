package com.example.nyasaplayer.auto.artwork

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import coil.ImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * T06: real covers through Coil and Palette, on the JVM.
 *
 * Coil runs part of every request on the main dispatcher, so a test that blocks the main thread
 * waiting for it deadlocks. [themeFor] asks from a background coroutine and pumps the main looper.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArtworkThemeExtractorTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val extractor = PaletteArtworkThemeExtractor(context, ImageLoader(context))

    private fun cover(color: Int): File = folder.newFile().apply {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun themeFor(url: String?): ArtworkTheme {
        val result = CoroutineScope(Dispatchers.IO).async { extractor.themeFor(url) }
        val deadline = System.currentTimeMillis() + TimeoutMs
        while (!result.isCompleted) {
            check(System.currentTimeMillis() < deadline) { "extraction did not finish" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(PollMs)
        }
        return result.getCompleted()
    }

    @Test
    fun a_red_cover_gives_a_red_theme() {
        val theme = themeFor(cover(Color.RED).toURI().toString())

        assertNotEquals(ArtworkThemeDefaults.theme, theme)
        val r = (theme.ambientPrimary shr 16) and 0xFF
        val b = theme.ambientPrimary and 0xFF
        assertTrue("r=$r b=$b", r > b)
    }

    @Test
    fun missing_or_unreadable_artwork_is_the_default_theme() {
        assertEquals(ArtworkThemeDefaults.theme, themeFor(null))
        assertEquals(ArtworkThemeDefaults.theme, themeFor(" "))
        assertEquals(ArtworkThemeDefaults.theme, themeFor(File(folder.root, "gone.png").toURI().toString()))
    }

    @Test
    fun a_cover_is_decoded_once_per_url() {
        val file = cover(Color.BLUE)
        val url = file.toURI().toString()
        val first = themeFor(url)

        // Gone from disk: only the cache can answer now.
        file.delete()

        assertNotEquals("precondition: a real theme, not the fallback", ArtworkThemeDefaults.theme, first)
        assertEquals(first, themeFor(url))
    }

    private companion object {
        const val TimeoutMs = 20_000L
        const val PollMs = 5L
    }
}
