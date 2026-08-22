# T2 — `AuthStateListener` for the automotive shell

- **Slice:** auth flow — cross-cutting, not a screen slice
- **Depends on:** T1 for Compose test tooling; plan adds a fakeable sync seam
- **Status:** Plan ready — implementation not started
- **Plan:** `docs/superpowers/plans/2026-08-22-aaos-t2-auth-state-listener.md`
- **Verification Command:** `./gradlew :automotive:testOemDebugUnitTest`
- **Design Reference:** `.claude/loop/ledger.md` rows #5, #15, #16
- **Risk Tags:** lifecycle · auth · silent failure
- **Affected Modules:** `:automotive`

## Problem

**The automotive shell decides once, at construction, whether the user is signed in, and never
looks again.** A session invalidated server-side — password change on another device, token
revoked, account disabled — leaves the driver inside a signed-in shell whose user-scoped screens
quietly stop updating. Nothing on screen indicates anything is wrong.

Evidence chain, each link verified during the A4 post-merge review:

| Where | What |
|---|---|
| `FirebaseAuthRepository.kt:20` | Firebase exposes auth state **live** — the data is available |
| `AutomotiveAuthViewModel.kt:35-37` | `_uiState` is seeded **once at construction** from a snapshot |
| `AutomotiveAuthViewModel.kt:57`, `:71` | only `signInWithGoogleToken` and `signOut` ever write it |
| `automotive/src`, `core/data/src/main` | **zero** occurrences of `AuthStateListener` |
| `AutomotiveApp.kt:77` | the entire shell is gated on that stale snapshot |

So `authRepository.currentUserId` can return null while `AuthenticatedApp` is still composed.
Every consumer downstream then has to defend itself against a null id it arguably should never
see. Two such defences were built as review rows:

- **Row #5** — `reloadUserContent()` treated a null id as an account switch, cancelling all three
  user-scoped collectors and clearing their state, after which each restart hit its own
  `?: return` and no-op'd. Fixed with `?: return` on the id.
- **Row #15** — `loadContent()` cancelled six collectors and cleared four flags describing them,
  but three of the six abort on a null id. A Retry therefore left the flows dead with their flags
  reset and no error to render a Retry from. Fixed by splitting the teardown and guarding the
  user-scoped half.

## The guards stay when this lands

**This is the most important line in the ticket.** An `AuthStateListener` fires **asynchronously**.
A `loadContent()` already in flight still observes the null id. The listener shortens the window
and reduces the frequency; **it does not close the hole.**

Both guards are also correct for a reason that has nothing to do with auth being broken: at cold
start the id is legitimately null before auth resolves, and no listener changes that. They are the
review loop's rule — *a field is cleared by the recovery of the thing that set it, and by nothing
else* — applied where it always belonged.

**Do not delete the row #5 or row #15 guards as redundant once this lands.** They are not
redundant, and a suite that passes without them proves only that the tests do not reach the
window. This is recorded in the ledger against row #16 for the same reason it is recorded here.

## User Impact

**Who:** any driver whose session is invalidated while the app is running — password changed
elsewhere, token revoked, account disabled or deleted.

**What happens if we do nothing:** the shell keeps rendering as signed in. Liked songs, recently
played and playlists stop updating and never resume. The driver has no error, no prompt, and no
route to sign in again short of killing the process. The rows #5 and #15 guards mean the screens
now hold their last good content rather than stranding on a skeleton — which is better, and is
also why the failure is now **completely silent**.

Frequency is low. Severity is that nothing surfaces it, which is why this was filed major rather
than minor.

## Scope

- Observe Firebase auth state continuously rather than sampling it once. `FirebaseAuthRepository`
  already sits on the live source; the natural shape is exposing it as a `Flow`/`StateFlow` there
  and having `AutomotiveAuthViewModel` collect it, rather than attaching a raw listener in the
  ViewModel.
- Drive `AutomotiveApp.kt:77`'s gate from that observed state so a dead session takes the app off
  `AuthenticatedApp` and back to `CarAuthScreen`.
