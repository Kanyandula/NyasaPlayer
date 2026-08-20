# T1 — Compose test tooling for `:automotive` (Robolectric, headless)

- **Slice:** tooling — not a feature slice
- **Depends on:** nothing
- **Status:** Ready to spec
- **Verification Command:** `./gradlew :automotive:testOemDebugUnitTest`
- **Design Reference:** `docs/superpowers/specs/2026-08-09-aaos-favourites-design.md` §9 items 7–10 · `.claude/loop/ledger.md` row #11
- **Risk Tags:** test infrastructure · build configuration
- **Affected Modules:** `:automotive` · `gradle/libs.versions.toml`

## Problem

**Nothing in `:automotive` executes a Composable.** The module has 74 passing unit tests and none
of them render anything, so every assertion about what a screen *shows* is really an assertion
about what a ViewModel *holds*.

This was found by mutation, not by reasoning. Row #8 of the A4 post-merge review fixed Favourites
reading a shared `errorMessage` field that genres and albums also write, so a genres failure
displaced the empty state screen 17 is supposed to own. The fix points the screen at a dedicated
`likedSongsError` instead. Reverting that one line —

```kotlin
// AutomotiveApp.kt:483
errorMessage = contentState.errorMessage,      // the defect
errorMessage = contentState.favouritesError,   // the fix
```

— **reintroduces row #8's exact defect and passes all 74 tests.**

The immediate cause was that the tests mirrored the screen's binding by hand: a private helper in
`FavouritesBoundaryTest.kt` returned the same field the Composable read, so the two copies could
drift apart in silence. That mirror has since been collapsed into production
(`AutomotiveContentState.favouritesLoading` / `.favouritesError`, read by both the screen and the
tests), which converted two mutations from surviving to killed. **The residual survives:** a
Composable can still bypass those derivations entirely and nothing notices.

Collapsing the copies was all that could be done without tooling. Closing it needs a test that
renders `CarFavouriteMusicScreen` and asserts what appears.

## This runs headless on the JVM

**No `androidTest` source set. No device. No emulator.** `createComposeRule` runs under
Robolectric in the existing `automotive/src/test/` source set, the same place the current 74 tests
already run, on the same `./gradlew :automotive:testOemDebugUnitTest` command.

This is stated emphatically because the review loop spent a round believing the opposite, and the
gap was nearly reported to the human as "the platform cannot verify this". It can. The accurate
statement is **"we have not added Robolectric"** — a decision, not a limitation. Anyone re-scoping
this ticket should not re-derive the wrong conclusion.

## User Impact

**Who:** indirectly every AAOS driver; directly whoever next changes a car screen.

**What happens if we do nothing:** the A4 review closed fourteen defects, and the class of defect
that produced six of them — a screen bound to the wrong piece of state — remains undetectable by
the suite. The next regression of that shape ships green. Screen 17 is the concrete casualty:
`D21` requires it reachable in every state, and its reachability is currently guaranteed by one
unguarded line.

## Scope

Three additions, all build configuration:

1. **Robolectric into `gradle/libs.versions.toml`** — absent from the catalog entirely, not merely
   unwired.
2. **`androidx-ui-test-junit4` into `:automotive`'s `testImplementation`** — already declared in
   the catalog at `libs.versions.toml:67` and referenced by no module.
3. **`testOptions.unitTests.isIncludeAndroidResources = true`** in
   `automotive/build.gradle.kts`, beside the `isReturnDefaultValues = true` that A4 added for
   `android.util.Log`.

Then at least one rendering test that fails when `AutomotiveApp.kt`'s Favourites branch is pointed
back at the shared `errorMessage`. That test is the deliverable; the three lines are only what
makes it possible.

## Out Of Scope

- **Screenshot or golden-image testing.** A semantics-level assertion closes row #11. Pixel
  comparison is a much larger commitment — baseline storage, device/font variance, a review
  workflow — and is not required here.
- **An `androidTest` source set.** See above; it would add an emulator dependency to CI for no
  gain over Robolectric on this problem.
- **Retrofitting rendering tests across all seven car screens.** One test that kills the row #11
  mutant closes the row. Broader coverage is a judgement call for whoever owns the next slice.
- **The mobile app.** `:app` has its own test story and is not implicated.

## Acceptance Criteria

- Given `AutomotiveApp.kt`'s Favourites branch binds `errorMessage` to `contentState.errorMessage`
  instead of `contentState.favouritesError`, when the suite runs, then it fails.
- Given the same mutation to `isLoading` (`contentState.isLoading` instead of
  `contentState.favouritesLoading`), when the suite runs, then it fails.
- Given no emulator or device is attached, when `./gradlew :automotive:testOemDebugUnitTest` runs,
  then the rendering tests execute and pass.
- Given `detekt` runs, then it passes at `maxIssues: 0`. Note that detekt's `source` in
  `build.gradle.kts` lists only `*/src/main/java`, so it never analyses test sources — a green
  detekt says nothing about the new tests.
- Given the 74 existing tests, when the tooling lands, then all still pass and none needed editing
  to accommodate it.

## Also retires

A4's definition of done items **7, 8, 9 and 10** all rest on inspection rather than execution,
because nothing could render a Composable:

- item 7 — `CarArtistLikedSongsScreen` has hero, Play all, Shuffle and hearts
- item 8 — `CarTrackRow` renders a heart **only** when `onLikeToggle` is supplied, and Home and the
  detail screens are unchanged by their defaults
- item 9 — the heart's touch target is ≥76dp via `carTouchTarget()` and carries a
  `contentDescription` of "Unlike"/"Like"
- item 10 — `CarEmptyFavouritesScreen` renders in place at the tab root with a working Browse CTA

None of these are known to be wrong. All four are currently defended by someone having looked.

## Affected Areas

- **Modules:** `:automotive`
- **Build:** `gradle/libs.versions.toml`, `automotive/build.gradle.kts`
- **Tests:** `automotive/src/test/` — new rendering test alongside the existing seven files
- **Storage:** none
- **Production code:** none. This ticket adds no production change and should not.

## Risk Areas

- **Security:** none. Test-scope dependency only.
- **Performance:** Robolectric is slower than plain JUnit; the module's suite is small enough that
  this is unlikely to matter, but watch total task time if rendering tests proliferate.
- **Compatibility:** verify the Robolectric version supports the module's `compileSdk 35`.
  `android.car` APIs are `compileOnly` from the platform jar and are **not** available under
  Robolectric — a rendering test must not touch `CarUxRestrictionsHandler` or anything reading
  `android.car`. Render the screen with restriction values passed in as plain parameters, which is
  how `CarFavouriteMusicScreen` already takes them.
- **Data migration:** none.

## Notes

Ticket T2 needs a mocking library for the same module, from the other direction —
`AutomotiveAuthViewModel`'s constructor takes `FirebaseSyncManager`, a concrete class wrapping
`FirebaseFirestore` and four DAOs, and `:automotive` has junit and coroutines-test only. Whoever
picks up either ticket should look at both, since the answer may be one dependency block rather
than two.
