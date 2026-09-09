# AAOS A7 - Settings and Profile Implementation Plan

> **For agentic workers:** the driving gate, the sheet enum and the two system-bar callbacks already
> exist. This slice fills them in. Do not add nav destinations, do not build restriction plumbing,
> and do not ship a control that stores a preference nothing reads.

**Goal:** The gear and the avatar in the system bar do something, sign-out moves off the Library
screen where it never belonged, and both new surfaces are refused while driving by the gate that is
already written.

**Spec:** `docs/superpowers/specs/2026-08-27-aaos-a7-settings-profile-design.md`

**Verification command:** `./gradlew :automotive:testOemDebugUnitTest detekt`

**Broader gate:** the usual set, plus a parked-and-driving device pass.

## Current baseline

Verified in code before writing this:

- `CarSheet` is `enum class CarSheet { Settings, Profile, Search }` — the two new members are
  already there and unused.
- `GateResult.gate()` already returns `Denied` with a written reason for
  `noSetup && sheet == CarSheet.Settings` and the same for `Profile`.
- `AutomotiveApp` holds `var sheet by rememberSaveable { mutableStateOf<CarSheet?>(null) }`, renders
  the search sheet with `if (sheet == CarSheet.Search)`, and runs `gate(location, restrictions)` in
  a `LaunchedEffect` keyed on both — so a vehicle that starts moving while a sheet is open already
  evicts.
- `CarSystemBar(onSearchClick, onSettingsClick, onAvatarClick)` takes all three callbacks and
  ignores two behind `@Suppress("UnusedParameter")`; `SystemBarControl` renders a disabled control
  when `onClick` is null.
