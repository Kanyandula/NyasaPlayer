package com.example.nyasaplayer.auto.artwork

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * T07 / D-T38.6: the car paints a new artwork theme only while parked. Driving holds whatever is
 * on screen; parking applies the current cover's theme.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppliedArtworkThemeTest {

    private val themeA = theme(0x10)
    private val themeB = theme(0x20)
    private val defaults = ArtworkThemeDefaults.theme

    private val cover = MutableStateFlow<String?>(null)
    private val driving = MutableStateFlow(false)

    /** Each cover's extraction finishes only when the test completes it. */
    private val pending = mutableMapOf<String, CompletableDeferred<ArtworkTheme>>()
    private val extractor = ArtworkThemeExtractor { url ->
        if (url == null) defaults else pending.getOrPut(url) { CompletableDeferred() }.await()
    }

    private fun finish(url: String, theme: ArtworkTheme) = pending.getValue(url).complete(theme)

    private fun TestScope.applied(): List<ArtworkTheme> {
        val out = mutableListOf<ArtworkTheme>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            appliedArtworkTheme(cover, driving, extractor).toList(out)
        }
        return out
    }

    @Test
    fun a_cover_extracted_while_parked_is_applied() = runTest {
        val applied = applied()

        cover.value = "a"
        finish("a", themeA)

        assertEquals(listOf(defaults, themeA), applied)
    }

    @Test
    fun a_track_change_while_driving_holds_the_theme_until_parked() = runTest {
        cover.value = "a"
        val applied = applied()
        finish("a", themeA)

        driving.value = true
        cover.value = "b"
        finish("b", themeB)

        assertEquals("nothing new while driving", listOf(themeA), applied)

        driving.value = false

        assertEquals(listOf(themeA, themeB), applied)
    }

    @Test
    fun an_extraction_still_running_when_parked_lands_once_it_finishes() = runTest {
        cover.value = "a"
        driving.value = true
        val applied = applied()

        driving.value = false
        finish("a", themeA)

        assertEquals(listOf(themeA), applied)
    }

    @Test
    fun a_newer_cover_cancels_a_stale_extraction() = runTest {
        val applied = applied()

        cover.value = "a"
        cover.value = "b"
        finish("b", themeB)
        finish("a", themeA)

        assertEquals("a's result never lands", listOf(defaults, themeB), applied)
    }

    @Test
    fun the_song_ending_resets_when_parked_but_holds_while_driving() = runTest {
        cover.value = "a"
        val applied = applied()
        finish("a", themeA)

        driving.value = true
        cover.value = null

        assertEquals(listOf(themeA), applied)

        driving.value = false

        assertEquals(listOf(themeA, defaults), applied)
    }

    @Test
    fun the_same_cover_on_the_next_track_is_not_extracted_again() = runTest {
        var extractions = 0
        val counting = ArtworkThemeExtractor { extractions++; themeA }
        val out = mutableListOf<ArtworkTheme>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            appliedArtworkTheme(cover, driving, counting).toList(out)
        }

        cover.value = "album"
        cover.value = "album"

        assertEquals(2, extractions) // the initial null, then the album once
    }

    private fun theme(seed: Int) = ArtworkTheme(seed, seed + 1, seed + 2)
}
