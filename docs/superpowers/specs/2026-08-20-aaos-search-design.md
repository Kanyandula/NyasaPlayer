# AAOS Slice A6 - Search and Search Results

> **Status:** Draft for review · **Date** 2026-08-20 · **Depends on:** A1 restrictions,
> A2 chrome, A3/A4 shared content components, A5 merged baseline
> **Design source:** `docs/aaos-DESIGN.md` §Driving restrictions, `docs/aaos-app.html`
> screens 5-6, Android `CarUxRestrictions` and media voice-search guidance
> **Scope source:** `docs/AAOS_PRD.md` §5, §6, §9 and §11; `docs/AAOS_SCREEN_CONTRACT.md`
> screens 5 and 6; `docs/tickets/A6-search.md`

## 1. Context

A6 makes the system-bar search control real for the `oem` custom launcher. Search was intentionally
left unreachable in A2-A5: `CarSystemBar` accepts an `onSearchClick` callback but renders the
control disabled, and `AutomotiveApp.carUiLocation()` hardcodes `sheet = null` even though
`CarSheet.Search` and the gate branch already exist.

The data layer is not empty. `AutomotiveContentViewModel` currently owns a hidden, debounced
song-search path:

```kotlin
fun onSearchQueryChange(query: String)
fun clearSearch()
val searchQuery: String
val searchResults: List<Song>
```

Those fields have no production UI. A6 is the first shipped consumer, so it is also the right
moment to split search out of `AutomotiveContentViewModel`. That file already records the warning
from A4: the next slice touching it should split rather than grow the suppression again.

### 1.1 What A6 is

1. Enable the system-bar search icon and open a full-screen search sheet.
2. Build `CarSearchScreen` for the idle/search-entry state.
3. Build `CarSearchResultsScreen` for submitted results.
4. Resolve Q2: use the platform/system IME for parked text entry and replace editing with a
   system/Assistant voice-search prompt whenever `UX_RESTRICTIONS_NO_KEYBOARD` is active.
5. Move search state into a small `AutomotiveSearchViewModel`.
6. Ship song-only results backed by `SongRepository.searchSongs`.
7. Keep results playable while driving and cap the visible result rows by
   `maxCumulativeContentItems`.
8. Record the multi-entity search gap as follow-up work rather than pretending it is covered.

### 1.2 What A6 is not

- **Not a custom keyboard.** The app uses Compose text input and lets the platform show whatever
  IME the head unit supplies. It does not draw its own keyboard.
- **Not in-app voice capture.** The app does not request `RECORD_AUDIO`, does not draw a recorder,
  and does not collect microphone input. Driving voice search stays system/Assistant driven through
  `PlaybackService.onSearch` / `onGetSearchResult`.
- **Not multi-entity search.** Album, artist and playlist result cards are deferred by D33.
- **Not a generic artist-detail screen.** The only existing artist screen is liked-songs scoped.
- **Not A7.** Settings and profile controls remain disabled.
- **Not persistent search history.** Recent queries are session state in the car ViewModel only.
- **Not the host-rendered media-template search path.** `PlaybackService` and `MediaBrowseTree`
  already return song results and remain first-class for Android Auto / `playstore`.
- **Not mobile search.** `:app` search screens and playlist/download overflow actions are not
  part of this slice.

## 2. Problems

### 2.1 Q2 blocked the phase

The PRD left text entry unresolved because a head unit is not a phone. A6 resolves it without
inventing a keyboard: when the platform permits keyboard input, the parked search sheet focuses a
normal Compose text field; when `NO_KEYBOARD` is active, the field is not editable and the sheet
offers the system voice-search path.

The implementation must read the same restriction state as every prior slice. It must not infer
"driving" from speed, gear, emulator mode or app-local booleans.

### 2.2 Search currently has no destination

`CarSheet.Search` exists only as a future contract. The system bar discards the callbacks, and
`AutomotiveApp` cannot represent an open search sheet. That means the restriction gate already
has tests for a state that production code cannot enter.

A6 wires that state rather than adding a parallel navigation model.

### 2.3 The source contract overpromises result types

`AAOS_SCREEN_CONTRACT.md` says screen 6 has album/artist result cards, but the available search
API is song-only:

- `SongRepository.searchSongs(query, limit): List<Song>`
- `MediaBrowseTree.search(query)` maps those songs to playable media items.
- `AlbumRepository` and `ArtistRepository` have list/get/popularity APIs, not search APIs.
- The only existing artist screen is `CarArtistLikedSongsScreen`, which cannot honestly represent
  every catalogue artist.

Filtering whatever album/artist lists happen to be loaded in memory would produce a different
contract from the media-session search surface, hide cache misses as "no results", and make
process-death restore depend on incidental collector timing. A6 therefore ships song search first
and tracks multi-entity search separately.

## 3. Search State

