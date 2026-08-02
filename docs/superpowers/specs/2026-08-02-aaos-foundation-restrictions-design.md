# AAOS Slice A1 — Foundation & Restrictions

**Date:** 2026-08-02
**Status:** Approved design, ready for implementation planning
**Scope:** `:automotive`, `:core:common`, `:core:playback` (no changes), `:app` (no changes)

---

## 1. Context

The AAOS design work produced a 20-screen system, a token spec, and an interactive
prototype, recorded in:

- `docs/aaos-DESIGN.md` — tokens, chrome contract, component specs, measured contrast
- `docs/aaos-screens.html` — all 20 screens, static
- `docs/aaos-app.html` — navigable prototype with driving restrictions enforced

This spec covers **Slice A1 only**: the foundation those screens will be built on. It
adds no new screens.

### 1.1 Decisions taken before this spec

Four decisions were settled during brainstorming. They are recorded here because each one
invalidates a plausible alternative reading of the work.

**Custom launcher is the product; Play Store compliance is deferred.**
`docs/AAOS_UI_REDESIGN_PLAN.md` §1.1 records an unresolved conflict: the app ships both an
OEM media-template surface and a custom Compose launcher, and Play's AAOS media category
rejects apps shipping custom activities for playback or browse. The 20-screen design is a
custom launcher and makes that conflict larger, not smaller.

Resolution: Play Store distribution is **not a hard requirement**. The custom launcher is
the product. The template path (`PlaybackService` + `MediaBrowseTree`) stays first-class —
it is needed for Assistant and voice search regardless — and a future `playstore` product
flavor drops the launcher for Play submission.

**The Play switch is build-time, not runtime.** A runtime feature flag cannot deliver Play
compliance: review inspects the shipped manifest, so an activity declared with a launcher
intent filter is present whether or not a flag hides it. This dictates where code lives
from day one and is why §6 exists.

**Brand is champagne gold, app-wide, but sequenced.** The design uses `#C9A84C`; the
shipping app uses `NyasaPrimary` `#A855F7`. Gold wins everywhere, but mobile migrates in a
separate project (see §1.2), so gold enters `:core:common` now as *new* token names rather
than repointing `NyasaPrimary`.

**Foundation before screens.** Tokens, primitives, flavors and the restriction layer land
before any screen work, because every screen depends on them and the restriction handler
currently contains a live bug (§7).

### 1.2 Out of scope — Project B

Rolling gold into mobile is a **separate project with its own spec**. It is not bundled
here because it touches every mobile screen and needs its own design review.

Measured blast radius, for whoever picks it up:

- 29 of 32 files reference `NyasaPrimary` **by name** and cascade automatically from
  `core/common/src/main/java/com/example/nyasaplayer/core/common/ui/theme/Color.kt`.
- 4 files hardcode the hex and need hand edits: `app/src/main/res/drawable/ic_launcher_background.xml`,
  `app/src/main/res/values/colors.xml` (two entries), `automotive/src/main/res/drawable/ic_launcher_background.xml`,
  plus a stale comment in `AutomotiveColors.kt`.

The token swap is cheap. The consequence is not:

| Foreground on primary fill | Contrast | Verdict |
|---|---|---|
| White on purple `#A855F7` | 3.96:1 | current mobile — already below AA for normal text |
| White on gold `#C9A84C` | 2.29:1 | unusable |
| Dark `#0A0A0C` on gold | 8.66:1 | what the AAOS design uses |

Gold is a light colour and purple is a dark one, so every filled CTA in mobile must invert
from white-on-primary to dark-on-primary. Project B is a **button-treatment change**, not a
colour swap. It also surfaces a pre-existing defect rather than causing one: white-on-purple
already fails AA for normal-size text today.

**Accepted interim state:** car is gold, phone is purple, until Project B lands.

---

## 2. The px → dp policy

The design is authored in **CSS px on a 1920 × 1080 canvas**. The codebase is in **dp**.
These are different units, and the repo already disagrees with itself:
`CarMiniPlayerHeight = 112.dp` versus the design's 88px mini-player.

**Rule: design px maps to dp 1:1**, treating the design canvas as a 1920 × 1080 dp logical
space — **except** where existing code holds a considered value, which wins.

Applied:

