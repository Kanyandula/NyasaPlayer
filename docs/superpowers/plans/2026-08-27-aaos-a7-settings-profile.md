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

- [ ] `main` clean and containing PR #50.
- [ ] Branch; run the focused gate once.

## Task 1: `CarSettingsScreen`

- [ ] New composable in `auto/ui/screens/`, following the sheet shape `CarSearchScreen` uses.
- [ ] Content: parked badge, account row (display name and email from `CarAuthUiState`), sign out,
      about row with the app version. Nothing else (D-A7.4).
- [ ] Reuse `CarSectionHeader`, `CarPillButton` and the existing sign-out confirmation overlay
      pattern rather than inventing chrome — check `ui/components/` before writing anything new.
- [ ] Wire `onSettingsClick` in `AutomotiveApp` to `sheet = CarSheet.Settings`, and render it beside
      the search sheet.

**Acceptance criteria:** the gear opens it, close returns to the tab underneath, and nothing about
the search sheet changes.

## Task 2: `CarProfileSwitcherScreen`

- [ ] New composable: current account, and a sign-out action that doubles as "switch account", worded
      so it does not promise a switcher that remembers two accounts (D-A7.2).
- [ ] Wire `onAvatarClick` to `sheet = CarSheet.Profile`.

**Acceptance criteria:** the avatar opens it; its sign-out path is the same one Settings uses, not a
second copy.

## Task 3: Enable the two controls

- [ ] `SystemBarControls` passes `onSettingsClick` and `onAvatarClick` through to their
      `SystemBarControl`s.
- [ ] Remove `@Suppress("UnusedParameter")` from `CarSystemBar` — if it still compiles with the
      suppression removed, the parameters are genuinely used now.
- [ ] Update the KDoc: it currently says both controls "stay disabled until A7 gives them
      destinations". They now have sheets.

**Acceptance criteria:** all three controls are live, and the disabled path in `SystemBarControl`
still exists for whatever needs it next.

## Task 4: Sign-out leaves the Library

- [ ] Delete the sign-out button and its confirmation overlay from `CarLibraryScreen`, and the
      `onSignOutClick` parameter if nothing else uses it.
- [ ] Check `CarFavouritesRouteTest` and any other automotive test that renders the Library — the
      button's removal may break a test that reaches for it.
- [ ] D14 says this is conditional on Settings existing. Do this **after** Task 1, not before.

**Acceptance criteria:** the Library has no account chrome, and signing out is still possible from
two places that both go through Settings' path.

## Task 5: Tests

`:automotive` has Robolectric Compose tests (T1) and the `GateResult` logic is plain Kotlin.

- [ ] Gate tests: `noSetup` with `sheet = Settings` and with `sheet = Profile` both deny, with their
      written reasons. These may already exist — check `CarRestrictionGateTest` before adding.
- [ ] A Compose test that the gear opens the settings sheet and close dismisses it, in the style of
      `CarFavouritesRouteTest`.
- [ ] A test that the Library no longer offers sign-out, so its removal cannot be undone silently.

## Task 6: Device pass

- [ ] Parked: gear opens Settings, avatar opens Profile, both close, sign-out works from Settings and
      returns to `CarAuthScreen`.
- [ ] Driving (`inject-vhal-event 0x11400400 8` plus the continuous events from
      `docs/AAOS_DRIVING_STATE_TESTING.md`): both controls refuse with the gate's reason, and a sheet
      open when the vehicle starts moving is evicted.
- [ ] Sign back in afterwards — the emulator's account is the one every other device pass depends on.

## Task 7: Docs

- [ ] Design record for D-A7.1 to D-A7.4.
- [ ] Update `docs/aaos-DESIGN.md` D14: its condition is met and sign-out has moved.
- [ ] Update `CarSystemBar`'s KDoc and the screen contract's phase column for screens 14 and 20.
- [ ] Record what A7 did **not** ship — the PIN screen, phone/email sign-in, audio quality — as
      tickets rather than prose, so they are not lost.
