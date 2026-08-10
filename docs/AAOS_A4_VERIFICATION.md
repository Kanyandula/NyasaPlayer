# AAOS Slice A4 — on-device verification record

Closes definition-of-done item 12 of
`docs/superpowers/specs/2026-08-09-aaos-favourites-design.md`: the §6.2 checklist executed
and its outcome recorded.

- **Date:** 2026-08-10
- **Build:** `ek/aaos-a4-impl` at the tip of the 15-commit implementation branch
- **AVD:** `AAOS_AOSP_33_userdebug` (API 33, `userdebug`), one emulator only
- **Account:** the real signed-in user, against live Firestore — not a fake

## Verified

| # | §6.2 check | Result |
|---|---|---|
| 1 | Favourites renders hero, count, Play all, Shuffle, rows; every control does something | Pass |
| 2 | Unlike holds the row and hollows the heart; tapping again fills it | Pass |
| 3 | Leave Favourites and return: the unliked row is gone | Pass |
| 4 | **Unlike, then a config change without leaving the tab — row still there, still hollow (D20)** | Pass |
| 5 | Process death inside Favourites returns a freshly reconciled list, no crash | Pass |
| 6 | Empty state renders screen 17 with a working Browse CTA | Pass |
| 7 | Artist screen: hero, Play all, Shuffle, hearts; unlike removes the row (D25) | Pass |
| 8 | Driving: unlike remains one tap | Pass — truncation not observable, see below |
| 9 | Every CTA on both rebuilt screens tapped | Pass |

### Check 4 is the one nothing else can see

The freeze lives in the ViewModel, so only a real Activity recreation proves it survives one.
`cmd uimode night yes` recreated the Activity — the window token changed from `fd0e12e` to
`41f89f5` — and the Favourites screen came back still on its tab, still showing the unliked
row with a hollow heart and the count still frozen at 12. A freeze held in composable state
would have been lost there and the row would have vanished under the driver.

### The freeze/thaw split was verified on both sides

The two screens pass opposite `freeze` values to the same callback, and both behaviours were
observed:

- **Favourites (`freeze = true`)** — unliking held the row in place, count frozen at 12.
- **Artist liked songs (`freeze = false`)** — unliking dropped the row immediately, "4 liked
  songs" → "3 liked songs", hero artwork re-derived.

Thaw was observed too, and by accident, which makes it better evidence: opening the full
player takes the shell off the Favourites tab, which fires `closeFavourites()`. Returning
showed 11 songs with the unliked row gone — the reconciliation D19 specifies.

### Check 5 used real process death

`am kill` is refused while playback holds the foreground service — the PID does not change and
the test silently passes for the wrong reason. Playback was stopped first, then the app
backgrounded and killed: PID `9218` → `10068`, a genuine restart. It restored **into the
Favourites tab** with a reconciled 11-song list, all hearts filled, and the crash buffer empty.

### The queue/display divergence fix was confirmed on device

Task 4's Critical finding was that a tap on a held-back row played a different song. With the
list frozen at 12 and row 1 held back, tapping row 2 played **"Softly and Tenderly"** —
`dumpsys media_session` confirmed the metadata. `Play all` started at `active item id=0` on the
held-back row, so the queue matches what is drawn. `Shuffle` jumped to `active item id=9`.

A useful cross-check fell out of it: the session's custom action flipped between `Like` and
`Unlike` in step with the heart, so the media-session projection and the car UI agree.

### The empty-vs-loading window (I3) was opened deliberately

This is the case a driver hits and no test on the device can stage by waiting — the first
Firestore emission normally arrives before a tap can land. It was forced by deleting only the
Firestore local cache and throttling the link:

```
adb shell run-as com.example.nyasaplayer --user 10 \
  rm -f /data/user/10/com.example.nyasaplayer/databases/firestore.*
adb emu network speed gsm && adb emu network delay gprs
```

Favourites then rendered the **loading skeleton**, not "No favourites yet" — which is the whole
point of `likedSongsLoaded`. Before the fix this window showed a false empty state.

## Observations that are not A4 defects

**A Firestore listener that stalls long enough does not always recover.** After the cache was
wiped *and* the link throttled to GPRS, the liked-songs listener emitted an empty snapshot and
had not delivered the server data 37 seconds after full speed was restored — the screen stayed
on the empty state, and leaving and returning did not fix it. A clean restart showed all 12
immediately. This is a data-layer characteristic of an artificial cache-wipe-plus-stall
combination, not something this branch introduced, and the same code path behaves normally
otherwise. Worth knowing before anyone reads a false empty state as a Favourites bug.

**Taps are swallowed until Compose has laid out.** `mCurrentFocus` naming the activity is not
sufficient — several rail taps issued within a second of focus did nothing, and the same
coordinate worked once the shell had settled. This cost several rounds before it was
recognised. Wait for focus *and* a few seconds, or verify the tap landed before trusting a
screenshot. This is the same class of false finding as A3's input starvation.

**`pm clear` targets user 0, which is not the user the app runs as.** On this image the driver
is user 10 and the app's live data is at `/data/user/10/com.example.nyasaplayer/`. A `pm clear`
against the package cleared an unused user-0 profile and changed nothing observable. Use
`run-as com.example.nyasaplayer --user 10` to reach the real data directory.

## Not verified

| Check | Why not run |
|---|---|
| Row truncation at `maxCumulativeContentItems` (§6.2 check 8, first half) | Driving state was injected successfully — `DO: true UxR: 255`, further than A3 reached — but the account has 12 liked songs, below the cap, so there is nothing to truncate. The cap itself is covered by unit tests. |
| Unlike failure path on device | The revert-and-error path is unit-tested; provoking a real `unlikeSong` failure needs a rules change or an offline write path that Firestore's offline queue defeats. |
| User-switch clearing of the freeze | Needs a second account. Reviewed in code — `favourites` and `pendingUnlikes` clear in the same `_contentState.update` as the other user-scoped state — but unproven on device, same gap A3 recorded. |

## Data touched

Two songs were genuinely unliked during the run and restored afterwards: mediaId `16`
("Softly and Tenderly") and `96` ("Worship Medley: Holy Ground/…"). The account is back to 12
liked songs. Their original `likedAt` timestamps were lost with the deleted documents and now
read `2026-08-10`.
