# AAOS Slice A4 — Favourites, Artist Liked Songs and the Empty State

> **Status:** Draft for review · **Date** 2026-08-09 · **Depends on:** A2 (merged, PR #16), A3 (merged, PRs #18–#20)
> **Design source:** `docs/aaos-DESIGN.md` §Layout, §Components, §Driving restrictions
> **Scope source:** `docs/AAOS_PRD.md` §9 (phase A4), `docs/AAOS_SCREEN_CONTRACT.md` screens 8, 9, 17

## 1. Context

A4 is the first slice that lets the driver **change** their library rather than browse it. Every
prior slice was read-only: A2 built the chrome, A3 built browse, library and detail. Unliking a
song from a list is the first per-row write the car has ever offered, and it is the reason this
slice needs a design rather than three screen rewrites.

Two of the three screens already exist as placeholders:

- `CarFavouriteMusicScreen` (76 lines) — a plain list. No Play all, no Shuffle, no unlike. A2
  shipped it deliberately minimal under its D2, to avoid two surfaces rendering the same content.
- `CarArtistLikedSongsScreen` (109 lines) — has Shuffle, a plain header, no Play all, no unlike.
  A3 rewrote its call site when `selectedArtist` became `CarDestination.Artist`, but left the
  screen alone.
- `CarEmptyFavouritesScreen` — does not exist. Today Favourites renders an inline `CarEmptyState`.

### 1.1 What A4 is

1. **`CarFavouriteMusicScreen` rebuilt** — hero, Play all, Shuffle, track rows with unlike.
2. **`CarArtistLikedSongsScreen` rebuilt** — artist hero, Play all, track rows with unlike.
3. **`CarEmptyFavouritesScreen`** — new composable, rendered in place at the Favourites tab root.
4. **A per-row unlike affordance** on the shared `CarTrackRow`.
5. **A snapshot model** that keeps rows stable under a live Firestore flow.

### 1.2 What A4 is not

- **Not search.** A6 owns screens 5 and 6, and is blocked on open question Q2 (head-unit text
  entry). A3 removed Browse's search field under its D10, so search stays unreachable in the car
  until A6 lands. A4 does not reintroduce it.
- **Not settings or sign-out.** A7 owns screen 14. Library keeps sign-out under A3's D14.
- **Not downloads.** A8 owns screen 15, and is blocked on D12's module blocker.
- **Not the Favourites → artist path.** See D21.
- **Not a `AutomotiveContentViewModel` split.** See D23.

## 2. The problem this slice actually has to solve

`AAOS_SCREEN_CONTRACT.md` screen 8 specifies:

> Allowed; list truncated; **unlike is one-tap and row removal is deferred until refresh**

That deferral is a distraction-safety requirement, not a nicety: a list that reflows under the
driver's finger at the moment of a tap is how a mis-tap becomes a wrong action. But it runs
against the data layer. `contentState.likedSongs` is fed by `observeLikedSongs()`, a live
Firestore snapshot listener. Unlike a song and the flow re-emits without it — the row removes
itself, immediately, whether or not anyone wanted it to.

**Deferring removal therefore means deliberately not rendering from the live source.**

### 2.1 Freeze on first unlike, not on entry (D19)

The obvious design — snapshot when the driver enters the tab — is wrong, and it is worth saying
why, because it fails in a way this programme has already been bitten by once.

`likedSongs` is empty until Firestore's first emission. A driver who opens Favourites before that
emission would be snapshotted against an empty list, and §2.2's guard would then stop it ever
being retaken: the screen would show "no favourites yet" permanently, with the songs one tab away.
That is the same restore-gap failure A3's D17 was written to remove.

So the list is **not** frozen on entry. It is frozen at the first unlike, which is the only moment
stability is actually required.

`AutomotiveContentState` gains two fields:

```kotlin
val favourites: List<Song>? = null,
val pendingUnlikes: Set<String> = emptySet(),
```

- While `favourites` is `null`, the screen renders the **live** `likedSongs`. Nothing has been
  unliked, so there is nothing to hold back, and tracking the live flow is simply correct — it
  also means a late first emission fills the screen normally.
- The **first** unlike captures `likedSongs` into `favourites` *before* the removal lands, and
  from then on the screen renders that frozen list. Rows cannot move or vanish for the rest of the
  visit.
- `pendingUnlikes` holds the `mediaId`s unliked during the visit. A heart is filled when its id is
  **not** in the set; tapping again re-likes and removes it, so the action is undoable without the
  row ever moving.

Two ViewModel methods, mirroring A3's `openDetail`/`closeDetail` so the shape reads as normal
here:

```kotlin
fun openFavourites()
fun closeFavourites()
```

driven from the shell by one effect keyed on the active tab. `openFavourites()` exists to mark the
visit; `closeFavourites()` clears both fields.

Distinguishing "not yet loaded" from "genuinely nothing liked" therefore does not depend on the
snapshot at all — it is the same `isLoading`/`errorMessage` pair every other screen in the module
already uses.

### 2.2 The guard that is not optional (D20)

`openFavourites()` **must not clear an existing freeze.** It is safe to call repeatedly; it clears
nothing that `closeFavourites()` has not already cleared.

`AutomotiveActivity` declares no `android:configChanges`, so a `uiMode` night-mode change —
routine in a vehicle, at a tunnel or at sunset — recreates the Activity and re-runs the effect.
If that re-entry reset `favourites` and `pendingUnlikes`, every held-back row would reconcile
silently, mid-drive, which is precisely the behaviour §2 exists to prevent.

This is not hypothetical. The identical defect was found in A3's `openDetail` by the whole-branch
review of PR #18 and fixed there; A4 must not reintroduce it one slice later.

### 2.3 What a refresh is

The row disappears when, and only when:

- the driver leaves the Favourites tab and returns (`closeFavourites()` then `openFavourites()`),
  or
- the process dies and is restored — the ViewModel dies with it, so the freeze goes too.

Both are genuine refreshes. Neither happens under the driver's finger.

## 3. The shared row (D22)

`CarTrackRow` is the module's shared track row, used by `CarHomeScreen`, `CarDetailScreen` and
`CarFavouriteMusicScreen`. It currently ends in a duration label and has no action slot:

```kotlin
fun CarTrackRow(
    title: String,
    artist: String,
    duration: String,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    coverUrl: String = "",
)
```

A4 adds two optional parameters:

```kotlin
    onLikeToggle: (() -> Unit)? = null,
    isLiked: Boolean = true,
```

The heart renders **only when `onLikeToggle` is non-null**, so Home and the detail screens are
untouched by their defaults and need no call-site changes.

The heart lives inside the component rather than at each call site so its **76dp touch target is
enforced in one place**. `CarListRowHeight` is 80dp, so a 76dp target fits within the existing row
height. Use `carTouchTarget()`, not an exact `size()`, per that modifier's own guidance.

Visual treatment matches `CarMiniPlayer`'s existing like control for consistency inside the
module: `Icons.Filled.Favorite` / `Icons.Filled.FavoriteBorder`, tinted `NyasaGold` when liked and
`CarTextSecondary` when not, with `contentDescription` of "Unlike" / "Like" respectively.
`app/lint.xml` makes `ContentDescription` an error, and unlike the artwork this control has no
adjacent text naming it, so the description is required rather than optional.

> Note: `NyasaIcons.kt` defines a `HeartIcon`, but `CarMiniPlayer` uses the Material icons above.
> A4 follows the mini-player because it is the car module's existing like affordance. The
> unused-vs-used icon inconsistency is pre-existing and out of scope.

## 4. The screens

### 4.1 `CarFavouriteMusicScreen` (screen 8)

Contract: *hero liked songs, Play all, Shuffle, track rows, unlike.*

Hero — artwork from the first song's `resolvedCoverUrl`, the title "Liked Songs", and the count —
then Play all and Shuffle as 76dp CTAs, then the rows.

Rows render from `favourites ?: likedSongs` per §2.1, truncated at `maxCumulativeContentItems` at
render time, not when the freeze is taken: truncation is a display concern that changes with
driving state, while the freeze is a stability concern that must not.

States follow the module's existing four-branch shape (error-with-no-content → loading → empty →
content), as `CarHomeScreen`, `CarBrowseScreen` and `CarLibraryScreen` all do. Empty renders
screen 17's composable in place. Because the freeze is not taken on entry (§2.1), an empty list
here means genuinely nothing is liked — not that the load has yet to arrive.

