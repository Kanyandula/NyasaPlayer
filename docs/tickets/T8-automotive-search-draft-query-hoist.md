# T8 - The search draft query round-trips through the app-root StateFlow

- **Slice:** performance follow-up after A6
- **Depends on:** A6 search implementation
- **Status:** Deferred — mechanism confirmed, cost not reproducible on available hardware
- **Verification Command:** `./gradlew :automotive:testOemDebugUnitTest`
- **Design Reference:** `docs/AAOS_A6_VERIFICATION.md` (typing frame measurements) · `docs/aaos-DESIGN.md` D32
- **Risk Tags:** text-entry latency · ViewModel API change · test surface
- **Affected Modules:** `:automotive`

## Measured 2026-08-21, before implementing: the mechanism is real, the cost is not

The ticket's own first step was to settle the stability reasoning with a compiler report rather
than documented defaults, and then to re-measure. Both were done. **The refactor was not started**,
because the evidence does not currently justify it.

**Compose compiler report** (`composeCompiler { reportsDestination }` on `:automotive`, temporary,
reverted):

- All **109** composables in the module are `restartable skippable`. None are restartable-but-not-
  skippable, including `AuthenticatedApp`, `BrowseShell`, `SearchSheet` and `CarSearchScreen`.
- `AutomotiveUiState`, `AutomotiveContentState` and `AutomotiveSearchUiState` are **unstable**;
  `UxRestrictionState` is **stable**. This confirms the reasoning below, and also confirms that
  T5's `remember(items, restrictions)` keys are sound.

Skippability does not save `AuthenticatedApp` here: it *reads* `searchState`, so a keystroke
invalidates its body regardless. The mechanism this ticket describes is real.

**Device measurement on a healthy emulator** (673MB available, load 0.00), same build, typing seven
characters at one per second into the search field:

| Interaction | Dropped frames |
|---|---|
| Four tab switches | **0** |
| Seven keystrokes | **0** |

Zero ANRs. Every character landed.

The 240-dropped-frame figure in `docs/AAOS_A6_VERIFICATION.md` came from an emulator instance at
1878MB of 2012MB used, whose car stack later crashed outright. It measured the emulator, not the
app. A6 already said the gap "was not reproduced on the healthy instance" — that sentence was
written before anyone checked, and this is the check.

**Recommendation: leave this deferred.** Moving the draft query changes the ViewModel's public
state shape and rewrites roughly a dozen tests, to fix something that costs zero measurable frames
on the only hardware available. Reopen if typing lag is reported on a real head unit, or if a
release-build measurement on real hardware shows a cost. The scope below stays accurate for
whoever does.

To reproduce the compiler report, add to `automotive/build.gradle.kts` above the `android` block:

```kotlin
composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_reports")
    metricsDestination = layout.buildDirectory.dir("compose_metrics")
}
```

then `./gradlew :automotive:compileOemDebugKotlin --rerun-tasks` — an ordinary build is
up-to-date and emits nothing.

## Problem

`AutomotiveSearchUiState.query` is the draft text, and `AuthenticatedApp` collects the whole state
at the app root. Every keystroke therefore costs a data-class copy, a `StateFlow` emit, and a full
re-execution pass over the largest composable in the module — roughly forty argument expressions
and skip comparisons, thirty of them for `BrowseShell` alone — before a glyph is drawn.

`BrowseShell` itself does not re-execute (its arguments keep their identity and its lambdas are
compiler-memoized under strong skipping, which is on by default at Kotlin 2.0.21), and
`RecentQueries` and `BrowseByShortcuts` skip correctly because the state copy carries the same
`List` instances forward. The cost is the root-level pass, not the subtree.

Asynchronously hoisting text-field state through a flow is the documented cause of laggy and
dropped characters in Compose text fields. A6's device run measured up to 240 dropped frames while
typing seven characters, against 33–47 for tab switching on the same build — consistent with this,
on top of software GL and the pre-existing 2 Hz shell churn in T5.

## Scope

- Move the draft query off `AutomotiveSearchUiState` and onto the ViewModel as Compose state
  (`var query by mutableStateOf("")`), so per-keystroke invalidation lands on `SearchField` and the
  submit CTA instead of the app root.
- Pass the read down rather than the value — a `MutableState`, or `query: () -> String`.
- `submitSearch()` reads the property instead of `_uiState.value.query`.
- Update the ViewModel tests that assert on `uiState.value.query` (roughly a dozen).
- Re-measure on device before and after; record the numbers in the A6 verification record.

## Out Of Scope

- Changing the explicit-submit contract (D32) or where `submittedQuery` lives.
- Reintroducing per-keystroke search.

## Acceptance Criteria

- Given the driver types a character, then `AuthenticatedApp` does not re-execute.
- Given the driver types, then the draft still survives `backToSearch()` and closing the sheet.
- Given the ViewModel tests run, then draft-query behaviour is still covered.
- Given the device measurement is repeated, then dropped frames while typing are materially closer
  to the tab-switching baseline.

## Notes

Found by an efficiency review of A6. Its stability reasoning rests on the compiler's documented
defaults rather than a compiled report; adding `composeCompiler { reportsDestination }` to
`automotive/build.gradle.kts` for one debug build would confirm `BrowseShell` is skippable and
settle both this and T5 empirically. Worth doing as the first step here.