A6 creates a dedicated ViewModel:

```kotlin
data class AutomotiveSearchUiState(
    val query: String = "",
    val submittedQuery: String = "",
    val results: List<Song> = emptyList(),
    val recentQueries: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isEditing: Boolean = false,
)
```

`AutomotiveSearchViewModel` injects `SongRepository` only. It exposes:

- `onQueryChange(query: String)`
- `submitSearch()`
- `selectRecentQuery(query: String)`
- `clearQuery()`
- `retrySearch()`
- `setEditing(isEditing: Boolean)`

Search runs on explicit submit, not on every keypress. Submit happens when the driver presses the
IME Search action, taps the sheet's Search CTA, taps a recent query, or a future system-search
bridge provides a committed query. This is a car UI: text input should not create a stream of
network/storage work while the driver is still editing.

Recent queries are in-memory and session-only:

- max 5 entries
- trim whitespace
- de-duplicate case-insensitively
- newest first
- do not persist across process death

## 4. Text Entry And Restrictions

`CarUiLocation.textEntryActive` must mean **an editable search field is active**, not "the query
string is non-empty." Results with a submitted query are allowed while driving; active keyboard
editing is not.

The sheet uses three input states:

| State | Condition | Behaviour |
|---|---|---|
| Editable | Search sheet open and `!restrictions.noTextEntry` | Compose text field may focus and show the system IME |
| Voice prompt | Search sheet open and `restrictions.noTextEntry` | No editable field; show system/Assistant voice-search prompt |
| Results | Search submitted and field is not editing | Results remain visible; back/clear are available |

If the vehicle starts moving while the editable field is active and `NO_KEYBOARD` arrives, the
existing gate refuses the location, evicts to the current tab root, and shows the text-entry
reason. Opening search again under the same restrictions is allowed, but it opens in voice-prompt
mode rather than editing mode.

The app must not add `RECORD_AUDIO` to any manifest and must not use `RecognizerIntent` or an
embedded recorder as a shortcut around the platform restriction.

## 5. Screen 5 - `CarSearchScreen`

Idle/search-entry sheet, opened from the system-bar search icon.

Required content:

- Back/close action.
- Search field when editable.
- Clear-query action when a draft query is present.
- Search/submit action when the trimmed query is non-empty.
- Voice-search prompt whenever text entry is restricted.
- Recent queries when there are saved session queries.
- Recent-empty state when there are no saved queries.
- Browse-by shortcuts.

Browse-by shortcuts are navigation shortcuts, not result filters:

- `Songs` keeps the driver in search and focuses the field if editing is allowed.
- `Genres` closes search and selects Browse.
- `Albums`, `Artists` and `Playlists` close search and select Library.

No shortcut may silently do nothing. If a future implementation adds section-specific scroll or
deep links, that is additive; A6 only needs to route to the tab root that already owns the content.

## 6. Screen 6 - `CarSearchResultsScreen`

Submitted-results sheet, reached after a non-blank search submit.

Required content:

- Back action returns to `CarSearchScreen` with the query preserved.
- Clear action clears the query and returns to idle search.
- Top result uses the first visible song when results are non-empty.
- Song rows render the remaining visible results.
- Loading, no-results and error states are explicit.
- Retry re-runs the submitted query.

Playing a result passes the visible result list and tapped song to the existing playback path,
matching Home/Favourites list behaviour. While restrictions require distraction optimization, the
visible result list is capped by `maxCumulativeContentItems`; parked search may show up to the
repository limit.

Album and artist names appear as song metadata only. Dedicated album/artist result cards do not
ship in A6.

## 7. Data And API

No new repository interface is required for A6. `SongRepository.searchSongs(query, limit)` remains
the single search API.

A6 should align the offline implementation with mobile search by matching album names in addition
to title and artist:

```sql
WHERE title LIKE '%' || :query || '%'
   OR artist_name LIKE '%' || :query || '%'
   OR album_name LIKE '%' || :query || '%'
```

That change improves both the custom launcher and the media-session voice/search path because
`MediaBrowseTree.search()` also delegates to `searchSongs`.

## 8. Verification

### 8.1 Unit tests

Add JVM tests before wiring the UI:

1. Blank submit does not call `searchSongs` and leaves results empty.
2. Submit trims whitespace and stores `submittedQuery`.
3. Successful submit populates results and records the recent query.
4. Re-submitting the same query moves it to the top rather than duplicating it.
5. Recent queries cap at five.
6. Failed search sets `errorMessage`, clears loading, and keeps the submitted query available for
   retry.
7. A stale in-flight result cannot overwrite a newer submitted query.
8. `clearQuery()` cancels in-flight work and clears query/results/error.
9. `setEditing()` changes only editing state.
10. `CarRestrictionGateTest` still proves driving allows `CarSheet.Search` without text entry and
    denies it with text entry.
