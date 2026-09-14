# Codex review brief — T29 implementation plan (2026-09-14)

**Target:** `docs/superpowers/plans/2026-09-14-t29-network-monitor.md`, implementing
`docs/superpowers/specs/2026-09-14-t29-network-monitor-design.md` (you reviewed the spec; its three
findings are folded in). Review the plan against the code. Ranked findings with file:line. Do not edit.

## Decided — do not re-litigate
The spec's decisions: the `INTERNET && !CAPTIVE_PORTAL` rule; a pure tracker with plain JUnit; register
before seeding; API 24–25 null capabilities count as online; mobile changes on purpose.

## Least confident — look here first
1. Will Task 2's `NetworkMonitor` compile and pass detekt as written: `private inline fun update(...) =
   synchronized(lock) { ... }` touching private members; expression-bodied overrides returning `update`'s
   result; `Build.VERSION_CODES.O` in a minSdk-24 module; lint's view of `activeNetwork`.
2. Does Task 1's test set actually pin every behaviour the spec lists, and would each test fail if the
   corresponding line of `DefaultNetworkState` were removed?
3. Is there any consumer or DI provider that constructs `NetworkMonitor` in a way the new init order
   (register, then seed) could break — e.g. on a thread where `registerDefaultNetworkCallback` throws?
4. Task 4's script edits: will the `NT_LAUNCH` substitution work in zsh as written?
