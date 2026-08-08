# AAOS Slice A2 — Chrome, Home & Ambient Motion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move navigation out of the top bar into a left rail, establish the three-region
chrome contract every later screen composes inside, rebuild Home against it, and add the
parked-only ambient layer.

**Spec:** `docs/superpowers/specs/2026-08-08-aaos-chrome-home-design.md`
(decisions D1–D6 in §7 are settled — do not re-litigate them)

**Tech stack:** Kotlin, Jetpack Compose, Hilt, JUnit 4, Gradle Kotlin DSL, Detekt, Lint.

## Global constraints

- **Max line length 120.** Trailing commas required. No wildcard imports.
- **Detekt `maxIssues: 0`.** Run `./gradlew detekt` before every commit.
- **Top-level constants are PascalCase** in this repo (`RecentlyPlayedLimit`, not
  `RECENTLY_PLAYED_LIMIT`). A1 lost time to this — `TopLevelPropertyNaming` uses
  `[A-Z][A-Za-z0-9]*`.
- **`MatchingDeclarationName` is on.** A file containing exactly one top-level class-like
  declaration must be named after it.
- **Composables emitting UI take `modifier: Modifier = Modifier`** as the first optional
  parameter.
- **Flavors exist.** Task names are `:automotive:assembleOemDebug`,
  `:automotive:testOemDebugUnitTest`, `:automotive:lintOemDebug` — not the plain `Debug`
  variants.
- **Verify on `AAOS_AOSP_33_userdebug`**, not the Play AVD. See
  `docs/AAOS_DRIVING_STATE_TESTING.md`; driving state is scriptable there.
- **Commits:** no AI attribution, no `Co-Authored-By`. Subject ≤72 chars.
- **The seven existing screens must keep compiling and running throughout.**

## Sequencing

Tasks 1–5 are the shell and are independent of Home. Task 6 onward sits inside it. Land and
screenshot the shell before touching screen content — that ordering is the entire reason A2
precedes A3.

## File structure

**Created**

| File | Responsibility |
|---|---|
| `automotive/.../ui/components/CarNavRail.kt` | The 80dp left rail, four destinations |
| `automotive/.../ui/components/CarAmbientBackground.kt` | Parked-only decorative gradient layer |
| `automotive/.../ui/motion/DecorativeMotion.kt` | `animatorDurationScale` reader + the predicate |
| `automotive/.../ui/screens/CarFavouriteMusicScreen.kt` | Minimal liked-songs screen (D2) |
| `automotive/src/test/.../ui/motion/DecorativeMotionTest.kt` | Predicate truth table |

**Modified**

| File | Change |
|---|---|
| `automotive/.../ui/components/CarTopBar.kt` | Becomes `CarSystemBar`; navigation removed; three disabled controls added |
| `automotive/.../ui/components/CarMiniPlayer.kt` | Top border, control order, `basicMarquee` removed |
| `automotive/.../ui/theme/AutomotiveColors.kt` | Add `CarDivider` |
| `automotive/.../ui/AutomotiveApp.kt` | Shell recomposed as bar / rail+content / mini-player; background hoisted; quick-action dispatch deleted |
| `automotive/.../ui/screens/CarHomeScreen.kt` | Rebuilt per D1 and D5 |
| The other six screens | Stop painting their own opaque background |
| `docs/aaos-DESIGN.md` | Record the D6 seek deviation |

---

### Task 1: `CarDivider` token and the mini-player corrections

Independent of everything else and touches one component. Doing it first gets a known-good
commit in before the structural work.

**Files:** `AutomotiveColors.kt`, `CarMiniPlayer.kt`

- [ ] **Step 1: Add the token**

In `AutomotiveColors.kt`, beside `CarOutline`:

```kotlin
/** Hairline divider between chrome regions. 8% white, per the design's mini-player border. */
val CarDivider = Color(0x14FFFFFF)
```

`CarOutline` (12%) is the chip and pill outline and is the wrong value and role here.

