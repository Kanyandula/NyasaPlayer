# AAOS T1 - Compose Test Tooling

## 1. Problem

`:automotive` still has no JVM test that renders a Composable. The unit suite now covers ViewModel,
navigation and list-derivation rules well, but UI binding and visibility still depend on emulator
sessions, screenshots and manual coordinate taps.

That gap has already cost time:

- A4 could only inspect several Favourites UI behaviours.
- A6 found the `NO_KEYBOARD` Search CTA issue on device.
- T6 had to prove pointer-through by tapping coordinates and reading the resulting screen state.

T1 adds the smallest useful headless Compose test harness before T4, so multi-entity search can add
UI with executable checks instead of another manual-only screen slice.

## 2. Outcome

`./gradlew :automotive:testOemDebugUnitTest` must run Compose rendering tests on the JVM, without
an emulator and without an `androidTest` source set.

The first landing proves three known-risk behaviours:

1. Favourites route binding reads `favouritesLoading` and `favouritesError`, not the shared
   catalogue `isLoading` and `errorMessage`.
2. Search in `NO_KEYBOARD` mode shows the voice prompt and does not render an editable field, the
   Search CTA or the Songs shortcut.
3. Full-screen surfaces using `carConsumeTouches()` do not leak empty-space taps to the shell
   behind them, while their own child controls still receive taps.

The harness itself is the product. The test list is intentionally short.

## 3. Scope

- Add Robolectric to the version catalog and `:automotive` test configuration.
- Add Compose UI test support to the existing `automotive/src/test/` JVM source set.
- Enable Android resources for unit tests.
- Add at most three focused Compose test files.
- Add a small internal Favourites route seam if needed so the binding mutation is testable without
  rendering the full Hilt-backed app shell.
- Update stale comments that currently say `:automotive` has no Compose test tooling.

## 4. Out of scope

- No screenshot or golden-image testing.
- No emulator-dependent `androidTest` suite.
- No broad retrofit across every car screen.
- No T4 multi-entity search implementation.
- No mobile `:app` test changes.
- No Hilt integration test of the full `AutomotiveApp` unless the small route seam cannot prove
  the target binding.
- No mocking library for T2 unless Gradle resolution makes it free and explicitly useful here.

## 5. Requirements

- **R1:** Compose tests run under `:automotive:testOemDebugUnitTest`.
- **R2:** The new tests execute on a machine with no emulator or device attached.
- **R3:** A mutation that binds Favourites error UI to `contentState.errorMessage` fails by name.
- **R4:** A mutation that binds Favourites loading UI to `contentState.isLoading` fails by name.
- **R5:** A mutation that renders Search CTA or Songs under `canType = false` fails by name.
- **R6:** A mutation that removes `carConsumeTouches()` from a full-screen surface fails by name
  if the pointer-consumption test can be made stable under Robolectric.
- **R7:** Existing `:automotive` JVM tests continue to pass without production behavioural changes.

R6 is allowed to be downgraded to a documented follow-up only if Robolectric cannot exercise the
pointer path reliably. Do not fake a passing pointer test by asserting implementation details.

## 6. Test targets

### 6.1 Harness smoke

Render a trivial Composable with `createComposeRule()` under Robolectric. This test exists only
until a real screen test proves the harness; remove it if it becomes redundant in the same branch.

### 6.2 Favourites binding

Render the Favourites route with:

- `contentState.errorMessage = "catalogue error"`
- `contentState.favouritesError = "favourites error"`
- empty songs

Assert "Something went wrong", "favourites error" and "Try again" are visible, and the catalogue
error is not.

Render the same route with:

- `contentState.isLoading = true`
- `contentState.favouritesLoading = false`
- empty songs

Assert "No favourites yet" is visible. A wrong binding to shared loading would render the skeleton
instead.

### 6.3 Search `NO_KEYBOARD`

Render `CarSearchScreen(query = "worship", canType = false, ...)`.

Assert the voice prompt and "Browse by" are visible. Assert the Search CTA, "Songs" shortcut and
editable-field placeholder are absent.

### 6.4 Surface touch consumption

Render a small test-only layout with an underlying clickable and a top full-screen surface carrying
`carConsumeTouches()`. Tap empty top-surface space and assert the underlying click did not fire.
Then tap a child control inside the top surface and assert that child did fire.

This is a helper-level test. Do not render the whole shell just to reproduce the T6 device check.

## 7. Dependencies

Expected Gradle changes:

- `gradle/libs.versions.toml`
  - add `robolectric`
- `automotive/build.gradle.kts`
  - add `testImplementation(platform(libs.androidx.compose.bom))`
  - add `testImplementation(libs.androidx.ui.test.junit4)`
  - add `testImplementation(libs.robolectric)`
  - add `debugImplementation(libs.androidx.ui.test.manifest)` if required for the JVM Compose rule
  - set `testOptions.unitTests.isIncludeAndroidResources = true`

Keep the dependency block test-scoped. Do not add runtime dependencies.

## 8. Acceptance criteria

- [ ] `:automotive:testOemDebugUnitTest` runs at least two real Compose rendering tests.
- [ ] Favourites error and loading binding regressions fail by name.
- [ ] Search `NO_KEYBOARD` CTA and Songs regressions fail by name.
- [ ] Pointer consumption is tested, or a specific Robolectric limitation is recorded.
- [ ] The branch does not add screenshot baselines or emulator-only tests.
- [ ] `./gradlew :automotive:testOemDebugUnitTest detekt :automotive:lintOemDebug
      :automotive:assembleOemDebug :automotive:assemblePlaystoreDebug` passes.

## 9. Follow-ups

- T4 should add its own Compose tests for multi-entity result rendering once T1 lands.
- T2 may still need a mocking library for auth ViewModel construction. Do not silently bundle that
  into T1 unless T1's actual tests require it.
