# AAOS T1 - Compose Test Tooling Implementation Plan

> **For agentic workers:** This is a single small PR before T4. Do not turn it into a broad UI
> testing retrofit. The goal is to add the harness and a few tests that catch known recent UI
> regressions by name.

**Goal:** Add headless JVM Compose tests to `:automotive`, then prove the harness with the smallest
set of high-value screen tests.

**Ticket:** `docs/tickets/T1-automotive-compose-test-tooling.md`

**Spec:** `docs/superpowers/specs/2026-08-21-aaos-compose-test-tooling-design.md`

**Branch:** `ek/aaos-t1-compose-test-tooling`, base `main`.

**Tech stack:** Kotlin, Jetpack Compose UI test, Robolectric, JUnit 4, Gradle Kotlin DSL.

## Current baseline

Start from `main` after T5, T6 and T8 are merged. Current main already contains:

- `carConsumeTouches()`
- D41 and D42 in `docs/aaos-DESIGN.md`
- `docs/AAOS_T5_T6_VERIFICATION.md`
- `rememberVisible()`
- the T8 measurement deferral

The code still has comments saying there is no Compose test tooling. T1 should remove or update
those comments where it touches the surrounding code.

## Constraints

- No `androidTest` source set.
- No screenshot or golden tests.
- No broad screen retrofit.
- No T4 UI work.
- Keep dependencies test-scoped.
- Prefer testing leaf routes/screens over the full Hilt-backed `AutomotiveApp`.
- Any production-code edit must be a test seam or stale-comment cleanup, not a behavioural change.

## File plan

**Modify**

| File | Change |
|---|---|
| `gradle/libs.versions.toml` | Add Robolectric version/library |
| `automotive/build.gradle.kts` | Add JVM Compose/Robolectric test dependencies and resources |
| `automotive/.../ui/AutomotiveApp.kt` | Add a tiny internal Favourites route seam if needed |
| `docs/tickets/T1-automotive-compose-test-tooling.md` | Move status and link spec/plan |

**Create**

| File | Responsibility |
|---|---|
| `automotive/src/test/.../ui/screens/CarFavouritesRouteTest.kt` | Binding regression tests |
| `automotive/src/test/.../ui/screens/CarSearchScreenTest.kt` | `NO_KEYBOARD` visibility test |
| `automotive/src/test/.../ui/components/CarConsumeTouchesTest.kt` | Pointer-consumption helper test |

If `CarConsumeTouchesTest` is unstable under Robolectric, replace it with a verification note in
the ticket and do not keep a weak test.

## Task 1: Add the headless Compose harness

**Purpose:** Make `createComposeRule()` run inside `:automotive:testOemDebugUnitTest`.

- [ ] Add `robolectric` to `gradle/libs.versions.toml`.
- [ ] In `automotive/build.gradle.kts`, add:

```kotlin
testImplementation(platform(libs.androidx.compose.bom))
testImplementation(libs.androidx.ui.test.junit4)
testImplementation(libs.robolectric)
```

- [ ] Add `debugImplementation(libs.androidx.ui.test.manifest)` only if the JVM Compose rule needs
      it for a manifest/activity host.
- [ ] Set `testOptions.unitTests.isIncludeAndroidResources = true`.
- [ ] Add a temporary harness smoke test only if the first real test is not ready in the same
      edit. Delete the smoke test before final verification if a real render test covers it.
- [ ] Run `./gradlew :automotive:testOemDebugUnitTest --tests "*Compose*"` or the exact new test
      class name.

**Acceptance criteria:** a JVM test under `automotive/src/test/` renders Compose without an
attached emulator or device.

## Task 2: Make Favourites route binding testable

**Purpose:** Catch the original T1 mutant: Favourites UI accidentally reads shared catalogue
loading/error state.

Preferred implementation:

- Extract the Favourites branch inside `BrowseShell` into an `internal` composable, for example
  `CarFavouritesRoute`.
- Keep it in `AutomotiveApp.kt` unless moving it to a small route file makes imports clearer.
- Pass the same callback shape the branch already uses.
- Do not change `CarFavouriteMusicScreen` behaviour.

Checklist:

- [ ] Extract only the Favourites branch. Do not expose the full `BrowseShell` unless needed.
- [ ] Render the route in `CarFavouritesRouteTest`.
- [ ] Test error binding:
      `errorMessage = "catalogue error"` and `favouritesError = "favourites error"` must display
      the favourites error only.
- [ ] Test loading binding:
      `isLoading = true` and `favouritesLoading = false` with no songs must show
      "No favourites yet", not the skeleton.
- [ ] Mutation-check locally by temporarily swapping the route bindings and confirming the tests
      fail, then revert the mutation.
- [ ] Run `./gradlew :automotive:testOemDebugUnitTest --tests "*CarFavouritesRouteTest*"`.

**Acceptance criteria:** Favourites route binding regressions fail by name and the production UI is
unchanged.

## Task 3: Add two focused screen/helper tests

**Purpose:** Convert the A6/T6 manual checks most likely to matter before T4 into executable tests.

Search test:

- [ ] Render `CarSearchScreen(query = "worship", canType = false, ...)`.
- [ ] Assert the voice prompt is visible.
- [ ] Assert "Search", "Songs" and the editable-field placeholder are absent.
- [ ] Assert "Browse by" remains visible.

Pointer helper test:

- [ ] Render a test-only layout with an underlying clickable counter.
- [ ] Place a full-size top Box with `carConsumeTouches()`.
- [ ] Click empty top-surface space and assert the underlying counter stays zero.
- [ ] Add a clickable child inside the top surface and assert the child still receives its click.
- [ ] If this is flaky or unsupported under Robolectric, remove the test and record the exact
      limitation in the ticket.

Run:

```bash
./gradlew :automotive:testOemDebugUnitTest \
  --tests "*CarSearchScreenTest*" \
  --tests "*CarConsumeTouchesTest*"
```

**Acceptance criteria:** Search `NO_KEYBOARD` visibility is covered; pointer consumption is either
covered or honestly documented as still manual-only.

## Task 4: Final verification and docs

**Purpose:** Keep T1 small, reviewed and ready before T4 starts.

- [ ] Update `docs/tickets/T1-automotive-compose-test-tooling.md` status to implemented only after
      the code lands.
- [ ] Record final test count and any pointer-test carve-out in the ticket or a small verification
      note.
- [ ] Run:

```bash
./gradlew :automotive:testOemDebugUnitTest detekt \
  :automotive:lintOemDebug \
  :automotive:assembleOemDebug \
  :automotive:assemblePlaystoreDebug
```

- [ ] Run code review before creating the PR.
- [ ] Create one PR from `ek/aaos-t1-compose-test-tooling` to `main`.
- [ ] Do not start T4 implementation from a branch that lacks T1 unless T1 is explicitly blocked.

## Definition of done

- `:automotive:testOemDebugUnitTest` contains real Compose rendering tests.
- The Favourites binding mutant from the ticket is killed.
- The A6 `NO_KEYBOARD` Search CTA regression is covered.
- T6 pointer consumption is covered or has a precise Robolectric limitation recorded.
- No emulator is required for the new tests.
- The full local gate passes and the PR is reviewed before merge.