### 4.2 `CarArtistLikedSongsScreen` (screen 9)

Contract: *artist hero, Play all, track rows, unlike.*

Gains the artist hero (avatar, name, liked count), Play all, and hearts. **Keeps its existing
Shuffle**, which the contract does not list — see D24.

Its track list stays a live filter over `likedSongs` rather than a snapshot. This is deliberate
and follows A3's D16: the artist screen is reached by drilling in, `rememberSaveable` restores the
destination synchronously while `likedSongs` is still empty, and the list is correctly empty for
that moment. Adding a snapshot here would reintroduce the resolve-during-the-gap problem D16 was
written to remove.

**Consequence, stated plainly:** unliking on screen 9 *does* remove the row, because there is no
snapshot holding it. That differs from screen 8 and it is a real inconsistency — recorded as D25
rather than papered over.

### 4.3 `CarEmptyFavouritesScreen` (screen 17)

Contract: *empty heart state, Browse Music CTA. Allowed; CTA routes to Browse root.*

A new composable in `auto/ui/screens/`, rendered by `CarFavouriteMusicScreen` when the snapshot is
empty. **Not a navigation destination** — see D21.

## 5. Errors

A failed unlike **reverts the heart and surfaces an error**. It does not fail silently.

The revert matters because the heart is optimistic: `pendingUnlikes` is updated before the
repository call returns, so the driver sees the toggle immediately. If `unlikeSong` throws, the id
comes back out of the set and the error is raised through `AutomotivePlayerViewModel`'s existing
`PlayerError` channel with `isRetryable = false` — the field PR #19 added precisely so that a
non-retryable error does not offer a Retry that acts on unrelated playback.

