# Codex review brief — T29 design (2026-09-14)

**Target:** `docs/superpowers/specs/2026-09-14-t29-network-monitor-design.md`. Review it against this repo's
code and the Android platform contract. Ranked findings with evidence (file:line, or the platform
source/doc you checked). Do not edit files.

## Decided — do not re-litigate
- Online = default network has `INTERNET` and not `CAPTIVE_PORTAL` (not `VALIDATED`).
- A pure tracker keyed by `Network.getNetworkHandle()`, tested with plain JUnit; no Robolectric.
- Mobile's behaviour changes on purpose.

## Least confident — look here first
1. **Callback semantics for `registerDefaultNetworkCallback`** on API 24–35: ordering of `onAvailable`,
   `onCapabilitiesChanged`, `onLost` across a default-network switch and a total loss. Is there any
   sequence where the tracker ends up wrong — stuck offline after reconnect, or online after loss?
   The Android SDK sources are at `~/Library/Android/sdk/sources/android-34`.
2. **API 24–25**: is a capabilities callback really not guaranteed after `onAvailable` there, and is the
   spec's one synchronous read the right fallback?
3. **`getNetworkHandle()` identity**: stable and unique for the life of a network? Any reuse risk that
   would let a stale event match the current network?
4. **The starting value + registration race**: the constructor reads `activeNetwork` and seeds the
   tracker, then registers. Can an event slip between them, and does the design recover?
5. **Threading**: callbacks arrive on ConnectivityThread; the tracker is mutated there and read nowhere
   else, and `MutableStateFlow` is written there. Any visibility issue the design must handle?
6. **Consumers**: does anything in `app/`, `automotive/` or `core/` depend on the old `VALIDATED`
   meaning (e.g. assumes online ⇒ a request will succeed)?
