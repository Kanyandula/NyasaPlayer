# AAOS A7 — Settings, Profile and Auth: design

The last unbuilt slice of the original AAOS design, and the reason two controls in the system bar
have been disabled since A2.

## What exists today

**Verified in code, not taken from the PRD** — which is stale on the first point:

- **The driving gate is already built.** `UxFlags` derives `noSetup` from
  `UX_RESTRICTIONS_NO_SETUP`, `CarSheet` already has `Settings` and `Profile` members, and
  `GateResult` already refuses both with written reasons: *"Settings can only be changed while the
  vehicle is parked."* The PRD's line 52 ("the handler never reads `NO_SETUP`") predates that work.
  **A7 does not need to build restriction plumbing; it needs to open sheets that the plumbing is
  already waiting for.**
- **The system-bar controls are disabled, not missing.** `CarSystemBar` takes `onSettingsClick` and
  `onAvatarClick` today and drops them on the floor behind `@Suppress("UnusedParameter")`; both
  controls render with `clickable(enabled = false)` so they announce as disabled rather than
  looking live (FR-2.6). Their hit areas are already full size, so enabling them will not reflow the
  bar.
- **`CarAuthScreen` exists** with Google sign-in only, and T2 made its gate live: `AuthGate`
  follows `AuthRepository.authSession`, and catalogue sync starts and stops with it.
- **Sign-out lives on `CarLibraryScreen`**, marked in D14 for deletion in A7. It is there only
  because removing it in A3 would have left no way to sign out of a vehicle at all while the avatar
  is disabled.

So the slice is smaller than the screen contract suggests. What it is not is *simple*, because two
of its four screens raise questions the codebase has never answered.

## The two questions to settle before any code

### 1. What is a "profile"?

The contract's screen 20 says "current profile, switch profile, add profile". The codebase has one
notion of identity — a Firebase account, via `AuthRepository` — and AAOS has another: the platform's
own car users, which is why every `adb` command in this project carries `--user 10`.

They are not the same thing, and picking the wrong one is expensive:

- **App-level accounts** (recommended): "switch profile" means sign out and sign in as someone else,
  or switch between Firebase accounts the app remembers. Self-contained, testable, and it matches
  what `AuthRepository` already models. It does not survive a platform user switch, which is fine:
  the platform already gives each car user their own app data.
- **Platform users:** the app would ask `CarUserManager` to switch the vehicle's user. That is a
  system-level action, needs privileged permissions the app does not hold, and would put a
  whole-vehicle change behind a media app's avatar button. Reject it, and say so in the design record
  so nobody revisits it in six months.

**Recommendation: app-level accounts, and rename the screen in the contract if "profile" keeps
implying otherwise.**

### 2. What does the PIN protect, and where does it live?

Screen 2 is a PIN opt-in offered after first sign-in (US-3, priority P). The contract says what it
looks like; nothing says what it *does*. Options, cheapest first:

- **Nothing yet.** Ship A7 without the PIN screen and drop US-3 to a later slice. The other three
  screens are independently useful, and a PIN that gates nothing is worse than no PIN.
- **A local re-auth gate:** the PIN unlocks the app on this head unit, so a passenger cannot see or
  change the account. Requires storing a salted hash — `EncryptedSharedPreferences` or the keystore —
  and a decision about lockout after failures.
- **Anything server-side** is out of scope: the app has no PIN concept in Firestore and inventing one
  is a backend change.

**Recommendation: defer the PIN.** It is the only P-priority item in A7 that has no existing
machinery behind it, and it is the one that carries a security decision. Ship the three screens that
have clear behaviour; file the PIN separately with the storage question in its title.

## Proposed scope

**In A7:**

| Screen | What it does | Notes |
|---|---|---|
| `CarSettingsScreen` (14) | Account row, audio-quality preference, about, sign out | Opened by the system bar's gear; refused by `NO_SETUP` through the existing gate |
| `CarProfileSwitcherScreen` (20) | Current account, switch account, sign out | Opened by the avatar; same gate |
| `CarAuthScreen` (1) | Keep Google; add the retry-on-failure state the contract asks for | Phone and email sign-in are a separate decision — see below |

Plus: enable the two system-bar controls, and **delete sign-out from `CarLibraryScreen`** (D14's
condition is met the moment Settings exists).

**Deferred out of A7, each with a reason:**

- **`CarPinOptInScreen`** — the storage and lockout decisions above.
- **Phone and email sign-in on `CarAuthScreen`** — both need text entry, which `NO_KEYBOARD` refuses
  while driving, and phone sign-in needs an SMS round trip on a head unit that may have no SIM. The
  screen contract lists them; nothing in the app does them today; and Google sign-in already works.
- **Audio-quality preference actually changing anything** — the row can exist and persist a value,
  but nothing in `PlaybackService` reads a quality setting today. Either wire it or label the row
  honestly; do not ship a control that stores a preference nothing observes. That is the FR-2.6
  problem in a different costume.

## Decisions to record

- **D-A7.1** — a profile is an app-level account; the platform's car users are not the app's to
  switch.
- **D-A7.2** — the PIN is deferred, not designed-and-skipped; A7 ships no PIN affordance at all
  rather than a disabled one, because a disabled control is a promise.
- **D-A7.3** — sign-out moves to Settings and leaves the Library, closing D14.
- **D-A7.4** — settings and profile open as **sheets**, not destinations: `CarSheet.Settings` and
  `CarSheet.Profile` already exist in `CarUiLocation`, and the gate already refuses them by name.
  Adding nav destinations instead would mean a second gating path for the same restriction.
- **D-A7.5** — every A7 surface is parked-only, so none of them needs a driving-state design beyond
  the refusal the gate already renders.

## Risks

- **A settings screen invites scope.** Audio quality, downloads-over-cellular, clear cache, theme —
  the contract lists four rows and the temptation is a fifth. Anything that nothing reads is a
  control that lies.
- **Profile switching touches auth state that T2 made live.** `AuthGate` evicts the shell the moment
  `authSession` reports unauthenticated; switching accounts means passing through that state
  deliberately. Expect the shell to flash back to `CarAuthScreen` between accounts unless the
  switcher handles it.
- **Sign-out's confirmation overlay already exists in Library** and should move rather than be
  rewritten.

## Open questions

1. Does "switch profile" mean multiple *remembered* accounts, or just sign-out-then-in? The first
   needs credential storage the app does not have; the second is what a driver can already do.
   Recommendation: sign-out-then-in for A7, and say so on the screen.
2. Does anything read an audio-quality preference? If not, the row is out until something does.
