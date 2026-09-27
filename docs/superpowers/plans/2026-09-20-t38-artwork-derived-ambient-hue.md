# T38 - Artwork-derived AAOS ambient hue: Implementation Plan

> **For agentic workers:** implement task-by-task and tick the checkboxes as work lands. This plan
> is the implementation authority for `docs/tickets/T38-artwork-derived-ambient-hue.md`.

> **Implemented 2026-09-27.** Tasks 1–3 landed in #104, Task 4 in #105, Tasks 5–7 in #106. The
> checkboxes below were not ticked as work landed, so they are left as written rather than ticked
> after the fact; the ticket's *Outcome* section records what shipped and where it departs from
> this plan.

**Goal:** While parked, the AAOS ambient layer and full player glow derive their hue from the
current track's artwork, similar to YouTube Music / Spotify, without violating the AAOS contrast
or decorative-motion gates. While driving, the last safe artwork theme is held.

**Architecture:** Add a small `:automotive` artwork-theme pipeline:
`currentSong.resolvedCoverUrl -> Coil software bitmap -> Android Palette -> conditioned ARGB theme
-> AutomotiveUiState -> CarAmbientBackground / CarFullPlayerScreen`.

**Key decisions:** D-T38.1 through D-T38.6 in
`docs/tickets/T38-artwork-derived-ambient-hue.md`.

**Reference research:**
- YouTube ambient mode samples visual source material, then stretches, blurs and scrims it:
  https://blog.youtube/inside-youtube/youtube-ambient-color-mode-visual-language-redesign/
- YouTube Music's Android Now Playing gradient is artwork-derived, muted and darkened:
  https://9to5google.com/2023/12/12/youtube-music-now-playing-gradient/
- Spotify recommends Android Palette for playback backgrounds, with `#191414` fallback:
  https://developer.spotify.com/documentation/design
- Android Palette must be generated from a bitmap and should be cached/off-main:
  https://developer.android.com/develop/ui/views/graphics/palette-colors
- Coil's Palette recipe requires `allowHardware(false)`:
  https://coil-kt.github.io/coil/recipes/

## Global Constraints

- Car-only. Do not touch the mobile `:app` `ExpandedPlayer` in this ticket.
- Do not put Compose `Color` in `AutomotiveUiState`; state carries ARGB ints.
- No raw album colour reaches UI. Every colour is conditioned before use.
- The rendered centre of each artwork-derived glow, after alpha over `NyasaBackground`, must be
  no lighter than `CarRaised`.
- Applied artwork theme changes only while `restrictions.isDistractionOptimized == false`.
- Track changes while driving hold the previous safe theme until parked.
- `ANIMATOR_DURATION_SCALE == 0` disables transitions; it does not forbid a parked theme update.
- Fallback for missing/failed/unsuitable artwork is the current shipping colours.
- Detekt `maxIssues: 0`; no magic numbers left inline.

## File Map

| File | Change |
|---|---|
| `gradle/libs.versions.toml` | add `androidx.palette:palette:1.0.0` |
| `automotive/build.gradle.kts` | add Palette dependency |
| `automotive/src/main/java/com/example/nyasaplayer/auto/AutomotiveApplication.kt` | implement `ImageLoaderFactory` |
| `automotive/src/main/java/com/example/nyasaplayer/auto/di/AutoAppModule.kt` | provide `ImageLoader` if extractor uses constructor injection |
| `automotive/src/main/java/com/example/nyasaplayer/auto/artwork/ArtworkTheme.kt` | create theme model, defaults, alpha constants, luminance conditioning |
| `automotive/src/main/java/com/example/nyasaplayer/auto/artwork/ArtworkThemeExtractor.kt` | create Coil + Palette extractor |
| `automotive/src/main/java/com/example/nyasaplayer/auto/viewmodel/AutomotivePlayerViewModel.kt` | extract/apply parked-only theme |
| `automotive/src/main/java/com/example/nyasaplayer/auto/ui/AutomotiveApp.kt` | pass theme to ambient/full player |
| `automotive/src/main/java/com/example/nyasaplayer/auto/ui/components/CarAmbientBackground.kt` | accept dynamic tints and animate only when allowed |
| `automotive/src/main/java/com/example/nyasaplayer/auto/ui/screens/CarFullPlayerScreen.kt` | accept dynamic glow and animate only when allowed |
| `automotive/src/test/java/com/example/nyasaplayer/auto/artwork/ArtworkThemeTest.kt` | create pure conditioning/selection tests |
| `automotive/src/test/java/com/example/nyasaplayer/auto/ui/CarUiCases.kt` | render bright artwork-theme cases |
| `automotive/src/test/java/com/example/nyasaplayer/auto/ui/CarTextContrastMeasurementTest.kt` | measure artwork-derived glow ceiling |
| `docs/aaos-DESIGN.md` | record conditioned artwork contrast row / D-T38 |
| `docs/tickets/T37-design-conformance-never-measured.md` | close finding 7 after code lands |
| `docs/tickets/T38-artwork-derived-ambient-hue.md` | final status update after verification |

