# T29 - NetworkMonitor can miss the network going away

- **Slice:** correctness, shared by both surfaces
- **Depends on:** —
- **Status:** Done — see Outcome and `docs/T29_VERIFICATION.md`
- **Verification Command:** `./gradlew :core:common:testDebugUnitTest`, plus a device pass
- **Design Reference:** `docs/AAOS_A8_VERIFICATION.md` (findings); D71
- **Risk Tags:** shared module, both surfaces, mobile behaviour change
- **Affected Modules:** `:core:common` (and every consumer of `NetworkMonitor.isOnline`)

## Problem

During the A8 device pass, the first airplane-mode toggle left the car app believing it was online:
no offline banner, `isOffline` false, while `dumpsys connectivity` reported no default network. A
relaunch, and a second toggle, both flipped it correctly. One miss in two live transitions; not yet
reproduced on demand.

The likely cause is in `NetworkMonitor`: every callback — `onAvailable`, `onLost`,
`onCapabilitiesChanged` — answers by calling `checkCurrentConnectivity()`, which reads
`connectivityManager.activeNetwork` and `getNetworkCapabilities(...)` synchronously. The platform
documentation for `NetworkCallback` warns against exactly that: the objects those synchronous calls
return are not guaranteed to be current inside a callback. An `onLost` that reads a stale active network
answers "online", and nothing corrects it until the next callback.

It matters more after A8. The car's fail-fast offline guards (D71) and mobile's own offline checks
read `isOnline`; when it is wrong, both fall back to streaming and failing slowly.

The same staleness cuts the other way, too: a network not yet `VALIDATED` after boot or
reconnect, one that never validates, or a stale capabilities read can leave `isOnline` false when
the vehicle is actually online. Since A8 that refuses every song tap and shuffle from the car's
custom UI.

## Scope

- **Reproduce first**, on the AAOS emulator and a phone: toggle airplane mode repeatedly with the app
  in the foreground and record how often the banner fails to appear.
- Derive the answer from the callbacks' own arguments rather than synchronous queries, through a pure
  tracker: `onLost` → offline; `onCapabilitiesChanged(network, caps)` → `INTERNET` and not
  `CAPTIVE_PORTAL`; `onAvailable` waits for the capabilities that follow it (API 24–25 excepted — see
  the spec).
- A JVM test for the state machine, with the callback driven directly.

## Out Of Scope

- The car's classification of every `IOException` as "No Connection" — separate finding in the A8 record.

## Acceptance Criteria

- Given the app is in the foreground, when the network goes away, then `isOnline` becomes false
  every time across repeated toggles, on both surfaces.
- Given the network returns and validates, then `isOnline` becomes true.
- Given a validated network, then `isOnline` is true within a bounded time, including after boot.

## Notes

This changes mobile behaviour, which is why A8 filed it rather than fixing it (spec decision 4: A8
leaves `:app`'s behaviour untouched). It needs its own phone pass.

## Outcome

Online now means the default network has `INTERNET` and is not a `CAPTIVE_PORTAL`, not `VALIDATED`
(spec decision 1): a network that never validates — an OEM or telematics APN that blocks Google's
probe — used to read offline forever, and since A8 that refuses every song tap on the car; a false
offline is now worse than a false online, which only falls back to the slow failure.

Baseline, measured on `main` on 2026-09-14 with the protocol in the spec's Testing section: 1 miss in
20 valid transitions (an offline transition where the system settled offline and the app stayed
online).

Mobile's behaviour changes too: more networks now read online, since an unvalidated network no
longer shows the banner or blocks offline checks.
