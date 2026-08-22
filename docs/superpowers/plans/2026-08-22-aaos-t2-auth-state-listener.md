# AAOS T2 - Automotive Auth State Listener Implementation Plan

> **For agentic workers:** This is a lifecycle/security fix, not an auth redesign. Keep the
> existing row #5 and row #15 null-user guards. Do not widen this into mobile navigation, token
> refresh or playback restore.

**Goal:** Make the automotive custom launcher observe Firebase auth state continuously, so a
revoked or invalidated session evicts the driver from `AuthenticatedApp` back to `CarAuthScreen`
instead of leaving signed-in UI with silently dead user content.

**Ticket:** `docs/tickets/T2-automotive-auth-state-listener.md`

**Verification command:** `./gradlew :automotive:testOemDebugUnitTest`

**Broader gate:** `./gradlew :automotive:testOemDebugUnitTest :core:data:testDebugUnitTest detekt
:automotive:lintOemDebug :automotive:assembleOemDebug :automotive:assemblePlaystoreDebug
:app:assembleDebug`

## Current baseline

Start from `main` after T1, T4 and T9 are merged. Current main has:

- Robolectric Compose test tooling in `:automotive`.
- `AuthRepository.currentUserId`, added so automotive tests do not need a constructible
  `FirebaseUser`.
- Favourites row #5 and row #15 guards in `AutomotiveContentViewModel`.
- `FirebaseSyncManager.start()` / `stop()` already idempotent.

Do not start from an old pre-T1 branch. T2's test story relies on the newer test harness and fake
repositories.

## Decisions

### D-T2.1: Auth state belongs on `AuthRepository`

Add a domain-shaped auth state flow to `AuthRepository` and implement it in
`FirebaseAuthRepository` with Firebase's live listener. Do not attach a raw
`AuthStateListener` directly in `AutomotiveAuthViewModel`.

Expected shape:

```kotlin
data class AuthSession(
    val userId: String? = null,
    val displayName: String = "",
) {
    val isAuthenticated: Boolean get() = !userId.isNullOrBlank()
}

interface AuthRepository {
    val authSession: Flow<AuthSession>
    // existing members stay
}
```

The exact name can change during implementation, but keep Firebase SDK types out of this flow so
tests can drive it without a mocking library.

### D-T2.2: Use a small sync interface, not a mocking dependency

Create a fakeable sync contract, for example:

```kotlin
interface CatalogSync {
    fun start()
    fun stop()
}
```

Have `FirebaseSyncManager` implement it and bind it through Hilt. Inject that interface into
`AutomotiveAuthViewModel` and `AutomotiveApplication`. This removes the concrete-class test
blocker recorded in the ticket without adding Mockito/MockK.

### D-T2.3: Session invalidation evicts UI but does not stop playback

On passive auth loss:

- set `CarAuthUiState.isAuthenticated = false`
- clear auth loading state
- stop catalog sync
- let `AutomotiveApp` naturally leave `AuthenticatedApp` and render `CarAuthScreen`
- do not send playback commands and do not stop `PlaybackService`

The rationale matches `docs/AAOS_PRD.md` US-2: setup/sign-in is refused while driving, but audio
from a previous session continues. Stopping a foreground playback session because a listener fired
is a distraction event and belongs in a separate product/security decision if that policy changes.

### D-T2.4: Explicit sign-in keeps its current completion boundary

The auth listener may emit an authenticated session before `signInWithGoogleToken()` finishes
creating the profile. Preserve the current visible behavior: explicit sign-in should not leave the
loading state or enter the shell until the sign-in method has finished its success path.

Practical rule: while a sign-in operation is in progress, ignore authenticated listener emissions
for UI entry. Always honor unauthenticated emissions, because they are revocations or failures.

## File plan

**Create**

| File | Responsibility |
|---|---|
| `core/data/src/main/java/.../api/AuthSession.kt` | Firebase-free auth state model |
| `core/data/src/main/java/.../sync/CatalogSync.kt` | Fakeable sync lifecycle interface |
| `core/data/src/main/java/.../di/SyncModule.kt` | Hilt binding from `FirebaseSyncManager` |
| `automotive/src/test/.../fake/FakeCatalogSync.kt` | Start/stop counters for auth tests |
| `automotive/src/test/.../viewmodel/AutomotiveAuthViewModelTest.kt` | T2 auth-state tests |
| `automotive/src/test/.../ui/AutomotiveAppAuthGateTest.kt` | Optional shell gate rendering test |

