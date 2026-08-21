# T8 - The search draft query round-trips through the app-root StateFlow

- **Slice:** performance follow-up after A6
- **Depends on:** A6 search implementation
- **Status:** Ready to spec
- **Verification Command:** `./gradlew :automotive:testOemDebugUnitTest`
- **Design Reference:** `docs/AAOS_A6_VERIFICATION.md` (typing frame measurements) · `docs/aaos-DESIGN.md` D32
- **Risk Tags:** text-entry latency · ViewModel API change · test surface
- **Affected Modules:** `:automotive`

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