`UserRepository` already exposes what is needed; A4 requires no `:core:data` change:

```kotlin
suspend fun likeSong(userId: String, mediaId: String)
suspend fun unlikeSong(userId: String, mediaId: String)
```

## 6. Verification

### 6.1 Unit tests

A3 left the harness A4 needs: `MainDispatcherRule` and six fakes in `automotive/src/test`.
`FakeUserRepository` is currently inert (`likeSong`/`unlikeSong` are `= Unit`, `getLikedSongs`
returns `flowOf(emptyList())`) and becomes behavioural, with a failure mode so the revert path can
be tested.

`FavouritesSnapshotTest`:

1. `openFavourites()` alone leaves `favourites` null — no freeze until an unlike.
2. **`likedSongs` emitting after `openFavourites()` is reflected on screen** while `favourites` is
   null. This is the empty-vs-loading case §2.1 exists for: a driver entering before Firestore's
   first emission must see the list when it arrives, not a permanent empty state.
3. The first unlike freezes `favourites` to the pre-removal list and adds the id to
   `pendingUnlikes`.
4. The live flow dropping that song does **not** remove it from the frozen `favourites`.
5. Re-liking removes the id from `pendingUnlikes` without changing `favourites`.
6. `closeFavourites()` then `openFavourites()` reconciles — the freeze is gone and the unliked
   song with it.
7. **A repeat `openFavourites()` while frozen preserves both `favourites` and `pendingUnlikes`.**
   This is D20's guard; it must be verified to fail without it.
8. A failed `unlikeSong` reverts `pendingUnlikes` and sets a non-retryable error.

