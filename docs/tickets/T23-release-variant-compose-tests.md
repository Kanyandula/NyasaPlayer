# T23 - `./gradlew test` is red because the release variant cannot launch a Compose test

- **Slice:** build, developer-facing gate
- **Depends on:** —
- **Status:** Filed, not specced
- **Verification Command:** `./gradlew test`
- **Design Reference:** —
- **Risk Tags:** build config, false-red gate
- **Affected Modules:** `:automotive` (`:app` is exposed to the same trap)

## Problem

`./gradlew test` fails on `main`. `:automotive:testOemReleaseUnitTest` reports 20 failures, every
one of them:

```
java.lang.RuntimeException: Unable to resolve activity for Intent { act=android.intent.action.MAIN
cat=[android.intent.category.LAUNCHER] cmp=com.example.nyasaplayer/androidx.activity.ComponentActivity }
    at org.robolectric.android.internal.RoboMonitoringInstrumentation.startActivitySyncInternal
    at androidx.compose.ui.test.junit4.AndroidComposeTestRule$apply$1$evaluate$1.invoke
```

`createComposeRule()` launches `androidx.activity.ComponentActivity`, which reaches the merged
manifest only through the `ui-test-manifest` AAR. `automotive/build.gradle.kts:131` wires that as
`debugImplementation` — correctly, and with a comment saying why it cannot be `testImplementation` —
so the release variant has no such activity and every test that needs one dies in `@Before`.

The correlation is exact: all 8 test classes that call `createComposeRule` fail (`AuthGateTest`,
`CarFavouritesRouteTest`, `CarConsumeTouchesTest`, `CarModalTest`, `CarSystemBarTest`,
`CarAccountSheetsTest`, `CarSearchResultsScreenTest`, `CarSearchScreenTest` — 20 tests), and all 17
classes that do not, pass. Nothing is wrong with the tests or with the release build; they are being
run against a variant that structurally cannot host them.

## Why it went unnoticed

The documented gate is per-variant — `:automotive:testOemDebugUnitTest` — and the pre-commit hook
runs Detekt and Lint, not tests. There is no CI workflow in this repo, so `./gradlew test` is only
ever typed by hand, and typing it is how this was found (during T15, PR #53).

## Scope

Pick one and apply it to `:automotive`, then check `:app`, which has the identical
`debugImplementation(libs.androidx.ui.test.manifest)` at `app/build.gradle.kts:131` and is one
Compose unit test away from the same red.

1. **Disable unit tests on the release variant** (recommended) — AGP native, one block:
   `androidComponents { beforeVariants(selector().withBuildType("release")) { it.enableUnitTest = false } }`.
   States the fact directly: these tests belong to the debug variant. `./gradlew test` goes green and
   stays green as tests are added.
2. **Move the 8 Compose classes to a debug-only test source set** (`src/testOemDebug/java`). Keeps
   release unit tests running for the logic tests, at the cost of splitting the test tree and having
   to remember which half a new test belongs in.
3. **Do nothing, document it** — teach `./gradlew test` as "not the gate, use the per-variant
   command". Cheapest, and it leaves a red build for the next person to rediscover.

`releaseImplementation(libs.androidx.ui.test.manifest)` is **not** an option: it merges a
test-only launcher activity into the shipped app.

## Out Of Scope

- The tests themselves. Not one of them is failing on its own merits.
- Adding CI. Worth its own ticket — this bug is a symptom of not having any, but fixing the variant
  config does not depend on it.

## Acceptance Criteria

- Given a clean tree on `main`, when `./gradlew test` runs, then it succeeds.
- Given the fix, then every test that runs today under `:automotive:testOemDebugUnitTest` still runs.
- Given a new Compose unit test added to `:app`, then it does not reintroduce this failure.

## Notes

Found while running the full suite for T15 (PR #53). Confirmed pre-existing by stashing that branch's
changes and running `:automotive:testOemReleaseUnitTest` against a clean `main`: same 20 failures.