**Modify**

| File | Change |
|---|---|
| `core/data/.../api/AuthRepository.kt` | Add `authSession: Flow<AuthSession>` |
| `core/data/.../FirebaseAuthRepository.kt` | Implement Firebase auth listener flow |
| `core/data/.../sync/FirebaseSyncManager.kt` | Implement `CatalogSync` |
| `automotive/.../auto/AutomotiveApplication.kt` | Inject `CatalogSync`; keep startup bootstrap |
| `automotive/.../viewmodel/AutomotiveAuthViewModel.kt` | Collect auth state and control sync |
| `automotive/.../ui/AutomotiveApp.kt` | Prefer state-backed display name if added |
| `automotive/src/test/.../fake/InertRepositoryFakes.kt` | Add mutable auth-session fake support |
| `core/data/src/test/.../fake/FakeAuthRepository.kt` | Add auth-session fake support |
| `core/playback/src/test/.../MediaBrowseTreeTest.kt` | Add new interface member to test fake |
| `app/.../NyasaPlayerApplication.kt` | Optional: inject `CatalogSync` instead of concrete sync |
| `app/.../MainActivity.kt` | Compile-only adjustments if `AuthRepository` changes |
| `docs/aaos-DESIGN.md` | Record T2 decisions after implementation |
| `docs/tickets/T2-automotive-auth-state-listener.md` | Move status after verification |

## Task 0: Baseline and branch

**Purpose:** Avoid implementing T2 on a stale pre-T1 branch.

- [ ] Confirm `main` is clean.
- [ ] Confirm PR #37/T1 is present so Robolectric Compose tests exist.
- [ ] Start a fresh T2 branch from `main`.
- [ ] Run the current focused gate once:

```bash
./gradlew :automotive:testOemDebugUnitTest
```

**Acceptance criteria:** branch starts cleanly from the current post-T4/T9 baseline.

## Task 1: Add live auth session to `AuthRepository`

**Purpose:** Put Firebase's live auth state behind the shared data boundary.

- [ ] Add Firebase-free `AuthSession`.
- [ ] Add `val authSession: Flow<AuthSession>` to `AuthRepository`.
- [ ] Implement the flow in `FirebaseAuthRepository` with `FirebaseAuth.addAuthStateListener`.
- [ ] Remove the listener in `awaitClose`.
- [ ] Map `FirebaseUser?` to `AuthSession(userId, displayName)`.
- [ ] Use `distinctUntilChanged()` either in the repository or in ViewModels that collect it.
- [ ] Update every fake/test implementation of `AuthRepository`.
- [ ] Add a focused repository test if Robolectric can construct enough FirebaseAuth state;
      otherwise cover the contract through fakes and ViewModel tests.

**Technical notes**

Do not remove `currentUser`, `currentUserId` or `isAuthenticated`. They are still used broadly by
mobile, playback and repository code. T2 adds a live channel; it does not replace the existing
snapshot API in one slice.

**Acceptance criteria:** every module compiles with the new interface member, and automotive tests
can emit auth sessions without constructing a `FirebaseUser`.

## Task 2: Add fakeable sync lifecycle

**Purpose:** Make `AutomotiveAuthViewModel` testable without a mocking library.

- [ ] Create `CatalogSync` with `start()` and `stop()`.
- [ ] Make `FirebaseSyncManager` implement `CatalogSync`.
- [ ] Add a Hilt binding in a sync-specific module.
- [ ] Inject `CatalogSync` into `AutomotiveAuthViewModel`.
- [ ] Inject `CatalogSync` into `AutomotiveApplication`; preserve its cold-start behavior.
- [ ] Optionally update `NyasaPlayerApplication` to inject `CatalogSync` too, for consistency.
- [ ] Add a test fake that counts `start()` and `stop()` calls.

**Acceptance criteria:** `AutomotiveAuthViewModelTest` can construct the ViewModel directly with
hand-written fakes and no Mockito/MockK dependency.

## Task 3: Collect auth state in `AutomotiveAuthViewModel`

**Purpose:** Make `CarAuthUiState.isAuthenticated` live instead of construction-only.