Cases 2 and 7 are the ones that justify the file, and they pull in opposite directions — 2 demands
the list keep tracking, 7 demands it stop. An implementation that satisfies only one of them will
look correct in normal use and fail in exactly the conditions this slice is about.

A3's equivalent guard was invisible to its tests until a reviewer noticed they passed with the
guard deleted. **Verify each case fails when its behaviour is removed**, and report what was seen.

### 6.2 Manual checklist

Per `docs/AAOS_DRIVING_STATE_TESTING.md`, on `AAOS_AOSP_33_userdebug`, **one emulator only** —
see `docs/AAOS_A3_VERIFICATION.md` for why two starve the guest into swallowing taps.

1. Parked: Favourites renders hero, Play all, Shuffle and rows; every control does something.
2. **Tap unlike — the row stays put and the heart hollows.** Tap again — it fills.
3. Leave Favourites, return: the unliked row is gone.
4. Unlike, then trigger a night-mode change without leaving the tab: **the row must still be
   there and still hollow.** This is D20; it is the check that cannot be seen any other way.
5. Process death inside Favourites: returns to a freshly reconciled list, no crash.
6. Empty state: unlike everything, leave, return — screen 17 renders with a working Browse CTA.
7. Artist screen: hero, Play all, Shuffle, hearts; unlike removes the row (D25).
8. Driving: rows truncate at `maxCumulativeContentItems`; unlike remains one tap.
9. Tap **every** CTA on both rebuilt screens. A3 shipped a silently dead card that nine reviews
   missed and one tap found; see `docs/AAOS_A3_VERIFICATION.md`.

### 6.3 Gates

- `oem` and `playstore` both build; `detekt` at `maxIssues: 0`; lint clean.
- Touch targets ≥76dp, including the new heart.
- No new colour pairs — reuses tokens already measured in `aaos-DESIGN.md` §Contrast.

## 7. Decisions

Numbering continues A3's D8–D18.

| # | Decision | Rationale |
|---|---|---|
| D19 | The list is frozen at the **first unlike**, not on tab entry. `favourites: List<Song>?` is null until then, and the screen renders live `likedSongs` while it is. | The contract requires row removal to be deferred until refresh, and `likedSongs` is a live Firestore listener that drops an unliked song immediately — so honouring it means not rendering from the live flow *once something has been unliked*. Freezing on **entry** instead would snapshot an empty list whenever the driver opens Favourites before Firestore's first emission, and D20's guard would then prevent it ever being retaken: a permanent "no favourites yet" with the songs one tab away. That is A3's D17 restore-gap failure in a new place. Freezing at the first unlike is both simpler and correct: before an unlike there is nothing to hold back. |
| D20 | `openFavourites()` **never clears an existing freeze**; only `closeFavourites()` does. | `AutomotiveActivity` declares no `configChanges`, so a night-mode flip recreates it and re-runs the driving effect. If re-entry reset `favourites` and `pendingUnlikes`, held-back rows would reconcile silently mid-drive — the exact behaviour D19 exists to prevent. The identical defect was found in A3's `openDetail` by PR #18's whole-branch review; reintroducing it one slice later would be indefensible. |
| D21 | Screen 17 is a **composable rendered in place** at the Favourites tab root, not a navigation destination. | Favourites is a tab root at drill depth 0. A destination would sit at depth 1, where `maxContentDepth` can refuse it — meaning a driver with nothing liked would be blocked from a screen whose only content is "you have nothing yet". Rendering in place keeps it reachable under every restriction state. |
| D22 | The unlike affordance goes **inside `CarTrackRow`**, behind an optional `onLikeToggle`, rather than into a new row variant or each call site. | The 76dp touch target is then enforced once, in the component, rather than depended upon at three call sites. Gating on a nullable callback leaves `CarHomeScreen` and `CarDetailScreen` untouched by their defaults. A separate `CarLikedTrackRow` would duplicate a row the screen contract explicitly names as shared. |
| D23 | A4 adds `@file:Suppress("TooManyFunctions")` to `AutomotiveContentViewModel.kt` and records the class's growth as **debt for a later slice**, rather than splitting it now. | The file is at 19 functions against detekt's `thresholdInFiles: 20`, and the class-level suppression added in A3 does not cover the file threshold, so A4's two methods break the build without action. Splitting a ViewModel that four screens depend on is a refactor, not a screen slice; doing it inside A4 would make three screens the smaller half of the work. **This is the fourth suppression of this rule across `:automotive` and `:app` view models — the class now owns search, genres, albums, playlists, recently-played, liked songs, popular, detail and favourites, and the next slice to touch it should split it rather than suppress again.** |
| D24 | `CarArtistLikedSongsScreen` **keeps its Shuffle control**, which the contract does not list for screen 9. | The control exists and works today. Removing it to match the contract would be a visible capability regression for no user benefit. Recorded as an additive deviation rather than silently kept. |
| D25 | Unlike on screen 9 **removes the row immediately**; on screen 8 it does not. | Screen 9's list is a live filter over `likedSongs`, which A3's D16 established deliberately: `rememberSaveable` restores the artist destination while `likedSongs` is still empty, and any snapshot or resolve step taken in that gap drops the user back to the tab root on every process-death restore. Preserving D16 is worth more than making the two screens' unlike behaviour identical. The inconsistency is real and is recorded here rather than discovered later. |

