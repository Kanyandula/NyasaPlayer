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
- Not the real Favourites screen (see the open question in §4.3).
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
| Right | search, settings, avatar (32dp), clock, wi-fi, bluetooth, battery — in that order, 24dp from edge |
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

`CarRestrictionDialog` cannot be reused as-is: it hardcodes the title "Not available while
driving" (`CarRestrictionDialog.kt:66`), which is the wrong sentence for a feature that does
not exist yet and would be actively misleading while parked. Either give it a `title`
parameter defaulted to the current string, or render the three controls disabled. See §7, Q3.

The existing `ClockDisplay` composable is reused as-is. Wi-fi, bluetooth and battery are
presentation-only for now; wiring them to real system state is not A2 scope, and they should
render from a single `CarSystemStatus` value so the wiring is one change later.

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

- **A 76dp-tall seek target around the progress bar.** Currently a `LinearProgressIndicator`
  with no seek affordance. This is new interaction, and it **conflicts with the row-wide
  click above**: a seek gesture inside a fully clickable row will also fire `onExpand`. The
  row-level clickable must be narrowed to the artwork-and-title block before the seek target
  is added, or seeking is unreachable. Signature:

  ```kotlin
  onSeek: (Long) -> Unit,   // backed by AutomotivePlayerViewModel.seekTo(Long)
  ```

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

| Contract section | Available source | Decision |
|---|---|---|
| Continue Listening | `recentlyPlayed` | Direct map |
| Your Mixes | *nothing* | Substitute `genres` as "Browse by mood", or defer |
| Recommended | *nothing* | Substitute `popularSongs` as "Popular now", or defer |
| Play card/item | `recentlyPlayed.first()` | Direct map |

Inventing a recommender is out of scope for a chrome slice. The honest options are to ship
the substitutions under truthful section titles, or to ship Continue Listening plus Popular
and leave the third section out until a repository slice adds one. **This needs a product
decision — see §7, Q1.** Do not implement a fake "Your Mixes" that is really genres.

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
    recentlyPlayed: List<Song>,          // Continue Listening
    popularSongs: List<Song>,            // pending Q1
    genres: List<Genre>,                 // pending Q1
    isLoading: Boolean,
    errorMessage: String?,
    onSongClick: (Song) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    currentlyPlayingMediaId: String? = null,
    isPlaying: Boolean = false,
)
```

`onQuickActionClick: (String) -> Unit` is dropped here on the assumption Q5 removes the
quick-access grid. If Q5 keeps it, the replacement takes `CarScreen` rather than raw strings
now that the rail owns navigation.

### 4.3 The Favourites duplication becomes visible

A1 added `CarScreen.Favourites` for `when`-exhaustiveness and routed it to `CarLibraryScreen`
as a placeholder. It is currently unreachable because the top bar renders only three tabs.

The moment A2 ships a four-item rail, **two rail destinations render byte-identical content,
including a Sign Out button on both.** The real screen is A4. This needs a decision — see
§7, Q2.

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

**Artwork-following hue needs a dependency decision.** Extracting a colour from album art
means `androidx.palette`, which is **not** in `gradle/libs.versions.toml`. A1 explicitly
deferred adding dependencies inside a foundation slice. Either add it deliberately here, or
ship the fixed two-tone ambient and treat artwork-following as a later refinement — see §7,
Q4.

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

## 7. Open questions

| # | Question | Blocks | Owner |
|---|---|---|---|
| Q1 | Home's third and fourth sections: ship `genres`/`popularSongs` under truthful titles, or ship two sections and defer? Inventing a recommender is out of scope. | §4.2 | Product |
| Q2 | Favourites on the rail before A4 builds it: hide the item, render it disabled with a "coming soon" state, or accept two tabs showing identical content? | §4.3 | Product |
| Q3 | Search/settings/avatar have no destinations until A6/A7. Render them disabled, or give `CarRestrictionDialog` a `title` parameter so it can say something other than "not available while driving"? FR-2.6 forbids silent no-ops either way. | §3.1 | Product |
| Q4 | Add `androidx.palette` for artwork-following ambient hue, or ship fixed two-tone and defer? | §5.1 | Tech |
| Q5 | Does the quick-access grid survive once the rail exists? Three of its four actions (My Music, Favorites, Trending) become rail destinations or duplicates of them. | §4.1 | Product |
| Q6 | Narrowing the mini-player's row-wide click to the artwork/title block is required before a seek target can work, but it removes a large existing tap area. Acceptable, or should seek live only in the full player? | §3.3 | Product |

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
6. `CarMiniPlayer` has its 8% top border, the design's control order, `basicMarquee`
   removed, and — subject to Q6 — a 76dp seek target with the row-wide click narrowed.
7. `CarHomeScreen` rebuilt against the chrome, with loading and error states, and the
   sections resolved by Q1.
8. Decorative motion runs only when parked and only when the animator scale is non-zero,
   with JVM tests on the predicate.
9. Both flavors green; Detekt zero; the §6.2 checklist executed and its outcome recorded.