| Value | Design | Code today | Resolution |
|---|---|---|---|
| Touch target | 76px | `CarTouchTargetSize = 76.dp` | **76.dp.** Code and design agree independently; the existing comment already cites the CTS requirement. |
| Mini-player height | 88px | `CarMiniPlayerHeight = 112.dp` | **112.dp wins.** It exceeds the design intent and clears the touch target with room. Correct `aaos-DESIGN.md` to record 112dp. |
| Card corner radius | 20px | `CarCardCornerRadius = 16.dp` | **20.dp.** No considered rationale for 16; take the design value. |

This rule must be written into `docs/aaos-DESIGN.md` as part of A1. Without it, every screen
becomes a fresh argument about units, and the answer will differ per screen.

**Caveat that must not be lost:** dp is density-independent but the *physical* size still
depends on the head unit. These figures are correct for the design's logical space; they are
not a substitute for checking a real device.

---

## 3. Token boundary

Split by ownership, not module convenience.

### 3.1 `:core:common` — brand accent

New names added to `core/common/.../ui/theme/Color.kt`, alongside the existing
`NyasaPrimary` / `NyasaPrimaryDark`, which stay untouched until Project B:

```
NyasaGold        = #C9A84C   // primary accent, CTAs, active states, progress fill
NyasaGoldDim     = #7A6428   // inverse/disabled accent
NyasaGoldBright  = #E0C169   // hover / raised accent
NyasaOnGold      = #0A0A0C   // label colour on any gold fill — never white, see 1.2
```

`NyasaOnGold` exists as a named token specifically so the 2.29:1 white-on-gold mistake
cannot be made by reaching for `Color.White` out of habit.

### 3.2 `:automotive` — car-only surfaces

Stay in `AutomotiveColors.kt`. Mobile has no obsidian surface and never will:

```
CarObsidian       = #0A0A0C   // base
CarChrome         = #111118   // system bar, nav rail
CarGlass          = #181824   // cards, mini-player
CarRaised         = #1E1E2A   // elevated cards, inputs, chips
CarTextSecondary  = #ACACBC   // metadata — see contrast note below
CarTextDisabled   = #555568
CarAmbientBlue    = #1A3A5C
CarAmbientPurple  = rgba(100,60,180,0.3)
```

**Do not darken `CarTextSecondary` without re-measuring.** It was moved from `#A0A0B0` to
`#ACACBC` because `#A0A0B0` gave only 6.8:1 on cards — AA, not AAA. The binding surface is
`CarRaised #1E1E2A` at 7.4:1, **not** the base at 8.8:1. Measuring against the base gives a
false pass.

The existing `CarGradient*` values in `AutomotiveColors.kt` are content artwork gradients,
not brand, and are unaffected.

---

## 4. Dimension tokens

Extend `AutomotiveDimens.kt`:

```
CarTouchTargetSize    = 76.dp    // exists, unchanged
CarListArtSize        = 80.dp    // exists, unchanged
CarMiniPlayerHeight   = 112.dp   // exists, unchanged (see §2)
CarCardCornerRadius   = 20.dp    // CHANGED from 16.dp
CarSystemBarHeight    = 80.dp    // NEW
CarNavRailWidth       = 80.dp    // NEW
CarChipHeight         = 76.dp    // NEW
CarPillButtonHeight   = 76.dp    // NEW
CarListRowHeight      = 80.dp    // NEW
CarScreenMargin       = 48.dp    // NEW
```

**Why the system bar is 80 and not 48:** it carries app-tappable controls (search, settings,
avatar). A 48dp bar cannot contain a 76dp target. This is a structural consequence of the
touch-target rule, not a style preference.

---

## 5. Component primitives

### 5.1 `Modifier.carTouchTarget()`

```kotlin
fun Modifier.carTouchTarget(): Modifier =
    this.defaultMinSize(CarTouchTargetSize, CarTouchTargetSize)
```

Applied to every interactive element whose visual size is smaller than 76dp. The glyph keeps
its visual size; the modifier supplies the target.

**This is the single mechanism keeping targets compliant.** In the HTML prototype, nine
controls silently fell below the minimum — worst was a like button at 22×27 — introduced by
ordinary styling, not carelessness. Without one enforced primitive, Compose will regress the
same way, one component at a time.

### 5.2 New components (`automotive/ui/components/`)

