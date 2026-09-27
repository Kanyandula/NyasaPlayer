# T38 - The ambient gradient does not follow the artwork, and the PRD says it does

- **Slice:** AAOS motion / colour
- **Depends on:** nothing. Closes T37 finding 7.
- **Status:** Filed 2026-09-20. **Implemented 2026-09-27** in three PRs: #104 (theme model and
  extractor), #105 (parked-only application), #106 (UI, contrast and motion verification). What
  changed from this ticket's plan is under *Outcome* at the end. Implementation plan:
  `docs/superpowers/plans/2026-09-20-t38-artwork-derived-ambient-hue.md`.
- **Verification Command:** `./gradlew :automotive:testDebugUnitTest detekt :automotive:lintDebug`
  (run per PR as its `oem` variant: `:automotive:testOemDebugUnitTest`, `:automotive:lintOemDebug`)
- **Design Reference:** `docs/AAOS_PRD.md` §7.3 (motion), FR-2.8 and NFR-6 (decorative motion),
  NFR-2 and §13.1 (contrast), `docs/aaos-DESIGN.md` §Contrast, D4 in
  `docs/superpowers/specs/2026-08-08-aaos-chrome-home-design.md:369`.
  Notion copy of this ticket: https://app.notion.com/p/3e1728b1385d81b7878ed9f5917280d4
- **Risk Tags:** contrast regression, driver distraction, unshipped PRD requirement
- **Affected Modules:** `:automotive`

## Problem

PRD §7.3 is unambiguous:

> Ambient background gradients drift and **follow the current album artwork** while parked, and
> freeze while driving.

They do not. `CarAmbientBackground.kt:71-78` draws two fixed tints — `CarAmbientBlue` and
`CarAmbientPurple` — and the component says so itself at `:39-41`:

> The hue is fixed. Following the current artwork was deferred in A2 (D4); when it lands it
> changes the two colours below and nothing else.

D4 gave the reason: it needs `androidx.palette` (still absent from `gradle/libs.versions.toml`),
bitmap access out of Coil, off-main-thread extraction and fallbacks, and the design said the hue
*may* follow artwork, so it was optional by its own wording. T37 finding 7 records the resulting
contradiction as live drift. This ticket closes it.

This is not the only requirement in play. **FR-2.8 is a Must** — *"Decorative motion runs only
while parked, freezes in motion."* §7.3 says the gradient follows the artwork; FR-2.8 says how it
may behave while the vehicle moves. Both bind, and the second is why item 6 of Scope is not
optional polish.

### Why this is not a styling change

NFR-2 requires **≥7:1 for all non-disabled text on every surface it lands on**, and the ambient
layer is the surface that nearly broke it once already. `docs/aaos-DESIGN.md` §Contrast:

> The glow was the surface the flat-surface figures missed: at full strength it took secondary
> text to 5.5:1. Its tints' alphas (`CarAmbientBlue`, `CarAmbientPurple`) now cap each centre at
> no lighter than raised `#1E1E2A`, and the same test checks that cap.

That cap is enforced by `CarTextContrastMeasurementTest`'s
`ambient glow behind content is never lighter than CarRaised` (`:116-131`), which captures real
pixels across every case in `CarUiCases.kt`. The current tints are calibrated *constants*; an
artwork-driven hue makes the composited colour unknown at build time. Measured against the real
tokens:

| Surface | Luminance | `CarTextSecondary #ACACBC` on it |
|---|---|---|
| `CarRaised #1E1E2A` — the tested ceiling | 0.01372 | 7.36:1 ✅ |
| Break-even for NFR-2 | 0.01701 | 7.00:1 |
| A white/bright cover at the full player's `0.15f` alpha → `#313131` | 0.03071 | **5.81:1** ❌ |

A naive hue swap fails the gate on bright artwork. **The artwork supplies hue; the app must keep
supplying luminance.** That split is how the reference products do it too:

