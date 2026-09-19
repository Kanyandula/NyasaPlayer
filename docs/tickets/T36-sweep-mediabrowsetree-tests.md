# T36 - `MediaBrowseTree`'s 17 tests have never been challenged

- **Slice:** test quality, `:core:playback`
- **Depends on:** nothing
- **Status:** Filed 2026-09-19 out of the mutation sweep's own "not covered" list
- **Verification Command:** the sweep harness, plus `./gradlew :core:playback:testDebugUnitTest`
- **Design Reference:** `docs/TEST_MUTATION_SWEEP.md` ("The 32 not covered")
- **Risk Tags:** unproven tests, the car's browse surface
- **Affected Modules:** `:core:playback` (tests; production code only if a weakness is found)

## Problem

The 2026-09-19 mutation sweep of `:core:playback` challenged 74 of 106 tests. `MediaBrowseTreeTest`
is the largest untouched block — **17 tests** — and the reason is mechanical, not principled:

> only `getChildren` was mutated; `getItem` and `search` mutations did not compile

A test that has never failed for the right reason is not known to work. This is not a claim that
those 17 are weak; two tests in this same codebase were found green for the wrong reason during
this sweep's own follow-up, which is why "no weakness shown" is not the same as "no weakness".

`MediaBrowseTree` is the browse and search surface the car's OEM template drives, so the tests
guarding it are worth more than most.

## Scope

- Find mutations of `getItem` and `search` that **compile**. The earlier attempts failed at
  `kspDebugKotlin`, not at the assertion — the mutation was never run.
- One mutation per behaviour the tests claim to guard; a predicate needs both of its constants
  broken, and a test that asserts nothing happened needs the mutation that makes something happen.
- Record the result in `docs/TEST_MUTATION_SWEEP.md` in the existing table shape, including any
  mutation that survived.

## Out Of Scope

- The other 15 unswept tests in the sweep's list (`PlayableItemsTest`, `ConnectedTransportTest`,
  `ControllerConnectionTest`, `PlaybackStatePersistenceTest`, `SharedControllerFutureTest`). They
  have named reasons; this ticket is the one block with no reason but a compile error.
- Rewriting any test that survives its mutation — that becomes its own ticket with the surviving
  mutation attached as evidence.

## Acceptance Criteria

- Every one of the 17 `MediaBrowseTreeTest` cases has been run against at least one compiling
  mutation of the code it covers, and the outcome — killed or survived — is recorded.
- Any test that survived every mutation aimed at it is named explicitly, not folded into a summary.

## Notes

**The harness trap from the first sweep applies here.** A run with no `tests completed` line in
gradle's output is invalid, not a result: the first sweep script read two `kspDebugKotlin` failures
as "0 tests failed". Check for compilation before believing a zero.