| Component | Notes |
|---|---|
| `CarChip` | 76dp tall, selected = gold fill + `NyasaOnGold` label |
| `CarPillButton` | 76dp tall, gold and ghost variants |
| `CarTrackRow` | 80dp tall, art + title/artist + like + duration, playing-state gold bar |
| `CarSectionHeader` | section label |
| `CarEmptyState` | orb + title + body + CTA |
| `CarRestrictionDialog` | "Not available while driving" + reason + dismiss (§8) |

### 5.3 Existing components

`CarMiniPlayer`, `CarTopBar`, `CarErrorOverlay` are **re-themed to the new tokens, not
rewritten.** They work today.

`CarMiniPlayer` gains one behavioural change from the prototype: **artwork and title become a
single touch target** that opens the full player, rather than two separate smaller ones.

---

## 6. Product flavors

```kotlin
// automotive/build.gradle.kts
flavorDimensions += "distribution"
productFlavors {
    create("oem")       { dimension = "distribution"; isDefault = true }
    create("playstore") { dimension = "distribution" }
}
```

| Flavor | Manifest contains | Purpose |
|---|---|---|
| `oem` | `AutomotiveActivity` + launcher filter, `PlaybackService` | the product |
| `playstore` | `PlaybackService` only | future Play submission |

**Implementation:** `AutomotiveActivity` moves **out of** `src/main/AndroidManifest.xml` into
`src/oem/AndroidManifest.xml`. `src/playstore/AndroidManifest.xml` declares no launcher
activity. The service, its intent filters, and the
`androidx.car.app.launchable` meta-data stay in `main` — both flavors need them.

Both flavors must compile, and Detekt and Lint must pass for both.

**Do not** attempt this with a runtime flag or a `BuildConfig` boolean. See §1.1.

---

## 7. CarUxRestrictionsHandler — bug fix and extension

### 7.1 The bug

`automotive/.../viewmodel/CarUxRestrictionsHandler.kt` maps:

```kotlin
noTextEntry = flags and CarUxRestrictions.UX_RESTRICTIONS_NO_TEXT_MESSAGE != 0
```

`UX_RESTRICTIONS_NO_TEXT_MESSAGE` is the **messaging** restriction. It has nothing to do with
keyboard entry. The correct flag is `UX_RESTRICTIONS_NO_KEYBOARD`.

This is live, not theoretical: `AutomotiveApp.kt:270` passes
`isSearchDisabled = playerState.restrictions.isDistractionOptimized`, and
`isDistractionOptimized` is derived from `noTextEntry`. **Search is currently gated on the
wrong signal.**

### 7.2 Target state

| Field | Today | Target |
|---|---|---|
| `noTextEntry` | `UX_RESTRICTIONS_NO_TEXT_MESSAGE` | `UX_RESTRICTIONS_NO_KEYBOARD` |
| `noSetup` | absent | `UX_RESTRICTIONS_NO_SETUP` |
| `maxContentDepth` | absent | `getMaxContentDepth()` |
| `maxCumulativeContentItems` | present as `limitedContentItems` | keep, rename for clarity |
| `noVideo` | present | keep |
| `noFiltering` | present | keep |
| `isDistractionOptimized` | `noFiltering \|\| noTextEntry` | `isRequiresDistractionOptimization()` |

`isDistractionOptimized` should come from the platform's own answer rather than being
re-derived by ORing two flags, which is both indirect and wrong when either flag changes
meaning.

**API names verified** against `android.car.jar` (android-36) rather than assumed. The stub
confirms `UX_RESTRICTIONS_NO_KEYBOARD`, `UX_RESTRICTIONS_NO_SETUP`,
`UX_RESTRICTIONS_NO_TEXT_MESSAGE`, `getMaxContentDepth()`, `getMaxCumulativeContentItems()`
and `isRequiresDistractionOptimization()` all exist with those exact names. Note the `is`
prefix on the last one — Kotlin exposes it as the property `isRequiresDistractionOptimization`.

### 7.3 Testability

Extract the mapping to an **internal pure function**:

```kotlin
internal fun CarUxRestrictions.toUxState(): UxRestrictionState
```

Currently `private`, so it cannot be tested. As a pure function over flags it is exhaustively
testable on the JVM with no emulator and no Car service.

### 7.4 Lifecycle — verify, do not assume

`AutomotivePlayerViewModel` calls `connect()` at line 104 and `disconnect()` at 313, and
collects into `_uiState`. That wiring is correct today and must survive the change.

