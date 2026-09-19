# Mutation sweep — `:core:playback`, 2026-09-19

Two tests were found this week that passed for the wrong reason: T14's
`repeatedFailuresWhileReconnecting_doNotQueueAttempts` (#84) and
`aSuccessfulReconnect_reportsNothingToTheSurface` (#85). Both asserted that nothing bad happened,
which is also true when the setup silently did nothing. This is the sweep that asked whether more
of the module is like that.

## Method

Break one thing in production code, run the module's 106 tests, and record which fail. A test that
fails has detected the break. A test that passes either does not touch that path, or does not check
what it claims to.

Predicates need **both** constants — a test asserting `true` cannot detect "always true" — and
"asserts nothing happened" tests need the mutation that makes something happen.

Fifteen mutations ran. Two more (`getItem`, `search`) did not compile and were dropped.

## Result

**68 of 106 tests were killed by at least one mutation. No new vacuous test was found.** Every test
whose subject a mutation actually broke, failed. The ones most at risk — the "returns null",
"reports nothing", "has no next" cases — were each killed by their complementary mutation:

| Test | Killed by |
|---|---|
| `NeverConnectedCollectorTest.aCommandBeforeAnyConnection_reportsNothing` | hook fires unconditionally |
| `RestoredSnapshotTest.applyRestored_*_hasNo*` (3) | `hasNext`/`hasPrevious` always true |
| `PlaybackStatePersistenceTest.restore_blankCurrentSongId_returnsNull`, `…_noSignedInUser_…` | restore refuses nothing |
| `PlayerTransportTest.isPlaying_noController_isUnknownAndReportsNothing` | `isPlaying` returns false; and again when it reports |
| all 8 `ReconnectingCollectorTest` | a dead controller counts as connected |
| all 10 `OfflinePlaybackTest` | `isPlayableNow` true, then false, then `isStreamStalledOffline` true |

## The 32 not covered

Not weaknesses — paths no mutation reached. Whoever extends this sweep starts here:

| File | Untested against mutation | Why |
|---|---|---|
| `MediaBrowseTreeTest` | 17 | only `getChildren` was mutated; `getItem` and `search` mutations did not compile |
| `PlayableItemsTest` | 5 | covered instead by T31/T32's own mutation checks (removing the await, removing the resolve) |
| `ConnectedTransportTest` | 4 | the connect path, two pre-dispatch refusal guards, and `sendRestoreState`, which does not go through `dispatch` |
| `ControllerConnectionTest` | 3 | acquire and consumer-count sharing |
| `PlaybackStatePersistenceTest` | 2 | the remaining two refusal paths |
| `SharedControllerFutureTest` | 1 | releases the future directly rather than through `ControllerConnection.release` |

## A trap in the harness

The first version of the sweep script decided a run's outcome from the task names in gradle's
output. Two mutations failed at `kspDebugKotlin`, which it did not check for, and it reported
"0 tests failed" — read as "nothing detected this", when nothing had run at all. **Treat a run with
no `tests completed` line as invalid, not as a result.** The same shape as the bugs being hunted.