---

## Task 1: Dependency and Image Loader

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `automotive/build.gradle.kts`
- Modify: `automotive/src/main/java/com/example/nyasaplayer/auto/AutomotiveApplication.kt`
- Modify as needed: `automotive/src/main/java/com/example/nyasaplayer/auto/di/AutoAppModule.kt`

- [ ] Add `palette = "1.0.0"` and `androidx-palette = { module = "androidx.palette:palette", version.ref = "palette" }`.
- [ ] Add `implementation(libs.androidx.palette)` to `:automotive`.
- [ ] Make `AutomotiveApplication` implement `coil.ImageLoaderFactory`, matching mobile's
  `respectCacheHeaders(false)`, disk cache directory and `maxSizePercent(0.10)`.
- [ ] Provide an `ImageLoader` through Hilt only if the extractor constructor needs it; prefer
  one shared loader rather than creating ad hoc loaders per extraction.
- [ ] Run `./gradlew :automotive:testDebugUnitTest --tests '*DecorativeMotionTest*'`.

**Acceptance:** project compiles far enough for `:automotive` unit-test configuration to resolve
Palette and Coil symbols.

---

## Task 2: Safe Artwork Theme Model

**Files:**
- Create: `automotive/src/main/java/com/example/nyasaplayer/auto/artwork/ArtworkTheme.kt`
- Test: `automotive/src/test/java/com/example/nyasaplayer/auto/artwork/ArtworkThemeTest.kt`

**Interfaces:**
- `data class ArtworkTheme(@ColorInt val ambientPrimary: Int, @ColorInt val ambientSecondary: Int, @ColorInt val fullPlayerGlow: Int)`
- `object ArtworkThemeDefaults`
- `fun conditionArtworkColor(@ColorInt seed: Int, alpha: Int, @ColorInt background: Int, @ColorInt ceiling: Int): Int`

- [ ] Write failing tests first:
  - default theme equals current `CarAmbientBlue`, `CarAmbientPurple`, and `NyasaGoldDim @ 0.15f`.
  - white/yellow/cyan seeds are conditioned so their composited luminance over `NyasaBackground`
    is `<= CarRaised`.
  - dark/saturated seeds keep enough hue identity to differ from the default fallback.
  - alpha bytes remain `0x64`, `0x3C`, and `0x26` for ambient primary, ambient secondary and full player.
- [ ] Implement WCAG relative luminance locally in the test/helper; do not depend on Compose tests.
- [ ] Implement conditioning as binary-search blending of the opaque seed toward `NyasaBackground`
  until `composite(seedWithAlpha, NyasaBackground) <= CarRaised`.
- [ ] Keep functions pure over ints so they can be tested without Robolectric rendering.

**Acceptance:** `./gradlew :automotive:testDebugUnitTest --tests '*ArtworkThemeTest*'` passes.

---

## Task 3: Palette Extraction

**Files:**
- Create: `automotive/src/main/java/com/example/nyasaplayer/auto/artwork/ArtworkThemeExtractor.kt`
- Test: `automotive/src/test/java/com/example/nyasaplayer/auto/artwork/ArtworkThemeExtractorTest.kt` if practical; otherwise cover selection through `ArtworkThemeTest`.

**Interfaces:**
- `interface ArtworkThemeExtractor { suspend fun themeFor(song: Song?): ArtworkTheme }`
- `class DefaultArtworkThemeExtractor @Inject constructor(...) : ArtworkThemeExtractor`

- [ ] For null or blank `resolvedCoverUrl`, return `ArtworkThemeDefaults.theme`.
- [ ] Execute a Coil request off-main with:
  - `data(song.resolvedCoverUrl)`
  - a small fixed decode size suitable for palette extraction, not full artwork display
  - `allowHardware(false)`
- [ ] Convert the successful drawable/image to a bitmap for Palette.
- [ ] Cache results by `resolvedCoverUrl`; a second request for the same URL must not decode again.
- [ ] Primary seed order: `vibrant -> muted -> darkVibrant -> darkMuted -> dominant -> default`.
- [ ] Secondary seed order: `muted -> darkMuted -> darkVibrant -> vibrant -> dominant -> default`.
- [ ] Run every seed through Task 2's conditioning before constructing `ArtworkTheme`.
- [ ] On load/extraction failure, return default without surfacing an error overlay.

**Acceptance:** extraction cannot run Palette on the main thread, cannot request hardware bitmaps,
and never throws for bad/missing artwork.

---

## Task 4: Parked-only ViewModel Application

**Files:**
- Modify: `automotive/src/main/java/com/example/nyasaplayer/auto/viewmodel/AutomotivePlayerViewModel.kt`
- Add test if constructor seam is practical: `automotive/src/test/java/com/example/nyasaplayer/auto/viewmodel/ArtworkThemeApplicationTest.kt`