## 8. Risks

| Risk | Impact | Mitigation |
|---|---|---|
| The freeze diverges from the live list in ways beyond unlike — e.g. a like made on the phone after the driver has unliked something | The car shows a stale list for the rest of the visit | Accepted, and bounded: the divergence starts only at the first unlike and ends when the tab is left. Before any unlike the screen tracks the live flow exactly |
| D20's guard is invisible to tests unless deliberately targeted | The reload-flash defect returns silently, as it nearly did in A3 | §6.1 case 7 exists for it, and must be verified to fail with the guard removed |
| §6.1 cases 2 and 7 pull in opposite directions | An implementation satisfying only one looks correct in normal use | Both are required, and the report must state what was seen when each was removed |
| `CarTrackRow` is shared by three screens | A regression in Home or the detail screens from a row change | Both new parameters are optional and default to the current behaviour; no existing call site changes |
| Unlike is optimistic | A failed write leaves the UI claiming something that did not happen | §5 reverts the heart and raises a non-retryable error; §6.1 case 7 covers it |
| Two screens now unlike with different row behaviour (D25) | A driver learns one behaviour and is surprised by the other | Recorded as D25. Revisit if A5 or later adds a third unlike surface, at which point the inconsistency stops being a two-screen curiosity |

## 9. Definition of done

1. `CarFavouriteMusicScreen` has hero, Play all, Shuffle, and rows with a working unlike.
2. Unliking a row on screen 8 leaves the row in place with a hollow heart; re-tapping refills it.
3. Leaving and re-entering Favourites reconciles the list.
4. A night-mode change while inside Favourites, **after an unlike**, does not reconcile the list
   (D20).
5. Opening Favourites **before** liked songs have loaded shows them when they arrive, not a
   permanent empty state (D19).
6. `CarEmptyFavouritesScreen` exists, renders at the tab root when nothing is liked, and its CTA
   reaches Browse.
7. `CarArtistLikedSongsScreen` has the artist hero, Play all, its existing Shuffle, and hearts.
8. `CarTrackRow` renders a heart only when `onLikeToggle` is supplied; Home and the detail screens
   are unchanged.
9. A failed unlike reverts the heart and surfaces a non-retryable error.
10. `FavouritesSnapshotTest` covers all eight cases in §6.1, each verified to fail without its
    behaviour, with what was seen recorded.
11. D19–D25 recorded in `docs/aaos-DESIGN.md` beside A3's D11–D18.
12. Both flavors green; detekt zero; the §6.2 checklist executed and its outcome recorded in
    `docs/`, as A3 did in `docs/AAOS_A3_VERIFICATION.md`.