- YouTube ambient mode uses source imagery as light, but stretches, blurs and scrims thumbnail /
  storyboard assets rather than placing raw colour behind controls
  (https://blog.youtube/inside-youtube/youtube-ambient-color-mode-visual-language-redesign/).
- YouTube Music's Android Now Playing gradient is described as artwork-derived, muted, and darker
  toward the bottom so white controls stand out
  (https://9to5google.com/2023/12/12/youtube-music-now-playing-gradient/).
- Spotify's developer guidelines explicitly say playback views should extract artwork colour for
  the background with Android Palette, and fall back to `#191414` when extraction is unavailable
  (https://developer.spotify.com/documentation/design).

## Decisions recorded 2026-09-20

**D-T38.1 — Extraction is song-driven, not composable-driven.** The bitmap comes from the current
song's `resolvedCoverUrl`, loaded by an injectable `ArtworkThemeExtractor` in `:automotive`.
It is keyed by URL/media id and exposed as `AutomotiveUiState.artworkTheme`. The app-root
ambient layer does not depend on any `AsyncImage` being composed, so the theme remains available
when the full player overlay hides `BrowseShell`.

**D-T38.2 — The car gets its own Coil loader factory.** `AutomotiveApplication` implements
`ImageLoaderFactory`, matching the mobile app's cache policy closely enough that `AsyncImage`
and the extractor share disk cache. The extractor requests a small software bitmap with
`allowHardware(false)` because Palette needs pixel access.

**D-T38.3 — Palette chooses mood; conditioning chooses safety.** Palette selection prefers
useful, colourful swatches, but every chosen colour is conditioned before use. The conditioning
algorithm blends the opaque seed toward `NyasaBackground` until the colour, after the intended
alpha, composites no lighter than `CarRaised`. This keeps the NFR-2 ceiling testable and avoids
trying to infer contrast from the raw artwork.

**D-T38.4 — The two ambient tints come from two swatches, with a deterministic fallback.**
Primary tries `vibrant -> muted -> darkVibrant -> darkMuted -> dominant -> current blue`.
Secondary tries `muted -> darkMuted -> darkVibrant -> vibrant -> dominant -> current purple`.
If both resolve to the same seed, the secondary uses the same conditioned hue at the existing
lower alpha rather than inventing an arbitrary hue rotation. The two-circle depth stays mostly
from placement and alpha, not from uncontrolled colour math.

**D-T38.5 — Full player is in scope, with the same safety ceiling.** `CarFullPlayerScreen`
currently paints its own full-screen gold glow (`NyasaGoldDim @ 0.15f`) over an opaque
`NyasaBackground`; it must receive the same conditioned artwork theme as the app-root ambient
layer. Leaving it gold would make the most visible artwork surface the one that does not follow
artwork.

**D-T38.6 — Artwork themes update only while parked.** Extraction may finish at any time, but
the applied `artworkTheme` changes only when `restrictions.isDistractionOptimized` is false.
If the track changes while driving, the previous safe theme is held until the car returns to a
parked/non-distraction-optimised state. If animator duration scale is `0`, the colour may update
while parked but must not animate.

## Scope

1. Add `androidx.palette:palette:1.0.0` to `gradle/libs.versions.toml` and `:automotive`. Not
   `-ktx` — its only addition is a `get(target)` operator we do not use.
2. Add `ArtworkTheme` and `ArtworkThemeExtractor` under `:automotive`, keeping the public state
   as ARGB ints rather than Compose `Color` values.
3. Add `AutomotiveApplication` / Hilt image-loader plumbing so the extractor can execute Coil
   requests off-main with `allowHardware(false)`.
4. Extend `AutomotivePlayerViewModel` so the current song's theme is extracted, cached and applied
   only while parked. Hold the last applied theme while driving.
5. Drive `CarAmbientBackground`'s two tints from the applied theme, preserving their current
   alphas (`0x64` / `0x3C`) as the contrast calibration.
6. Drive `CarFullPlayerScreen`'s glow from the same theme, preserving its current `0.15f` alpha.
7. Gate colour transitions on `decorativeMotionEnabled(...)` (`ui/motion/DecorativeMotion.kt:30`),
   consistent with the layer's existing drift gating.
8. Extend `CarTextContrastMeasurementTest` so the existing `CarRaised` ceiling case runs with a
   deliberately bright/white test artwork — the case that measures 5.81:1 unconditioned.
9. Amend `CarAmbientBackground.kt:39-41`, `docs/aaos-DESIGN.md` and T37 so D4's deferral is closed.

## Out Of Scope

- **The OEM media template.** Its colours come from the host media app's own theme. Nothing here
  reaches it.
- **The mobile `:app` `ExpandedPlayer`** (`ExpandedPlayer.kt:114-127`). Same feature, no NFR-2
  gate and no driving state. Separate ticket.
- **`CarMiniPlayer`, `CarQueueScreen`, the nav rail.** They keep the neutral chrome.
- **A shared extraction helper in `:core:common`.** One or two call sites in one module. Hoist
  when a third appears.
- **A server-side colour field or Firestore backfill.** Alternative B remains valid, but this
  ticket implements client-side extraction first.

## Alternatives Considered

### Alternative B — precompute the colour server-side and sync it (Spotify's model)

Spotify does not extract on device. Its internal endpoint takes the artwork URL as a path
parameter and returns the colours with the lyrics payload:

```
https://spclient.wg.spotify.com/color-lyrics/v2/track/{track_id}/image/{url-encoded image_url}
```

Extraction runs server-side, which is why a track looks identical on phone, desktop and
Chromecast. No client ever runs a quantizer.

The same shape fits this app unusually well, because the catalogue already syncs
Firestore → Room via `FirebaseSyncManager`. A `dominantColor` field on the `songs` document,
computed once at artwork ingest, would delete most of the Scope above:

- no `androidx.palette` dependency (items 1 and 3 vanish)
- no `allowHardware(false)`, so displayed artwork stays a hardware bitmap (item 2 vanishes)
- no bitmap access, no off-main-thread extraction, no decode cost
- the old bitmap-source question dissolves entirely — there is no bitmap to source, so the
  app-root layer's lack of an artwork image stops mattering
- the colour becomes plain data, unit-testable without Robolectric or a real bitmap
- mobile and car get identical colours for free

The plumbing is small and the types cooperate. `Song` (`core/common/.../models/Song.kt:3-18`)
has all-default parameters, so a `dominantColor: Int = 0` is source-compatible and Firestore's
`toObject` tolerates the field being absent during rollout. `SongEntity` gains one column, which
means a `MIGRATION_4_5` alongside the existing `MIGRATION_3_4` (`DatabaseModule.kt:27`) — the
project already writes real migrations rather than falling back to destructive ones.

**Why it is the alternative and not the plan:**

- It moves the work out of `:automotive` and into `:core:data` plus a **Firestore backfill of the
  whole `songs` collection** — a different blast radius, and the backfill is the part that is not
  free. There is no ingest pipeline today; something has to compute these.
- Artwork changes need a recompute, and nothing currently watches for that.
- **It does not remove the conditioning work.** The luminance ceiling depends on which surface
  the wash lands on, which the server cannot know. The ambient/full-player mapping, the clamp,
  and the parked-only update rule remain under either approach. Those are the items NFR-2 and
  FR-2.8 actually turn on.

So Alternative B is a real simplification of the *extraction* half — and it is the only option
that deletes the bitmap-source problem — while doing nothing for the *conditioning* half, which
is the half that can fail the build. Worth taking if the catalogue ingest is going to grow a
processing step anyway; not worth building an ingest pipeline for one gradient.

### Alternative C — `material-color-utilities` instead of Palette

Google's own current stack is `QuantizerCelebi` (Wu partitioning → WSMeans refinement) followed
by `Score`, whose job is to *"remove colors that are unsuitable for a UI theme, and rank the rest
based on suitability."* That `Score` step is conceptually exactly what item 4 hand-rolls, and
Palette has no equivalent — it offers HSL-targeted swatches and a near-black/near-white filter,
nothing that reasons about theme suitability.

Rejected on packaging, not merit: there is no clean standalone Android artifact. It ships vendored
inside `com.google.android.material` under a restricted package. Pulling that in for one gradient
stop is a worse trade than Palette plus an explicit luminance clamp — and the clamp has to exist
regardless, because `Score` does not know about NFR-2 or `CarRaised`.

### Alternative D — sample and blur the artwork directly (YouTube ambient mode)

YouTube's ambient mode runs no palette extraction at all: it draws the current frame to a much
smaller canvas, blurs it, and places it behind the player. The downscale *is* the quantization.
Cheapest of the three techniques, and it would reuse the artwork bitmap with no new dependency.

Rejected for the car. It re-samples continuously and relies on smoothing to stay comfortable —
a continuously-updating background in the driver's periphery is the thing §7.3's freeze rule and
`decorativeMotionEnabled` exist to prevent. It is also the least controllable: a blur has no
luminance ceiling you can assert on.

## Acceptance Criteria

- Given a track with strongly coloured artwork, when it plays while parked, then the ambient
  gradient and full player glow tint toward that colour; a different track's artwork changes it.
- `CarTextContrastMeasurementTest` passes, **including its `CarRaised` ceiling case run against a
  deliberately bright/white artwork** — the case that measures 5.81:1 unconditioned.
- Artwork that is missing, fully black, or single-colour falls back to the current constants and
  the app renders exactly as it does today.
- The colour is present on every surface the ambient layer is, and the full player receives the
  same applied theme while it is open.
- Extraction runs off the main thread; no dropped frames on track change.
- Driving: no colour update or transition runs (FR-2.8); the previous safe theme is held. With
  `ANIMATOR_DURATION_SCALE` at 0 while parked, the theme may update but no transition animates
  (NFR-6).
- `./gradlew :automotive:testDebugUnitTest detekt :automotive:lintDebug` clean. Magic numbers
  extracted to `const val`.
- `docs/aaos-DESIGN.md` §Contrast gains a row for the conditioned artwork tint.
- `CarAmbientBackground.kt:39-41` no longer says artwork-following is deferred, and T37 finding 7
  is resolved with a date.

## Notes

**The first draft of this ticket read as ready for dev, and was not.** Scope item 2 said to take
the bitmap from an existing `AsyncImage` `onSuccess`. That was carried over from a phone sketch
targeting `ExpandedPlayer` and never re-checked after the scope moved to the app-root ambient
layer, which has no artwork image. D-T38.1 is the correction: extraction is driven by playback
state, not by whichever composable happens to show art.

**This is an unshipped PRD requirement, not new work.** PRD §7.3 has required artwork-following
gradients since it was written; A2 deferred it via D4 and T37 finding 7 recorded the gap. Worth
stating because the feature arrived in conversation as a phone idea — the phone version
(`ExpandedPlayer.kt:114-127`) is the one that is genuinely new, and it is out of scope here.

The contrast figures in this ticket were computed from the committed token values
(`AutomotiveColors.kt:18,27`, `Color.kt:23-24`) using the WCAG relative-luminance formula, not
measured from rendered pixels. `CarTextContrastMeasurementTest` measures pixels and is the
authority; if it disagrees with the table above, it is right. PRD §13.1 corroborates the binding
surface independently — secondary `#ACACBC` on raised `#1E1E2A` at 7.4:1, marked *"binding
surface"*, with the note that measuring against the base instead is *"precisely how the original
6.8:1 failure went unnoticed."* Same trap, same layer.

**The Notion copy of the PRD is stale.** Last edited 2026-08-20; it lists A5 as unmerged on a
branch, A6 blocked and A7/A8 not started, while the repo carries A9 verification and a ship
record. §7.3, FR-2.8, NFR-2, NFR-6 and §13.1 are identical in both, so nothing in this ticket
turns on the difference — but do not read phase status from Notion. Not this ticket's job to fix.

## Outcome (2026-09-27)

Shipped as AAOS Now Playing T06–T08: #104, #105, #106. Each PR description carries its
verification commands and results. Where it differs from the plan above:

- **The two ambient tints are conditioned as a stacked pair, not one by one** (`ArtworkTheme.kt`
  `artworkThemeFromSeeds`). Held to `CarRaised` each on its own, as D-T38.3 read, a white cover
  measured L 0.0144 against 0.0137 where the circles overlap at the drift's lowest frame. Stacking
  is the worst any window shape can do, so the pair holds on all of them; bright covers give a
  dimmer ambient glow for it. The full player's glow is one layer and keeps its own ceiling.
- **Hue changes ease in sRGB, not Oklab.** `animateColorAsState` interpolates in Oklab, and between
  two hues each at the ceiling an Oklab path peaks over it midway (red to blue). An sRGB path cannot,
  because the canvas composites in sRGB. `DecorativeColorTest` pins both halves.
- **The extractor is keyed on the cover URL** — `themeFor(coverUrl: String?)`, not
  `themeFor(song: Song?)` — so tracks that share a cover share one extraction, and the key matches
  the one Coil caches the on-screen cover under.
- **The car's image loader has no crossfade**, unlike mobile's (D-T38.2 said "matching closely"):
  the car never had one, and it would animate covers while driving.
- **Acceptance criteria.** Contrast, fallback, off-main extraction, parked-only application and the
  docs are covered by tests and the design doc. A parked track change was checked on the Play AVD by
  pixel sample. **The driving hold, the apply-on-park and animator scale 0 were not exercised on a
  device:** the Play AVD refuses driving-state injection, so they rest on `AppliedArtworkThemeTest`
  and `DecorativeColorTest` until a pass on the userdebug AVD. "No dropped frames on track change"
  was not measured.
- T37 finding 7's artwork half is closed; its screen cross-fade half is not part of this ticket.

