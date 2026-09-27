package com.example.nyasaplayer.auto.artwork

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.mapLatest

/**
 * The artwork theme to paint, from the current cover and whether the car is moving (D-T38.6).
 *
 * One extraction per cover, and a newer cover cancels an older one still running, so a stale
 * result can never land. While driving nothing is emitted: the theme already on screen holds —
 * through a track change, or the song ending — and the latest one lands the moment the car parks.
 * A cover of null (nothing playing) is the default theme, applied once parked.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun appliedArtworkTheme(
    coverUrls: Flow<String?>,
    isDriving: Flow<Boolean>,
    extractor: ArtworkThemeExtractor,
): Flow<ArtworkTheme> = combine(
    coverUrls.distinctUntilChanged().mapLatest(extractor::themeFor),
    isDriving.distinctUntilChanged(),
) { theme, driving -> theme.takeUnless { driving } }
    .filterNotNull()
    .distinctUntilChanged()