- [ ] Add `artworkTheme: ArtworkTheme = ArtworkThemeDefaults.theme` to `AutomotiveUiState`.
- [ ] Inject `ArtworkThemeExtractor`.
- [ ] On current song URL/media-id change, launch one extraction job and cancel the previous one.
- [ ] If extraction returns while parked and still matches the current song, apply it to `_uiState`.
- [ ] If extraction returns while driving, store it as pending but do not update `_uiState.artworkTheme`.
- [ ] When restrictions change from driving to parked, apply the pending theme for the current song.
- [ ] If the song becomes null while parked, apply the default theme. If it becomes null while driving,
  hold the last applied theme.
- [ ] Make stale extraction results no-op by checking the current song key before applying.

**Acceptance:** track changes while driving do not change the applied theme; returning to parked
applies the latest current-song theme.

---

## Task 5: Wire Dynamic Tints Into UI

**Files:**
- Modify: `automotive/src/main/java/com/example/nyasaplayer/auto/ui/AutomotiveApp.kt`
- Modify: `automotive/src/main/java/com/example/nyasaplayer/auto/ui/components/CarAmbientBackground.kt`
- Modify: `automotive/src/main/java/com/example/nyasaplayer/auto/ui/screens/CarFullPlayerScreen.kt`
- Modify: `automotive/src/main/java/com/example/nyasaplayer/auto/ui/preview/CarScreenPreviews.kt`
- Modify: `automotive/src/test/java/com/example/nyasaplayer/auto/ui/CarUiCases.kt`

- [ ] Add `primaryTint` and `secondaryTint` parameters to `CarAmbientBackground`, defaulting to
  `CarAmbientBlue` and `CarAmbientPurple`.
- [ ] Add an artwork glow parameter to `CarFullPlayerScreen`, defaulting to the current
  `NyasaGoldDim.copy(alpha = 0.15f)`.
- [ ] In `AutomotiveApp`, convert the ARGB theme ints to Compose `Color` and pass them to both surfaces.
- [ ] Animate colour changes only when `motionEnabled` is true. When false, render the target colour
  directly. Do not animate while driving or while animator scale is zero.
- [ ] Keep all text/control colours unchanged.
- [ ] Update previews and `CarUiCases` call sites with defaults or explicit bright-theme cases.
- [ ] Update `CarAmbientBackground`'s KDoc so it no longer says artwork-following is deferred.

**Acceptance:** dynamic colour is visible on parked full player and root ambient surfaces, with no
API break in existing previews/tests.

---

## Task 6: Contrast and Motion Verification

**Files:**
- Modify: `automotive/src/test/java/com/example/nyasaplayer/auto/ui/CarTextContrastMeasurementTest.kt`
- Modify: `automotive/src/test/java/com/example/nyasaplayer/auto/ui/CarUiCases.kt`
- Add or extend motion test if needed: `automotive/src/test/java/com/example/nyasaplayer/auto/ui/motion/DecorativeMotionTest.kt`

- [ ] Add one or more `CarUiCase` variants that use a deliberately bright artwork theme, including
  `CarFullPlayerScreen`.
- [ ] Extend the ambient ceiling test so it measures both default and bright-conditioned artwork themes.
- [ ] Confirm the brightest pixel behind the content slot is no lighter than `CarRaised`.
- [ ] Confirm the full player bright-theme render does not produce text contrast violations.
- [ ] Add a small test or documented assertion that colour animation is gated by `motionEnabled`.

**Acceptance:** `CarTextContrastMeasurementTest` fails with an unconditioned bright theme and passes
with the conditioned implementation.

---

## Task 7: Docs and Ticket Closeout

**Files:**
- Modify: `docs/aaos-DESIGN.md`
- Modify: `docs/tickets/T37-design-conformance-never-measured.md`
- Modify: `docs/tickets/T38-artwork-derived-ambient-hue.md`
- Optional create: `docs/T38_VERIFICATION.md`

- [ ] Add a dated D-T38 decision row/paragraph to `docs/aaos-DESIGN.md` covering:
  - Palette provides hue/mood
  - luminance clamp protects NFR-2
  - parked-only updates, driving hold
  - full player included
- [ ] Add a contrast row for the conditioned bright artwork theme.
- [ ] Mark T37 finding 7 closed with the PR/date once merged.
- [ ] Update T38 status from planning to implemented/verified after the verification command passes.
- [ ] Record device/emulator notes if a parked/driving manual pass is run.

**Acceptance:** docs no longer say artwork-following is deferred, and the ticket points to the
passing verification evidence.

## Verification Command

Run before closeout:

```bash
./gradlew :automotive:testDebugUnitTest detekt :automotive:lintDebug
```

If the unrelated mobile `ExpandedPlayer.kt` Notion paste remains in the worktree, either fix it in
its own change or avoid verification commands that compile `:app`.