`connect()` returns early when `car != null` but does not handle `Car.createCar()` returning
null on a later attempt, so a failed first connect is permanent for the process lifetime.
Fixing this is **in scope** for A1 — the restriction layer failing open, silently, is exactly
the failure mode that matters here.

---

## 8. Restriction gating harness

A pure decision function plus a UI affordance.

```kotlin
sealed interface GateResult {
    data object Allowed : GateResult
    data class Denied(val reason: String) : GateResult
}

fun gate(destination: CarDestination, state: UxRestrictionState): GateResult
```

Rules, mirroring the validated prototype:

| Restriction | Effect |
|---|---|
| `noSetup` | Settings and profile switching denied |
| `noTextEntry` | Search text entry denied; voice search offered instead |
| `maxContentDepth` | Drill-down beyond the cap denied |
| `maxCumulativeContentItems` | Content lists truncated |

**Entry refusal is not sufficient.** When restrictions change while the user is already
inside a restricted destination, the harness must **evict** them — pop to a safe destination
and show `CarRestrictionDialog`. A vehicle can start moving at any moment; gating only the
entry path leaves the user sitting on a restricted screen while driving.

Denials show an explanation, never a silent no-op.

**Playback transport, seeking, queue and tab switching stay available while driving.** They
are not restricted, and blocking them would be wrong.

---

## 9. Verification

### 9.1 JVM unit tests — no new dependencies

- `CarUxRestrictions.toUxState()`: exhaustive over `NO_KEYBOARD`, `NO_SETUP`, `NO_VIDEO`,
  `NO_FILTERING`, and the depth/item caps, including the specific regression that
  `NO_TEXT_MESSAGE` alone must **not** set `noTextEntry`.
- `gate()`: every destination × parked/driving, including the eviction transition.

Both are pure functions. Tests live in `automotive/src/test/`, matching the existing pattern
in `core/data/src/test/`.

### 9.2 Prerequisite spike — driving-state injection

**Nothing in the repo can currently put the emulator into a driving state**, so the entire
restriction layer would ship unverified.

Establish and document an adb VHAL recipe (gear selection and vehicle speed) on the
`Automotive_Distant_Display_with_Google_Play` AVD (API 33), covering parked → driving →
parked, and record it in `docs/` alongside the existing template-verification notes.

**This spike blocks A1 acceptance.** If no reliable recipe exists, that is a finding to
report, not a step to skip.

### 9.3 Manual checklist

Per restriction: attempt entry while driving (expect refusal + dialog), enter while parked
then transition to driving (expect eviction), transition back (expect restored access).

**Custom launcher screens render on display 0 and screenshot normally** — unlike the
template's Now Playing, which renders on `FLAG_SECURE` distant-display surfaces where
`screencap` returns nothing. The custom path is materially easier to verify than the template
path.

### 9.4 Gates

Detekt (`maxIssues: 0`) and Android Lint must pass **for both flavors**.

---

## 10. Risks

| Risk | Mitigation |
|---|---|
| VHAL driving-state recipe may not exist or may be unreliable | Spike first (§9.2). Treat failure as a reportable finding. |
| Detekt `maxIssues: 0` — no warnings tolerated | Every new file clean on first commit; run Detekt before each. |
| Token migration breaks the existing 7 screens | They must compile and run throughout; re-theme, don't rewrite. |
| Flavor split breaks the existing build | Both flavors build + pass Detekt/Lint before A1 is accepted. |
| Mixed palette (car gold, phone purple) | Accepted and documented until Project B. |
| dp figures unverified on real hardware | Explicitly noted in §2; not a substitute for device testing. |

---

## 11. Definition of done

1. Gold tokens in `:core:common`; car surfaces in `:automotive`.
2. px→dp policy written into `docs/aaos-DESIGN.md`, with the 112dp mini-player correction.
3. Dimension tokens added; `CarCardCornerRadius` changed to 20.dp.
4. `Modifier.carTouchTarget()` plus the six new components.
5. `oem` / `playstore` flavors; both build and pass Detekt + Lint.
6. `CarUxRestrictionsHandler` fixed and extended; mapping extracted and pure.
7. `gate()` harness with entry refusal and eviction.
8. JVM unit tests for mapping and gate.
9. Driving-state adb recipe documented, or its absence reported.
10. Existing 7 screens still build and run, re-themed.

**Not in A1:** any new screen. Those follow in later slices against this foundation.