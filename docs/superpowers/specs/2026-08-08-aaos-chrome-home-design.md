# AAOS Slice A2 — Chrome Contract, Home & Ambient Motion

> **Status:** Draft for review · **Date** 2026-08-08 · **Depends on:** A1 (merged, PR #14)
> **Design source:** `docs/aaos-DESIGN.md` §Layout, §Chrome, §Motion
> **Scope source:** `docs/AAOS_PRD.md` §9 (phase A2), `docs/AAOS_SCREEN_CONTRACT.md` screen 3

## 1. Context

A1 delivered the foundation: gold tokens, car surfaces and dimensions, the 76dp
touch-target primitive, five component primitives, the restriction layer with `gate()` and
eviction, and the `oem`/`playstore` flavor split. Nothing about the *shell* changed — the
seven existing screens still sit inside the same layout they had before, now painted gold.

A2 settles that shell. Every screen in A3–A8 composes inside it, so settling it late means
re-laying-out screens built against a moving target. That is the whole reason A2 is second.

### 1.1 What A2 is

1. The **chrome contract** — three regions rendered identically on every screen.
2. **CarHomeScreen** rebuilt against that chrome and the design's section list.
3. **Ambient motion** — the parked-only decorative layer, and the rule that freezes it.

### 1.2 What A2 is not

- Not the other screens. Browse/Library/Playlist/Album are A3; Favourites is A4;
  FullPlayer/Queue are A5. A2 only guarantees they have a correct shell to sit in.
- Not the *designed* Favourites screen — A4 owns that. A2 ships a minimal one so the
  rail never changes shape (D2).
- Not Project B. Mobile stays purple.

## 2. The structural change nobody has written down yet

**Navigation currently lives in the top bar. The design puts it in a left rail.**

Today `CarTopBar` renders the app wordmark, a centred row of three tabs
(`Home`/`Browse`/`Library`) and a clock, and it owns tab selection:

```kotlin
// automotive/ui/components/CarTopBar.kt — current
@Composable
fun CarTopBar(
    currentScreen: CarScreen,
    onSelectTab: (CarScreen) -> Unit,
    modifier: Modifier = Modifier,
)
```

The design has no tabs in the system bar at all. `docs/aaos-DESIGN.md` §Chrome specifies the
bar's right cluster as exactly seven items — search, settings, avatar, clock, wi-fi,
bluetooth, battery — and puts the four destinations in an 80dp left rail.

This is not a restyle. It moves ownership of navigation between two components and changes
`CarTopBar`'s public signature. It is the single largest source of risk in A2 and the reason
the shell must be settled before A3.

### 2.1 Target layout

`AuthenticatedApp` currently composes (via `BrowseShell`):

```
Column {
    CarTopBar(currentScreen, onSelectTab)
    OfflineBanner
    Box(weight 1f) { screen content }
    CarMiniPlayer
}
```

Target:

```
Column {
    CarSystemBar                                  // 80dp, no navigation
    OfflineBanner
    Row(weight 1f) {
        CarNavRail(currentScreen, onSelectTab)    // 80dp wide
        Box(weight 1f) { screen content }         // 48dp screen margin
    }
    CarMiniPlayer                                 // 112dp
}
```

The rail spans from below the system bar to the top of the mini-player, per §Layout. The
offline banner stays above the rail: it is a full-width app-level condition, not screen
content.

### 2.2 Which surfaces get chrome — and which deliberately do not

"Identical on every screen" needs qualifying, because three surfaces bypass `BrowseShell`
today and the design does not put them inside it either:

| Surface | Inside the shell? | Why |
|---|---|---|
| Home, Browse, Library, Favourites | **Yes** | Rail destinations |
| ArtistLikedSongs | **Yes** | Renders in the content region as a Library drill-down |
| `CarFullPlayerScreen` | **No** | Full-screen overlay; `showFullPlayer` short-circuits before `BrowseShell` (`AutomotiveApp.kt:124`) |
| `CarQueueScreen` | **No** | Conditional overlay above everything |
| `CarAuthScreen` | **No** | Gate before `AuthenticatedApp` exists (`AutomotiveApp.kt:55`); there is no signed-in user to draw a rail for |

So the contract is: **every rail destination and every screen in the content region renders
identical chrome.** Overlays and the auth gate are deliberately outside it. §9's
definition-of-done is worded against that, and A5 owns whatever chrome the full player and
queue need.

## 3. The three regions

Tokens already exist from A1: `CarSystemBarHeight` (80.dp), `CarNavRailWidth` (80.dp),
`CarMiniPlayerHeight` (112.dp), `CarScreenMargin` (48.dp), `CarChrome`, `CarGlass`,
`CarOutline`, `NyasaGold`, `CarTextSecondary`. A2 consumes the ones A1 declared and did not
yet use — that is by design, not leftover scope.

### 3.1 `CarSystemBar` (evolves `CarTopBar`)

| Property | Value |
|---|---|
| Height | `CarSystemBarHeight` (80.dp), full bleed |
| Background | `CarChrome` |
| Left | "Nyasa Music" wordmark, `NyasaGold`, 20sp weight 700, 24dp from edge, never wraps |
| Right | search, settings, avatar (32dp), clock — in that order, 24dp from edge. Wi-fi/bluetooth/battery deferred (D7) |
| Icons | 24dp, white at 80% |
| Tappable | search, settings, avatar each in a 76dp hit area via `carTouchTarget()` |

Signature after the change — navigation parameters are removed, not defaulted, so the
compiler finds every caller:

```kotlin
@Composable
fun CarSystemBar(
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier,
)
```

**Search, settings and avatar have no destinations yet** — Search is A6, Settings and Profile
are A7. They must not be silent no-ops; FR-2.6 prohibits that.

**D3: they render disabled**, tinted `CarTextDisabled`, with no click handler. `CarTextDisabled`
is a token A1 declared and nothing consumes yet; this is its first consumer, and disabled text
is exempt from the contrast floor.

`CarRestrictionDialog` is deliberately *not* reused here: it hardcodes the title "Not
available while driving" (`CarRestrictionDialog.kt:66`), which is wrong for a feature that
does not exist yet and actively misleading while parked. It gains a `title` parameter when A7
needs it.

The existing `ClockDisplay` composable is reused as-is. Wi-fi, bluetooth and battery are
deferred entirely (D7): `core.common.ui.icons` has no vectors for them, and static icons
claiming a connected radio and a full battery would be a lie the driver may act on.

### 3.2 `CarNavRail` (new)

| Property | Value |
|---|---|
| Width | `CarNavRailWidth` (80.dp) |
| Background | `CarChrome` |
| Items | Home, Browse, Library, Favourites — `CarScreen.entries` order |
| Item height | 88.dp, icon 28dp above a 13sp label |
| Rest | icon and label `CarTextSecondary` |
| Active | icon and label `NyasaGold` inside a rounded-full pill of gold at 12% alpha |

```kotlin
@Composable
fun CarNavRail(
    currentScreen: CarScreen,
    onSelectTab: (CarScreen) -> Unit,
    modifier: Modifier = Modifier,
    // The active pill slides between items, which is decorative motion. The rail cannot
    // read the vehicle state itself, so the shell passes the resolved predicate down.
    animateSelection: Boolean = false,
)
```

Exactly one item is active. Each item is a full-width 88dp row, comfortably above the 76dp
minimum, so `carTouchTarget()` is belt-and-braces rather than load-bearing here.

**The rail is never disabled while driving.** FR-2.7 lists tab switching as always
available, and A1 verified it stays live at `UxR: 255`. Do not gate the rail on
restrictions.

The active-pill slide is decorative motion and is therefore parked-only (§5).

### 3.3 `CarMiniPlayer` (mostly done)

A1 re-themed it and added the queue button. The design's "artwork and title are one target,
not two" note described a prototype defect that never existed here — but the real situation is
broader than A1 recorded: **the entire mini-player row carries `.clickable(onExpand)`**
(`CarMiniPlayer.kt:64,69`), not just the artwork and title block.

That matters for the rest of this section. Remaining for A2:

- **A 1dp top border at 8% white.** `CarOutline` is 12% (`0x1FFFFFFF`) and is the
  chip/pill outline token — wrong value and wrong role. Add `CarDivider = Color(0x14FFFFFF)`
  rather than reusing `CarOutline` or hardcoding.

- **No seek target (D6).** The design asks for one, and it cannot coexist with the row-wide
  click: a seek gesture inside a fully clickable row also fires `onExpand`, so adding seek
  means narrowing the largest, most forgiving target in the app. In a moving vehicle that
  trade is the wrong way round, and the full player — one tap away, allowed while driving —
  already seeks via `AutomotivePlayerViewModel.seekTo(Long)`. The progress bar stays a
  non-interactive indicator, vertically centred in the 76dp region so the layout still
  matches. **Record this deviation in `aaos-DESIGN.md` §Chrome.**

- **Control order.** Design: heart, previous, play/pause, next, queue. Current: previous,
  play/pause, next, progress, heart, queue.

- **Remove `basicMarquee()`** from the title and artist (`CarMiniPlayer.kt:130,138`). This is
  a live violation, not a nice-to-have: `AAOS_SCREEN_CONTRACT.md:36` says "No auto-scrolling
  text. Use ellipsis and stable row heights," and `aaos-DESIGN.md` §Motion says nothing
  auto-scrolls in either vehicle state. Replace with `maxLines = 1` and
  `TextOverflow.Ellipsis`. It arrived via PR #11, which added marquee to the mobile players
  and to this car component alongside them.

Seeking from the mini-player is playback control and stays available while driving (FR-2.7).

## 4. CarHomeScreen

### 4.1 Current state

```kotlin
@Composable
fun CarHomeScreen(
    recentlyPlayed: List<Song>,
    onSongClick: (Song) -> Unit,
    onQuickActionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    currentlyPlayingMediaId: String? = null,
    isPlaying: Boolean = false,
)
```

A greeting, a 2x2 quick-access grid (My Music / Radio / Favorites / Trending), and a
Recently Played column. `onQuickActionClick` dispatches on raw strings — `"my_music"`,
`"radio"`, `"favorites"`, `"trending"` — which `AutomotiveApp` maps back to tabs. That
stringly-typed hop should become `CarScreen` now that the rail owns navigation, or disappear
entirely if the quick-access grid does.

### 4.2 Target sections

`docs/AAOS_SCREEN_CONTRACT.md` screen 3 specifies: **Continue Listening cards, Your Mixes,
Recommended, and a Play card/item**, with loading, empty, error and offline states, and lists
truncated by the item cap.

**There is a data gap and the spec will not paper over it.** `AutomotiveContentState`
exposes exactly:

```
recentlyPlayed, genres, favoriteArtists, albums, popularSongs, likedSongs,
searchQuery, searchResults, isLoading, errorMessage
```

There is no mixes concept and no recommendation concept anywhere in the data layer. So:

| Contract section | Available source | Decision (D1) |
|---|---|---|
| Play card/item | `recentlyPlayed.first()` | Resume hero |
| Continue Listening | `recentlyPlayed` | Ships |
| Recommended | *nothing* | **Dropped** — no recommender exists |
| Your Mixes | *nothing* | **Dropped** — no mixes concept exists |
| — | `popularSongs` | Ships as **Popular Now** |

Dropped, not renamed. A section titled "Your Mixes" that is really a genre list promises
personalisation the backend does not do. `genres` stays in Browse, which owns genre
discovery; surfacing it on Home as well would duplicate the rail's job.

The section slots remain, so adding a real recommender later is a data-source change, not a
layout change.

Every list takes `maxCumulativeContentItems` — A1 clamps that value at ingestion, so `.take()`
is safe without further guarding.

### 4.2a Target signature

Whichever way Q1 resolves, the screen must gain the state it cannot currently express.
`AutomotiveContentState` already carries `isLoading` and `errorMessage`, but `CarHomeScreen`
accepts neither, so the contract's loading and error states are not implementable from
today's parameter list. `BrowseShell` currently passes only `recentlyPlayed`
(`AutomotiveApp.kt:312`).

```kotlin
@Composable
fun CarHomeScreen(
    recentlyPlayed: List<Song>,          // resume hero + Continue Listening
    popularSongs: List<Song>,            // Popular Now
    isLoading: Boolean,
    errorMessage: String?,
    onSongClick: (Song) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    currentlyPlayingMediaId: String? = null,
    isPlaying: Boolean = false,
)
```

`onQuickActionClick: (String) -> Unit` is removed with the grid (D5), along with the
`when (action)` string dispatch in `AutomotiveApp`.

### 4.3 The Favourites duplication becomes visible

A1 added `CarScreen.Favourites` for `when`-exhaustiveness and routed it to `CarLibraryScreen`
as a placeholder. It is currently unreachable because the top bar renders only three tabs.

The moment A2 ships a four-item rail, **two rail destinations render byte-identical content,
including a Sign Out button on both.**

**D2: Favourites gets a minimal real screen in A2.** Liked songs only, via `CarTrackRow` and
`CarEmptyState`. No favourite-artists row, no albums, no Sign Out — those belong to Library.
A4 replaces it with the designed screen (hero, Play all, Shuffle, unlike).

```kotlin
@Composable
fun CarFavouriteMusicScreen(
    likedSongs: List<Song>,
    onSongClick: (Song) -> Unit,
    onBrowseClick: () -> Unit,           // empty-state CTA routes to Browse root
    modifier: Modifier = Modifier,
    currentlyPlayingMediaId: String? = null,
    isPlaying: Boolean = false,
)
```

The alternative — hiding the rail item until A4 — would change the rail's shape mid-programme,
which contradicts the fixed-chrome contract this slice exists to establish.

## 5. Ambient motion

`docs/aaos-DESIGN.md` §Motion:

- **Parked:** ambient background gradients drift slowly and may follow the current track's
  artwork hue. Screen changes cross-fade. The rail's active pill slides.
- **Driving:** all decorative motion stops and the gradients freeze in place. Only
  informational motion continues — progress bar, clock, play/pause state.
- **Always:** nothing auto-scrolls, pulses or parallaxes.
- If `Settings.Global.ANIMATOR_DURATION_SCALE` is `0`, the decorative layer is disabled
  entirely, even parked.

### 5.1 What has to be built

Nothing exists for this. Two new pieces:

1. **A reduced-motion signal.** No helper reads `ANIMATOR_DURATION_SCALE` anywhere in the
   repo today. It needs a small `:automotive` utility exposing it as state, combined with
   `UxRestrictionState.isDistractionOptimized` into one boolean the ambient layer consumes:

   ```
   decorativeMotionEnabled = !isDistractionOptimized && animatorDurationScale != 0f
   ```

   Gate on `isDistractionOptimized` here, deliberately. `docs/AAOS_DRIVING_STATE_TESTING.md`
   warns that idling reports `DO: true` with only `NO_VIDEO` set and that code must not gate
   *restrictions* on that flag alone — but that warning is about over-refusing, where the
   cost is a driver denied a legitimate action. Decorative motion inverts the asymmetry:
   over-freezing costs nothing, and there is no restriction flag for "decorative animation"
   to gate on instead. Flagged here so a later reader does not "fix" it into a flag check.

2. **The ambient layer itself** — a composable behind screen content drawing the drifting
   gradient from `CarAmbientBlue` / `CarAmbientPurple`, frozen to a static frame when
   `decorativeMotionEnabled` is false. Frozen must mean *stopped*, not hidden: the background
   should not visibly change at the moment the vehicle starts moving.

   **It will be invisible unless backgrounds move first.** Every surface above it currently
   paints opaque `NyasaBackground` — the shell at `AutomotiveApp.kt:122`, and each screen
   again (`CarHomeScreen.kt:68`, `CarBrowseScreen.kt:136`, and siblings). Painting the
   background must move up into the shell, once, behind the ambient layer, and out of the
   individual screens. That is a change to all seven screens and belongs in the chrome half
   of this slice, not bolted on with the motion work.

**D4: artwork-following hue is deferred.** It would mean `androidx.palette` — not in
`gradle/libs.versions.toml` — plus bitmap access from Coil, off-main-thread extraction,
caching, and fallbacks for missing or single-colour art. The design says the hue *may* follow
artwork, so it is optional by its own wording. Ship the fixed `CarAmbientBlue` /
`CarAmbientPurple` two-tone; swapping the colour source later does not change the layer.

## 6. Verification

### 6.1 Unit tests

The chrome is layout, and this project has no Compose UI test harness — A1 established that
JVM unit tests are the only automated coverage. Testable in JVM tests:

- The reduced-motion predicate: driving, parked, `scale == 0`, and parked with `scale == 1`.
- Any pure mapping introduced by the Home section decision.

Layout correctness itself is manual. Do not pretend otherwise by asserting on dp constants.

### 6.2 Manual checklist

Run on `AAOS_AOSP_33_userdebug` per `docs/AAOS_DRIVING_STATE_TESTING.md`, which supports
scripted driving-state injection.

1. Every one of the seven existing screens renders the same system bar, the same rail, and
   the same mini-player, in the same positions. This is the contract; a single screen that
   differs is a failure.
2. All four rail destinations are reachable and exactly one is active at a time.
3. Inject driving. The rail stays usable, tab switching works, playback controls work.
4. Inject driving while the ambient layer is drifting: motion stops, and the background does
   not jump.
5. `adb shell settings put global animator_duration_scale 0` while parked: no decorative
   motion. Restore to `1`.
6. No screen's content is clipped by the rail — the 80dp is taken out of the content region,
   not overlaid on it.

### 6.3 Gates

- `oem` and `playstore` both build, test and lint green (NFR-7).
- Detekt `maxIssues: 0`.
- Touch targets: rail items 88dp, system-bar controls 76dp.
- Contrast: no new measurement needed. `CarTextSecondary` on chrome `#111118` is already
  recorded at **8.4:1** in `docs/aaos-DESIGN.md` §Contrast, so the rail's rest state clears
  AAA. Re-measure only if a new colour pair is introduced.

## 7. Decisions

Taken 2026-08-08. Recorded here so implementation does not re-litigate them.

| # | Decision | Rationale |
|---|---|---|
| D1 | Home ships **Continue Listening** (`recentlyPlayed`), **Popular Now** (`popularSongs`) and a resume hero. "Your Mixes" and "Recommended" are dropped, not renamed. | The backend has no recommender. A truthful section title beats dressing `genres` up as personalisation. Genre discovery belongs to Browse; duplicating it on Home muddies the rail's job. The section slots remain, so a future recommender swaps a data source without touching layout. |
| D2 | The rail ships **all four items**, and Favourites gets a **minimal real screen** — liked songs only, `CarTrackRow` + `CarEmptyState`, no artists, no albums, no Sign Out. A4 replaces it. | Hiding the item would change the rail's shape in A4, contradicting the fixed-chrome contract this slice exists to establish. Two tabs rendering identical content, with two Sign Out buttons, is a visible bug. ~40 lines is the cheapest way to have neither. |
| D3 | Search, settings and avatar render **disabled**, using `CarTextDisabled`. No dialog. | FR-2.6 prohibits *silent no-ops* — a control that looks live and does nothing. A visibly disabled control is honest state, not a no-op. A dialog costs a tap and a dismissal to learn nothing. `CarRestrictionDialog` gains a `title` parameter when A7 needs it, not before. |
| D4 | **No `androidx.palette`.** Ambient ships as the fixed `CarAmbientBlue`/`CarAmbientPurple` two-tone. | The design says the hue *may* follow artwork — explicitly optional. Extracting it needs a dependency plus bitmap access, off-main-thread work, caching and fallbacks for missing or single-colour art. A1 refused to add a dependency inside a foundation slice for the same reason. The layer's structure does not change when the colour source later does. |
| D5 | **Delete the quick-access grid** and the `onQuickActionClick: (String) -> Unit` hop with it. | Three of its four actions become rail destinations; two navigation systems on one screen is the muscle-memory problem the chrome contract prevents. The fourth, Radio, has no implementation and routes to Browse — a dead label. Frees the space D1 fills. |
| D7 | **Defer the wi-fi, bluetooth and battery indicators.** The bar ships as wordmark · search · settings · avatar · clock. | `core.common.ui.icons` has no vectors for them (only `WifiOffIcon`), so shipping them means authoring three `ImageVector`s *and* wiring real system state. Static icons claiming a full battery and a connected radio are a lie the driver may act on. On AAOS the OEM system bar generally owns these. Record in `aaos-DESIGN.md` beside D6. |
| D6 | **No seek in the mini-player.** The progress bar stays a non-interactive indicator inside the 76dp region, and the row-wide tap-to-expand is kept. | The row-wide target is large and forgiving, which is what a moving vehicle needs; narrowing it for a precision gesture is a net loss. Seeking already exists in the full player, one tap away and permitted while driving. This deviates from `aaos-DESIGN.md` §Chrome and must be recorded there rather than silently skipped. |

## 8. Risks

| Risk | Impact | Mitigation |
|---|---|---|
| Moving navigation out of the top bar touches every screen's available width | Layout regressions across all seven screens | Land the shell first and screenshot all seven before touching Home |
| `CarTopBar` → `CarSystemBar` is a breaking signature change | Compile break | Only one caller (`AutomotiveApp.kt:306`); remove params rather than defaulting them so the compiler finds it |
| Ambient motion is the first animation in the car module | Jank on a 1024p head unit, or motion that survives into driving | Freeze-not-hide; verify with scripted injection rather than by eye |
| Home rebuild depends on unresolved Q1 | Rework | Do not start Home until Q1 is answered; the chrome half of A2 is independent and can proceed |

## 9. Definition of done

1. `CarSystemBar` renders the design's bar with no navigation in it, three 76dp controls.
2. `CarNavRail` exists, carries four destinations, marks exactly one active, and is never
   disabled while driving.
3. `AuthenticatedApp` composes bar / rail+content / mini-player as in §2.1.
4. Every rail destination and content-region screen renders identical chrome, per §2.2.
   FullPlayer, Queue and Auth are explicitly outside it.
5. Background painting moved into the shell; no screen paints its own opaque background.
6. `CarMiniPlayer` has its 8% top border, the design's control order, and `basicMarquee`
   removed. No seek target; the deviation is recorded in `aaos-DESIGN.md`.
7. `CarHomeScreen` rebuilt: resume hero, Continue Listening, Popular Now, loading and error
   states, quick-access grid deleted.
7a. `CarFavouriteMusicScreen` exists in minimal form and is reachable from the rail.
8. Decorative motion runs only when parked and only when the animator scale is non-zero,
   with JVM tests on the predicate.
9. Both flavors green; Detekt zero; the §6.2 checklist executed and its outcome recorded.