11. `OfflineSongRepositoryTest` proves album-name matches are returned by `searchSongs`.

### 8.2 Manual checklist

Run on `AAOS_AOSP_33_userdebug`, one emulator only, per `docs/AAOS_DRIVING_STATE_TESTING.md`.

1. Parked: search icon is enabled; settings/profile remain visibly disabled.
2. Parked: opening search focuses the field and the platform keyboard appears.
3. Parked: submitting a query shows loading and then results.
4. Parked: tapping top result and a later row starts playback from the visible result list.
5. Parked: no-results and error states are readable and recoverable.
6. Parked: recent queries de-duplicate and cap at five.
7. Driving injected to `DO: true UxR: 255`: opening search shows the voice prompt and no keyboard.
8. Driving: search results from a previously submitted query remain viewable and are capped.
9. Driving while actively editing: the app evicts from text entry and shows the refusal reason.
10. Manifest check: no variant requests `RECORD_AUDIO`.

### 8.3 Gates

```bash
./gradlew :automotive:testOemDebugUnitTest
./gradlew :core:data:testDebugUnitTest
./gradlew detekt
./gradlew :automotive:lintOemDebug
./gradlew :automotive:assembleOemDebug :automotive:assemblePlaystoreDebug
```

No new Detekt baseline entries. No new color pairs unless measured in `docs/aaos-DESIGN.md`.

## 9. Decisions

Numbering continues A5's D26-D30.

| # | Decision | Rationale |
|---|---|---|
| D31 | **Q2 is resolved by using system IME when `NO_KEYBOARD` is absent and a voice-search prompt when it is present.** | The app should react to platform restrictions, not infer driving state or draw a custom keyboard. This aligns the custom launcher with Android car UX guidance and keeps `RECORD_AUDIO` out of the app. |
| D32 | **A6 searches on explicit submit, not on every keypress.** | A head-unit search field should not create work while the driver is still editing. Submit also maps cleanly to screen 6: a results screen exists only after a committed query. |
| D33 | **A6 ships song-only results. Album, artist and playlist result cards are deferred.** | The shared search API, media-session search path and available tests are song-search contracts. Multi-entity cards need a typed result model, repository APIs, and a real artist-detail destination before the UI can be honest. |
| D34 | **Recent searches are session-only in `AutomotiveSearchViewModel`.** | Persistence is not required by the PRD and would add storage/privacy questions to a screen slice. Losing recent queries on process death is acceptable; losing playback state is not, and is tracked separately by T3. |
| D35 | **`AutomotiveContentViewModel` loses search ownership rather than growing another suppression.** | A4's D23 explicitly called out that this ViewModel now owns too many unrelated domains. A6 can isolate search with a small ViewModel because the feature depends only on `SongRepository`. |

## 10. Risks

| Risk | Impact | Mitigation |
|---|---|---|
| `textEntryActive` is derived from `query.isNotEmpty()` again | Driving evicts from a valid results screen | Store editing/focus state separately and keep the existing gate tests green |
| Search icon is enabled but settings/profile accidentally become enabled too | A7 scope leaks into A6 | Add enabled flags to `CarSystemBar`; only search changes state |
| Song-only scope silently drops the album/artist card contract | Future work is lost | Update PRD/contract wording and create the multi-entity follow-up ticket |
| In-memory search results are uncapped while driving | FR-2.4 regression | Apply `maxCumulativeContentItems` at the screen call site, before playback callbacks receive the list |
| `AutomotiveContentViewModel` keeps dead search fields | Search has two state owners | Remove `searchJob`, `searchQuery`, `searchResults`, `onSearchQueryChange()` and `clearSearch()` in the split task |
| Voice CTA becomes a dead button | FR-2.6 silent no-op | Render it as a prompt unless a verified system action is wired; do not ship a clickable no-op |

## 11. Definition of done

1. System-bar search opens a full-screen search sheet; settings/profile remain disabled.
2. Parked text entry uses the platform IME; no custom keyboard exists.
3. `NO_KEYBOARD` mode shows a non-editable voice-search prompt and never requests microphone input.
4. Submitted song search renders loading, results, no-results and error states.
5. Search results are playable and use the visible result list as the playback queue.
6. Results remain viewable while driving when text entry is inactive.
7. Active text entry is refused/evicted under `NO_KEYBOARD` with an explanation.
8. Visible results cap to `maxCumulativeContentItems` while distraction optimization is required.
9. Recent queries are session-only, de-duplicated and capped at five.
10. `AutomotiveContentViewModel` no longer owns search state or methods.
11. D31-D35 are recorded in `docs/aaos-DESIGN.md`.
12. PRD and screen contract no longer promise album/artist result cards inside A6.
13. Unit tests, Detekt, Lint and both flavor assemblies pass.
14. Manual A6 verification is executed and recorded.