- `CarLibraryScreen` owns the sign-out button and its confirmation overlay (D14, "marked for
  deletion in A7").

## Decisions

> **Numbering:** `docs/aaos-DESIGN.md` runs one flat D-sequence, so these landed as
> **D66** (a profile is an app account), **D67** (PIN deferred), **D68** (sign-out moves to
> Settings, confirmation hoisted to the shell), **D69** (sheets, not destinations; no parked
> badge) and **D70** (only rows something reads). The `D-A7.x` labels below are the drafting
> names — code and docs reference the D66–D70 ones.

Carried from the spec, restated because implementation will be tempted by each:

### D-A7.1: Sheets, not destinations

`CarSheet.Settings` and `CarSheet.Profile` are the whole navigation change. A destination would need
its own gating path for a restriction the sheet gate already refuses by name.

### D-A7.2: A profile is an app-level account

Switching means signing out and signing in as someone else. The app does not ask `CarUserManager` to
switch the vehicle's user: that needs privileged permissions it does not hold, and it is a
whole-vehicle action behind a media app's avatar.

For A7 the switcher therefore shows the current account and offers sign-out; **it does not remember
a second account**, because credential storage is a separate decision. Say that on the screen rather
than implying a switcher that cannot switch.

### D-A7.3: No PIN affordance at all

Not a disabled one. A disabled control is a promise, and A7 has nothing behind it (spec, question 2).

### D-A7.4: Only rows that do something

Settings ships: account (read-only), sign out, and about (version). **No audio-quality row** until
something reads a quality preference — `PlaybackService` does not. A stored preference nothing
observes is FR-2.6 wearing a different hat.

## Task 0: Baseline and branch

- [x] `main` clean and containing PR #50.
- [x] Branch; run the focused gate once.

## Task 1: `CarSettingsScreen`

- [x] New composable in `auto/ui/screens/`, following the sheet shape `CarSearchScreen` uses.
- [x] Content: account row (display name), sign out, about row with the app version. Nothing else.
      **Two deviations, both recorded as D69:** no parked badge (the gate makes the screen
      unreachable while driving, so the badge would name a state the reader cannot be in), and no
      email — `CarAuthUiState` carries `displayName` only, and adding a field to show a second
      copy of the same identity was not worth a ViewModel change.
- [x] Reuse `CarSectionHeader`, `CarPillButton` and the existing sign-out confirmation overlay
      pattern rather than inventing chrome — check `ui/components/` before writing anything new.
- [x] Wire `onSettingsClick` in `AutomotiveApp` to `sheet = CarSheet.Settings`, and render it beside
      the search sheet.

**Acceptance criteria:** the gear opens it, close returns to the tab underneath, and nothing about
the search sheet changes.

## Task 2: `CarProfileSwitcherScreen`

- [x] New composable: current account, and a sign-out action that doubles as "switch account", worded
      so it does not promise a switcher that remembers two accounts (D-A7.2).
- [x] Wire `onAvatarClick` to `sheet = CarSheet.Profile`.

**Acceptance criteria:** the avatar opens it; its sign-out path is the same one Settings uses, not a
second copy.

## Task 3: Enable the two controls

- [x] `SystemBarControls` passes `onSettingsClick` and `onAvatarClick` through to their
      `SystemBarControl`s.
- [x] Remove `@Suppress("UnusedParameter")` from `CarSystemBar` — if it still compiles with the
      suppression removed, the parameters are genuinely used now.
- [x] Update the KDoc: it currently says both controls "stay disabled until A7 gives them
      destinations". They now have sheets.

**Acceptance criteria:** all three controls are live, and the disabled path in `SystemBarControl`
still exists for whatever needs it next.

## Task 4: Sign-out leaves the Library

- [x] Delete the sign-out button and its confirmation overlay from `CarLibraryScreen`, and the
      `onSignOutClick` parameter if nothing else uses it.
- [x] Check `CarFavouritesRouteTest` and any other automotive test that renders the Library — the
      button's removal may break a test that reaches for it.
- [x] D14 says this is conditional on Settings existing. Do this **after** Task 1, not before.

**Acceptance criteria:** the Library has no account chrome, and signing out is still possible from
two places that both go through Settings' path.

## Task 5: Tests

`:automotive` has Robolectric Compose tests (T1) and the `GateResult` logic is plain Kotlin.

- [x] Gate tests: `noSetup` with `sheet = Settings` and with `sheet = Profile` both deny, with their
      written reasons. These may already exist — check `CarRestrictionGateTest` before adding.
- [x] A Compose test that the gear opens the settings sheet and close dismisses it, in the style of
      `CarFavouritesRouteTest`. **Split in two, not one end-to-end test:** `CarSystemBarTest` asserts
      the gear reaches its callback (a disabled control would count zero clicks), and
      `CarAccountSheetsTest` asserts the sheet renders and closes. The one link between them —
      `AutomotiveApp` assigning `sheet = CarSheet.Settings` — sits behind three Hilt ViewModels and
      is not worth a hosted test; the device pass covered it.
- [x] A test that the Library no longer offers sign-out, so its removal cannot be undone silently.
- [x] Added while running the device pass: `idling_allowsSettingsAndProfile`, the case injection
      cannot reach (see Task 6).

## Task 6: Device pass

- [x] Parked: gear opens Settings, avatar opens Profile, both close, the Library carries no account
      chrome, and the full sign-out round trip works — Sign Out → confirm evicts to `CarAuthScreen`
      in the same process (pid 9617 either side, so it is a state transition, not a restart), and
      Cancel returns to the sheet. Verified 2026-08-27 on `AAOS_AOSP_33_userdebug`, user 10.
- [x] Driving, on `AAOS_AOSP_33_userdebug` (2026-08-27). `inject-vhal-event 0x11400400 8` plus
      `inject-continuous-events 0x11600207 40 -s 5 -d N`, oracles reading
      `Current Driving State: 2` / `DO: true UxR: 255` throughout:
      - Settings open, vehicle starts moving → sheet evicted to the tab root, dialog reads
        *"Settings can only be changed while the vehicle is parked."*
      - Profile open, vehicle starts moving → evicted, dialog reads *"Profiles can only be switched
        while the vehicle is parked."*
      - Gear tapped while moving, from a dismissed-dialog state → refused, Settings reason.
      - Avatar tapped while moving, from a dismissed-dialog state → refused, Profile reason. The two
        reasons differ, which is what proves each refusal is new rather than a stale dialog.
      - Back to PARK → `DO: false UxR: 0`, and the gear opens Settings again.

      **Idling was not reachable by injection** — sending speed 0 while the gear is in DRIVE leaves
      the vehicle `MOVING`, because the moving config's range starts at 0.0 inclusive (the recipe
      says the same about the Play image's GUI). It matters: idling is `DO: true` with `UxR: 16`,
      so a gate reading `isDistractionOptimized` alone would refuse Settings at a red light.
      Covered by `CarRestrictionGateTest.idling_allowsSettingsAndProfile` instead, which is where
      it belongs.
- [x] Signed back in afterwards. The Google picker resolved without a prompt (one account on the
      device), the shell came back on Home with content and the mini-player intact, and Settings
      reads the same account. The emulator is left as it was found.

## Task 7: Docs

- [x] Design record for D-A7.1 to D-A7.4.
- [x] Update `docs/aaos-DESIGN.md` D14: its condition is met and sign-out has moved.
- [x] Update `CarSystemBar`'s KDoc and the screen contract's phase column for screens 14 and 20.
- [x] Record what A7 did **not** ship — the PIN screen, phone/email sign-in, audio quality — as
      tickets rather than prose, so they are not lost.