- [ ] Seed UI state from the current snapshot as today.
- [ ] In `init`, collect `authRepository.authSession`.
- [ ] On unauthenticated emission:
      - set `isAuthenticated = false`
      - set `isLoading = false`
      - clear or leave `errorMessage` according to current sign-in error semantics
      - stop catalog sync
- [ ] On authenticated emission while no sign-in is in progress:
      - set `isAuthenticated = true`
      - update display name if `CarAuthUiState` carries it
      - start catalog sync
- [ ] On authenticated emission while explicit sign-in is in progress, defer UI entry until the
      sign-in success path completes.
- [ ] In `signInWithGoogleToken`, keep profile creation before successful UI entry.
- [ ] In explicit `signOut()`, call `authRepository.signOut()` and let the auth-session collector
      perform the final UI/sync transition.
- [ ] Keep `onGoogleSignInError()` and `clearError()` behavior.

**Acceptance criteria:** passive sign-out changes the ViewModel state without calling public
`signOut()`, and explicit sign-in does not enter the shell before its success path completes.

## Task 4: Prove the shell leaves signed-in UI

**Purpose:** Test the user-visible failure T2 was filed for.

- [ ] Add ViewModel tests:
      - initial authenticated session renders authenticated
      - passive auth loss renders unauthenticated and stops sync
      - passive auth restoration renders authenticated and starts sync
      - explicit sign-out delegates to repository and stops sync through the collector
      - auth loss during loading clears loading
- [ ] Add or extend a Compose test for the `AutomotiveApp` auth gate if practical:
      - render authenticated state and see the shell branch
      - emit unauthenticated state and see `CarAuthScreen`
- [ ] If full `AutomotiveApp` is too Hilt-heavy, extract a tiny internal route seam that selects
      auth vs shell from `CarAuthUiState`, similar to T1's Favourites route seam.

**Acceptance criteria:** a mutation that removes the auth-state collection fails by name, and a
mutation that keeps `isAuthenticated` true after a null auth emission fails by name.

## Task 5: Preserve row #5 and row #15 guards

**Purpose:** Ensure T2 does not undo the defensive content work that made the stale auth window
survivable.

- [ ] Do not remove the null-user guard in `reloadUserContent()`.
- [ ] Do not remove the split catalogue/user teardown guard in `loadContent()`.
- [ ] Run the existing Favourites boundary tests that describe rows #5 and #15.
- [ ] If auth fake changes weaken those tests, strengthen them before proceeding.
- [ ] Mutation-check locally by removing each guard and confirming the suite still fails.

**Acceptance criteria:** the existing guard tests still defend the asynchronous window after the
listener lands.

## Task 6: Manual verification and docs

**Purpose:** Close the ticket with executable and device evidence.

- [ ] Update `docs/aaos-DESIGN.md` with the T2 decisions:
      - auth state lives on `AuthRepository`
      - passive session invalidation evicts UI but does not stop playback
      - sync follows live auth state in the automotive shell
- [ ] Update `docs/tickets/T2-automotive-auth-state-listener.md` status and outcome.
- [ ] Run:

```bash
./gradlew :automotive:testOemDebugUnitTest :core:data:testDebugUnitTest detekt \
  :automotive:lintOemDebug \
  :automotive:assembleOemDebug \
  :automotive:assemblePlaystoreDebug \
  :app:assembleDebug
```

- [ ] Device check on `AAOS_AOSP_33_userdebug`:
      - launch signed in
      - invalidate or sign out the Firebase session externally if feasible
      - verify the custom launcher returns to `CarAuthScreen`
      - verify foreground playback is not stopped by the UI eviction
      - verify catalog sync restarts after signing in again
- [ ] If external invalidation is not feasible on the available account, record that carve-out and
      rely on the fake-driven ViewModel/route tests for the listener transition.

**Acceptance criteria:** T2 has JVM proof for the auth transition and honest manual evidence for
what could and could not be exercised on the emulator/account.

## Definition of done

- The automotive auth gate is live, not construction-only.
- Passive session invalidation returns the custom launcher to `CarAuthScreen`.
- Catalog sync starts/stops with live auth state in the automotive shell.
- Playback is not stopped by passive auth loss.
- Row #5 and row #15 guards remain covered.
- No mocking library is added for T2.
- The focused and broader Gradle gates pass.
