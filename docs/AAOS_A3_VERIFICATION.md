# AAOS Slice A3 — on-device verification record

Closes definition-of-done item 12 of
`docs/superpowers/specs/2026-08-09-aaos-browse-library-detail-design.md`: *"the §7.2
checklist executed and its outcome recorded"*.

- **Date:** 2026-08-09
- **Build:** `ek/aaos-a3-impl` at the tip that became PR #18, plus the PR #19 follow-ups
- **AVD:** `AAOS_AOSP_33_userdebug` (API 33, `userdebug`)
- **Why that AVD:** driving-state injection is refused on `user` builds. See
  `docs/AAOS_DRIVING_STATE_TESTING.md`.

> **Run one emulator at a time.** Two AAOS AVDs together put two QEMU processes at ~300%
> and ~260% CPU on a 6-core host and starve the guest — load average 4+, `Choreographer`
> dropping 30–50 frame batches, `dumpsys window` reporting `mCurrentFocus=null`. Taps are
> then silently swallowed and the app *looks* like it has a state bug. An hour went into
> chasing an apparent "Library resets to Home" defect that was purely input starvation: the
> process had never restarted, and the symptom vanished when the second AVD closed. Before
> believing any UI-state finding, check `mCurrentFocus` and guest load.

## Verified

| # | Check | Result |
|---|---|---|
| 1 | Browse renders real Firestore genres | Pass |
| 2 | Browse cards fill the content width; scrollbar adjacent to its content | Pass |
| 3 | Every Browse card plays or explains itself | Pass — see "Defect found" |
| 4 | Library renders the contract's category rows in order | Pass |
| 5 | Downloads row present, visibly dimmed, non-interactive (D13) | Pass |
| 6 | Favourites is one count card, not a second liked-songs list | Pass |
| 7 | `PlaylistRepository` reaches the car — real playlists render | Pass |
| 8 | Playlist detail: title, derived hero artwork, tracks in `songIds` order | Pass |
| 9 | Album detail: hero, Play/Shuffle/Back, **no Download button** (D12) | Pass |
| 10 | Album tracks render in `songIds` order, not natural order | Pass — see below |
| 11 | Play starts at track 1 rather than resuming the prior queue | Pass |
| 12 | Back from a detail screen returns to Library | Pass |
| 13 | **Process death — artist detail** (D16) | Pass |
| 14 | **Process death — playlist detail** (D17) | Pass |
| 15 | **Process death — album detail** (`getAlbumById` path) | Pass |
| 16 | Library cards stay 180dp while Browse cards flex | Pass |
| 17 | Driving-state injection works and restores to parked | Pass |

### The three process-death checks are the point of the exercise

Unit tests cannot see them. Each was run by backgrounding the app, `adb shell am kill`,
confirming the PID actually changed, and relaunching.

**D17 (playlist) is the one that justified a spec change.** An earlier draft of the spec
resolved playlists from `contentState.playlists`. At the moment `rememberSaveable` restores
the destination, `observePlaylists()` has not emitted, so that version would have returned a
permanently empty screen — and nothing re-triggers the load, because the driving effect is
keyed on the destination alone. Resolving via `getPlaylists(userId).first()` suspends until
the emission instead of racing it. The device confirms the restored screen comes back with
its tracks.

**D16 (artist) confirms removing the resolve-or-clear rule was right.** The artist screen
renders its name from the restored destination with `favoriteArtists` still empty; a rule
that cleared the destination when the artist was missing would have fired in exactly that
gap, on every restore.

### Ordering (spec §3.3) was verified with adversarial data

A temporary album was seeded with `songIds` in the deliberately non-natural order
`[203, 190, 170]`. The tracklist rendered in that order, confirming `getSongsByIds`'
request-order guarantee end to end — repository → `loadAlbumDetail` → `CarDetailState.tracks`
→ the rendered list. Until then the guarantee rested on a unit test against a fake. The
seeded document was deleted after the run; the `albums` collection is empty again.

## Defect found on device that review did not catch

A genre whose songs could not be resolved was a **silently dead card**: the tap produced no
playback, no error, no log entry. That is the CTA violation the slice removes elsewhere — the
old name-matching lookup was deleted, but the `if (songs.isNotEmpty())` guard behind it was
carried over unchanged, so the *cause* went and the *failure* stayed.

It survived nine task reviews, three scoped re-reviews, an opus whole-branch review and 41
unit tests. Every reviewer verified the thing the spec said was fixed. Tapping the card found
it in seconds.

Fixed in PR #18, then corrected again in PR #19 when review found the first fix gated on
`Genre.songIds` while the tap resolves through `Song.genreIds` — two sources that can
disagree. The gate is gone; the error path reads the authoritative source.

**Takeaway: tap every CTA on a rebuilt screen.** Reviews check that the named cause is gone.
They do not check that the failure is.

## Not verified

These remain open and are not covered by unit tests either.

| Check | Why not run |
|---|---|
| Driving refusal at drill depth | Needs an injected `maxContentDepth` of 0. This AVD reports `UxR 16` (`NO_VIDEO`) with no `LIMIT_CONTENT`, so the cap stays unrestricted and depth 1 is *correctly* allowed. Spec §7.2 check 4 already says this cannot be observed by driving the emulator. |
| Drive-transition eviction from inside a detail screen | Same cause — no eviction occurs while the reported cap exceeds depth 1. |
| User-switch playlist leak (spec §7.2 check 7) | Needs a second account. The code path is reviewed — `playlistsJob` is cancelled and `playlists` cleared in the same `_contentState.update` as the other user-scoped lists — but unproven on device. |
| Album duplicate-`songId` dedupe | Would need an album seeded with a repeated id. Covered by unit tests only. |
| PR #19's three fixes | Reasoned from layout arithmetic and the error contract, not observed. Browse's loading state and an error overlay are worth a glance. |