- Decide and implement what happens to in-flight playback when the session dies (see D-1).

## Out Of Scope

- **Removing the row #5 or row #15 guards.** See above. Non-negotiable.
- **The mobile app.** `:app` has its own auth flow and its own shell gating. If the change lands in
  `:core:data`'s `AuthRepository`, check the mobile side still compiles and behaves, but do not
  redesign it here.
- **Token refresh, re-auth prompts or silent re-authentication.** Getting the driver back to the
  auth screen is this ticket. Getting them signed in again without friction is a product feature.
- **The OEM media template path.** `PlaybackService` and `MediaBrowseTree` authenticate separately
  and are not implicated.

## Acceptance Criteria

- Given the driver is inside the shell, when the Firebase session is invalidated server-side, then
  the shell leaves `AuthenticatedApp` and presents `CarAuthScreen`.
- Given the session is invalidated, when the driver is on Favourites, then they are not left on a
  screen whose content silently stopped updating.
- Given the app is starting and auth has not yet resolved, when `loadContent()` runs with a null
  id, then the row #15 guard still applies — no user-scoped collector is cancelled and no
  user-scoped flag is reset.
- Given this change is complete, when the row #5 and row #15 guards are each removed in turn, then
  the suite still fails. Their defending tests must not have been weakened by this work.
- Given `detekt` and lint run, then both pass with no new baseline entries.

## Decisions resolved by the plan

- **D-1 — Playback continues when the session dies.** The custom launcher leaves signed-in UI and
  catalog sync stops, but T2 sends no playback command. Stopping foreground audio from an async
  listener is a separate product/security decision.
- **D-2 — Auth state belongs in the repository.** `AuthRepository` will expose a Firebase-free
  auth-state flow. `AutomotiveAuthViewModel` collects that flow instead of owning a raw Firebase
  listener.

## Affected Areas

- **Modules:** `:automotive`; `:core:data` only if D-2 chooses the repository shape
- **Screens:** `AutomotiveApp.kt` (the gate at `:77`), `CarAuthScreen`
- **APIs:** `AutomotiveAuthViewModel`, `AuthRepository` / `FirebaseAuthRepository`
- **Storage:** none

## Risk Areas

- **Security:** this *is* the security-relevant part — a revoked session currently continues to
  present as valid on the client. Note that the client gate is a UX affordance, not a control:
  Firestore rules remain the enforcement boundary and must not be assumed to be backed up by it.
- **Lifecycle:** `AutomotiveActivity` declares no `configChanges`, so a night-mode flip recreates
  it and re-runs `LaunchedEffect(Unit)`. Any new auth effect must be idempotent under that, which
  is the same trap D20 was written for on the Favourites freeze.
- **Compatibility:** `minSdk 29`, `targetSdk 35`.
- **Data migration:** none.

## Plan decisions

- Auth state will be exposed as a Firebase-free flow on `AuthRepository`, then collected by
  `AutomotiveAuthViewModel`.
- Passive session invalidation will evict the custom launcher back to `CarAuthScreen` and stop
  catalog sync, but it will not stop foreground playback.
- `FirebaseSyncManager` will be hidden behind a small fakeable sync interface, so T2 does not add a
  mocking library.
- The row #5 and row #15 null-user guards stay. The listener reduces the stale-auth window; it
  does not eliminate every in-flight read of a null user id.

## Notes — the test story no longer needs a mocking library

`:automotive` has **junit and coroutines-test only**. `AutomotiveAuthViewModel`'s constructor takes
`FirebaseSyncManager`, a concrete class wrapping `FirebaseFirestore` plus four DAOs, so it cannot
be hand-faked the way the six repository interfaces in `InertRepositoryFakes.kt` were — there is no
interface to implement.

The implementation plan resolves this by adding a tiny sync lifecycle interface and injecting that
instead of the concrete manager. T1 already supplied the Compose/Robolectric half of the test story.