- [ ] **Step 2: Remove `basicMarquee`**

`CarMiniPlayer.kt:130` and `:138` call `Modifier.basicMarquee()` on the title and artist.
This violates `AAOS_SCREEN_CONTRACT.md:36` ("No auto-scrolling text. Use ellipsis and stable
row heights") and `aaos-DESIGN.md` §Motion. Replace each with:

```kotlin
maxLines = 1,
overflow = TextOverflow.Ellipsis,
```

Both already set `maxLines = 1`; add the overflow and drop the modifier. Remove the
`androidx.compose.foundation.basicMarquee` import; add
`androidx.compose.ui.text.style.TextOverflow` if absent.

**`CarFullPlayerScreen.kt:223` and `:231` have the same violation.** Fix them in this task
too — the rule is "no auto-scrolling text" anywhere in the car module, and leaving the full
player marqueeing would make the final verification's "nothing auto-scrolls" claim false.
Confirm none remain:

```bash
grep -rn "basicMarquee" automotive/src/main/java/ || echo "clean"
```

- [ ] **Step 3: Top border**

Add a 1dp top border in `CarDivider` to the mini-player `Row`.

- [ ] **Step 4: Control order**

Design order is heart, previous, play/pause, next, queue. Current is previous, play/pause,
next, then progress, heart, queue. Reorder so the transport cluster reads
heart · previous · play/pause · next · queue, with the progress indicator to its left.

Do **not** add a seek target (D6). Keep the progress bar non-interactive and keep the
row-wide `.clickable(onExpand)`.

- [ ] **Step 5: Verify**

```bash
./gradlew :automotive:assembleOemDebug && ./gradlew detekt
```

- [ ] **Step 6: Commit**

```bash
git commit -m "fix: remove auto-scrolling text from the car mini-player"
```

---

### Task 2: Record the D6 deviation in the design doc

Small, but it is the difference between a decision and an omission.

- [ ] **Step 1:** In `docs/aaos-DESIGN.md` §Chrome, under the mini-player block, note that the
  implementation deliberately ships the progress bar as a non-interactive indicator: seeking
  lives in the full player, because a seek target cannot coexist with the row-wide
  tap-to-expand and the large target is worth more in a moving vehicle.

- [ ] **Step 2: Commit**

```bash
git commit -m "docs: record that mini-player seek lives in the full player"
```

---

### Task 3: `CarSystemBar` — navigation out, status in

**This breaks `CarTopBar`'s signature deliberately.** Remove the navigation parameters rather
than defaulting them, so the compiler finds the single caller (`AutomotiveApp.kt:306`).

**Files:** `CarTopBar.kt` → `CarSystemBar.kt`, `AutomotiveApp.kt`

- [ ] **Step 1: Rename the file and the composable**

`git mv` `CarTopBar.kt` to `CarSystemBar.kt` and rename `CarTopBar` to `CarSystemBar`.
`MatchingDeclarationName` will fail otherwise.

- [ ] **Step 2: New signature**

```kotlin
@Composable
fun CarSystemBar(
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier,
)
```

- [ ] **Step 3: Delete `NavigationTabs` and `CarTab`**

Both move to the rail in Task 4. Delete them here rather than leaving them unused — Detekt's
unused-symbol rules and ktlint will complain, and a stale copy invites divergence.

- [ ] **Step 4: Build the right-hand cluster**

Order, 24dp from the right edge: search, settings, avatar (32dp circle), clock, wi-fi,
bluetooth, battery. Icons 24dp, white at 80%.

Search, settings and avatar are **disabled** (D3): tint `CarTextDisabled`, no `clickable`.
They still occupy `carTouchTarget()` hit areas so the layout does not shift when A6/A7 enable
them. The three callbacks stay in the signature, unused for now, so enabling them later is a
one-line change per control.

Keep the existing `AppLogo` and `ClockDisplay` composables.

**Wi-fi, bluetooth and battery are deferred (D7).** `core.common.ui.icons` has no vectors for
them — only `WifiOffIcon` — so shipping them means authoring three new `ImageVector`s *and*
wiring real system state. Static icons showing a full battery and a connected radio would be
worse than none: they are a lie the driver may act on. On AAOS the OEM system bar usually
owns connectivity and battery anyway. Ship the bar as wordmark · [gap] · search · settings ·
avatar · clock, and record the deviation in `aaos-DESIGN.md` alongside the D6 note.

- [ ] **Step 5: Update the caller**

`AutomotiveApp.kt:306` — pass three no-op lambdas for now. They become real in A6/A7.

- [ ] **Step 6: Verify**

```bash
./gradlew :automotive:assembleOemDebug && ./gradlew detekt
```

Expected: the bar renders with no tabs. Navigation is temporarily unreachable — that is
expected between Tasks 3 and 4, and is why they land together in Task 5's screenshot pass.

- [ ] **Step 7: Commit**

```bash
git commit -m "refactor: move navigation out of the car system bar"
```

---

### Task 4: `CarNavRail`

**Files:** Create `automotive/.../ui/components/CarNavRail.kt`

- [ ] **Step 1: Create the rail**

```kotlin
@Composable
fun CarNavRail(
    currentScreen: CarScreen,
    onSelectTab: (CarScreen) -> Unit,
    modifier: Modifier = Modifier,
    animateSelection: Boolean = false,
)
```

| Property | Value |
|---|---|
| Width | `CarNavRailWidth` (80.dp) |
| Background | `CarChrome` |
| Items | `CarScreen.entries` — Home, Browse, Library, Favourites |
| Item height | 88.dp, icon 28dp above a 13sp label |
| Rest | `CarTextSecondary` (8.4:1 on chrome — already measured, no new check needed) |
| Active | `NyasaGold` icon and label inside a rounded-full pill of `NyasaGold.copy(alpha = 0.12f)` |

Icons: reuse `HomeIcon`, `SearchIcon`, `LibraryIcon` from
`core.common.ui.icons` as the existing tabs do; Favourites uses `HeartIcon`.

`animateSelection` drives the pill slide. When false, the pill jumps. The rail cannot read
vehicle state itself — the shell resolves the predicate and passes it down.

**The rail is never disabled while driving.** FR-2.7 lists tab switching as always available
and A1 verified it live at `UxR: 255`. Do not gate it.

- [ ] **Step 2: Verify**

```bash
./gradlew :automotive:assembleOemDebug && ./gradlew detekt
```

- [ ] **Step 3: Commit**

```bash
git commit -m "feat: add the car navigation rail"
```

---

### Task 5: Recompose the shell

The structural change. After this the chrome contract is real.

**Files:** `AutomotiveApp.kt`, plus all seven screens for the background hoist

- [ ] **Step 1: Restructure `BrowseShell`**

From:

```
Column { CarTopBar; OfflineBanner; Box(weight 1f){ content }; CarMiniPlayer }
```

To:

```
Column {
    CarSystemBar(...)
    OfflineBanner(isOffline = playerState.isOffline)
    Row(Modifier.weight(1f)) {
        CarNavRail(currentScreen, onSelectTab, animateSelection = decorativeMotionEnabled)
        Box(Modifier.weight(1f)) { content }
    }
    CarMiniPlayer(...)
}
```

`decorativeMotionEnabled` arrives in Task 7; until then pass `false`.

- [ ] **Step 2: Hoist the background**

Every screen currently paints opaque `NyasaBackground` — `AutomotiveApp.kt:122`,
`CarHomeScreen.kt:68`, `CarBrowseScreen.kt:136`, and the rest. Remove
`.background(NyasaBackground)` from the screens and paint it once. Without this the ambient
layer in Task 8 is invisible.

**Paint it at the root of `AutomotiveApp`, not inside `BrowseShell`.** `CarAuthScreen` is
rendered from the other branch of the auth `if` (`AutomotiveApp.kt:61`) and never passes
through `BrowseShell`; hoisting into the shell alone would leave the signed-out screen with
no background at all. The root `Box` wraps both branches.

Find them with:

```bash
grep -rn "background(NyasaBackground)" automotive/src/main/java/
```

- [ ] **Step 3: Apply the screen margin**

`CarScreenMargin` (48.dp) on the content `Box`, so screens stop applying their own outer
padding. Check each screen for a now-doubled `.padding(24.dp)` and remove it.

- [ ] **Step 4: Build and install**

```bash
./gradlew :automotive:assembleOemDebug
adb -s emulator-5554 install -r automotive/build/outputs/apk/oem/debug/automotive-oem-debug.apk
```

- [ ] **Step 5: Screenshot every screen — this is the gate**

Open Home, Browse, Library, ArtistLikedSongs and confirm each shows the identical system bar,
identical rail, identical mini-player, in identical positions, with no content clipped by the
rail. FullPlayer, Queue and Auth are outside the shell by design (spec §2.2) — confirm they
still render and are not accidentally wrapped in chrome.

Record the outcome. A single screen that differs is a failure of the contract, not a cosmetic
bug.

- [ ] **Step 6: Verify and commit**

```bash
./gradlew detekt && ./gradlew :automotive:testOemDebugUnitTest
git commit -m "refactor: compose the car shell as bar, rail and mini-player"
```

---

### Task 6: Minimal Favourites screen (D2)

**Files:** Create `CarFavouriteMusicScreen.kt`; modify `AutomotiveApp.kt`

- [ ] **Step 1: Create the screen**

```kotlin
@Composable
fun CarFavouriteMusicScreen(
    likedSongs: List<Song>,
    onSongClick: (Song) -> Unit,
    onBrowseClick: () -> Unit,
    modifier: Modifier = Modifier,
    currentlyPlayingMediaId: String? = null,
    isPlaying: Boolean = false,
)
```

A `LazyColumn` of `CarTrackRow`, and `CarEmptyState` when `likedSongs` is empty with a
"Browse Music" action routing to Browse root. No favourite-artists row, no albums, no Sign
Out — those stay in Library. A4 replaces this with the designed screen.

- [ ] **Step 2: Route the rail destination**

Replace the `CarScreen.Favourites -> CarLibraryScreen(...)` placeholder in `BrowseShell` with
`CarFavouriteMusicScreen`. Two rail tabs must no longer render identical content.

- [ ] **Step 3: Verify on device**

Both Library and Favourites reachable from the rail and visibly different. Empty state
reachable by signing in with an account that has no likes, or trust the composable and note
it as unverified.

- [ ] **Step 4: Commit**

```bash
git commit -m "feat: add a minimal car favourites screen"
```

---

### Task 7: The decorative-motion predicate

TDD — this is the one genuinely testable piece of A2.

**Files:** Create `ui/motion/DecorativeMotion.kt` and its test

- [ ] **Step 1: Write the failing test**

`automotive/src/test/.../ui/motion/DecorativeMotionTest.kt`:

```kotlin
class DecorativeMotionTest {
    @Test fun parkedWithAnimationsOn_isEnabled() =
        assertTrue(decorativeMotionEnabled(isDistractionOptimized = false, animatorScale = 1f))

    @Test fun driving_isDisabled() =
        assertFalse(decorativeMotionEnabled(isDistractionOptimized = true, animatorScale = 1f))

    @Test fun animationsOff_isDisabledEvenParked() =
        assertFalse(decorativeMotionEnabled(isDistractionOptimized = false, animatorScale = 0f))

    @Test fun drivingAndAnimationsOff_isDisabled() =
        assertFalse(decorativeMotionEnabled(isDistractionOptimized = true, animatorScale = 0f))
}
```

- [ ] **Step 2: Implement**

```kotlin
/**
 * Decorative motion is allowed only while parked and only when the platform has not
 * disabled animations.
 *
 * Gating on isDistractionOptimized is deliberate. AAOS_DRIVING_STATE_TESTING.md warns
 * against gating restrictions on that flag alone, because idling reports it true with only
 * NO_VIDEO set — but that warning is about over-refusing a driver's action. Over-freezing
 * decoration costs nothing, and no restriction flag covers decorative animation.
 */
fun decorativeMotionEnabled(isDistractionOptimized: Boolean, animatorScale: Float): Boolean =
    !isDistractionOptimized && animatorScale != 0f
```

Plus a Compose-side reader for
`Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)`. Nothing in
the repo reads this today. Observe it with a `ContentObserver` rather than sampling once —
the user can change it while the app runs.

- [ ] **Step 3: Verify**

```bash
./gradlew :automotive:testOemDebugUnitTest && ./gradlew detekt
```

Expected: 4 new tests pass; 29 total across the module and `:core:common`.

- [ ] **Step 4: Commit**

```bash
git commit -m "feat: add the decorative motion predicate"
```

---

### Task 8: The ambient layer

**Files:** Create `CarAmbientBackground.kt`; modify `AutomotiveApp.kt`

- [ ] **Step 1: Create the layer**

```kotlin
@Composable
fun CarAmbientBackground(
    animate: Boolean,
    modifier: Modifier = Modifier,
)
```

A slow drifting gradient from `CarAmbientBlue` and `CarAmbientPurple` over `CarObsidian`.
Fixed two-tone — no artwork extraction (D4).

**Frozen means stopped, not hidden.** When `animate` is false the gradient renders at a
static frame; the background must not visibly change at the moment the vehicle starts moving.
Drive it from an `InfiniteTransition` that is simply not started, or hold the last offset —
do not swap to a different composable.

- [ ] **Step 2: Wire it into the shell**

Behind the content `Box`, above the hoisted background from Task 5. Pass
`animate = decorativeMotionEnabled`, and pass the same value to `CarNavRail`'s
`animateSelection`.

- [ ] **Step 3: Verify on device**

Using scripted injection from `docs/AAOS_DRIVING_STATE_TESTING.md`:

```bash
# Start parked (gear PARK = 4) and confirm the gradient drifts
adb -s emulator-5554 shell cmd car_service inject-vhal-event 0x11400400 4

# Go driving (gear DRIVE = 8, then hold speed) — motion must stop without the
# background jumping to a different frame
adb -s emulator-5554 shell cmd car_service inject-vhal-event 0x11400400 8
adb -s emulator-5554 shell cmd car_service inject-continuous-events 0x11600207 40 -s 5 -d 60

# Back to parked — drift resumes
adb -s emulator-5554 shell cmd car_service inject-vhal-event 0x11600207 0
adb -s emulator-5554 shell cmd car_service inject-vhal-event 0x11400400 4
```

Then, parked:

```bash
adb -s emulator-5554 shell settings put global animator_duration_scale 0   # no motion
adb -s emulator-5554 shell settings put global animator_duration_scale 1   # restore
```

- [ ] **Step 4: Commit**

```bash
git commit -m "feat: add the parked-only ambient background"
```

---

### Task 9: Rebuild Home

Last, because it sits inside everything above.

**Files:** `CarHomeScreen.kt`, `AutomotiveApp.kt`

- [ ] **Step 1: New signature**

```kotlin
@Composable
fun CarHomeScreen(
    recentlyPlayed: List<Song>,
    popularSongs: List<Song>,
    isLoading: Boolean,
    errorMessage: String?,
    onSongClick: (List<Song>, Song) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    currentlyPlayingMediaId: String? = null,
    isPlaying: Boolean = false,
)
```

**`onSongClick` takes the section's list, not just the song, and this is load-bearing.**
`AutomotivePlayerViewModel.playSong(songs, song)` resolves the start index with
`indexOfFirst { it.mediaId == song.mediaId }.coerceAtLeast(0)`
(`AutomotivePlayerViewModel.kt:178`). A song that is not in the list it is given yields `-1`,
coerced to `0` — so a single `onSongClick` bound to `recentlyPlayed` would make every Popular
Now tap play the first recently-played track instead. Each section passes its own list. This
matches the existing `onArtistSongClick: (List<Song>, Song) -> Unit` shape in `BrowseShell`.

- [ ] **Step 2: Delete the quick-access grid (D5)**

Remove `QuickActionsColumn`, `QuickActionCard`, and the `onQuickActionClick` parameter. In
`AutomotiveApp`, delete the `when (action)` string dispatch that mapped `"my_music"` and
friends to tabs — the rail owns that now.

- [ ] **Step 3: Build the sections (D1)**

1. **Resume hero** — `recentlyPlayed.firstOrNull()`, large card, tap to play.
2. **Continue Listening** — `recentlyPlayed`.
3. **Popular Now** — `popularSongs`.

No "Your Mixes", no "Recommended". Both are dropped, not renamed; the backend has neither.

Every list takes `maxCumulativeContentItems`, which A1 clamps at ingestion.

- [ ] **Step 4: Loading, error and empty states**

`isLoading` renders static skeletons — no shimmer while driving, and none at all when
decorative motion is off. `errorMessage` renders an inline error with `onRetry`. Empty
renders `CarEmptyState`. The offline banner is already handled by the shell.

- [ ] **Step 5: Give the ViewModel a retry that actually retries**

`reloadUserContent()` is **not** a valid retry. It early-returns when the user has not
changed (`AutomotiveContentViewModel.kt:63`), so wiring the error state's Retry button to it
produces a no-op — the worst kind of error UI. Add a public entry point beside it:

```kotlin
/** Retry after a load failure. Unlike reloadUserContent(), does not early-return. */
fun retryLoad() {
    loadContent()
}
```

- [ ] **Step 6: Update the caller**

`BrowseShell` currently passes only `recentlyPlayed` (`AutomotiveApp.kt:312`). Pass
`popularSongs`, `isLoading`, `errorMessage`, `contentViewModel::retryLoad`, and an
`onSongClick` that forwards the section's own list:

```kotlin
onSongClick = { songs, song ->
    playerViewModel.playSong(songs, song)
    showFullPlayer = true
},
```

- [ ] **Step 7: Update the preview**

`CarScreenPreviews.kt:80` calls `CarHomeScreen` with `onQuickActionClick = {}` and none of
the new parameters. It lives in `src/main`, so **`assembleOemDebug` fails until it is
updated.** Supply `popularSongs = PreviewSongs`, `isLoading = false`, `errorMessage = null`,
`onRetry = {}`, and the two-argument `onSongClick`.

- [ ] **Step 8: Verify and commit**

```bash
./gradlew :automotive:assembleOemDebug && ./gradlew detekt
git commit -m "feat: rebuild the car home screen against the chrome contract"
```

---

## Final verification

- [ ] **Full build and check**

```bash
./gradlew clean
./gradlew :automotive:assembleOemDebug :automotive:assemblePlaystoreDebug
./gradlew :core:common:testDebugUnitTest :automotive:testOemDebugUnitTest
./gradlew detekt
./gradlew :core:common:lintDebug :core:data:lintDebug \
  :automotive:lintOemDebug :automotive:lintPlaystoreDebug
```

`:app:lintDebug` is **expected to fail** — it crashes in `androidx.navigation`'s lint jar,
confirmed pre-existing at `55d2e43`. Do not treat it as an A2 regression and do not try to
fix it here.

- [ ] **Chrome contract** — all four rail destinations plus ArtistLikedSongs render identical
  bar, rail and mini-player. FullPlayer, Queue and Auth deliberately outside.
- [ ] **Driving** — rail usable, tab switching works, playback works, ambient frozen, nothing
  auto-scrolls.
- [ ] **Definition of done** — walk spec §9 items 1–9 and record the outcome of each.
