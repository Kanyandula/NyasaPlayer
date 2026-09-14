# Codex review brief — AAOS A8 implementation plan (2026-09-13)

**Target:** `docs/superpowers/plans/2026-09-13-aaos-a8-playback-states.md`, which implements
`docs/superpowers/specs/2026-09-13-aaos-a8-playback-states-design.md` (you reviewed the spec earlier;
its four findings are folded in). Review the plan against the code. Ranked findings with file:line
evidence. Do not edit files.

## Decided — do not re-litigate

Everything in the spec's "Decisions taken", including: `:app` is untouched; no surface checks in shared
code; car downloads are A9; NoConnection is a behaviour.

## Least confident — look here first

1. **Will every code block compile against the file as it stands?** Especially Task 3 (edits inside
   `AutomotivePlayerViewModel`: `onPlaybackError` rewrite, `togglePlayPause`, the two observers) and
   Task 4 (`ErrorActions` inside a `Row`, `Modifier.weight`, the import removals).
2. **Task 2's test harness.** Will a `SimpleBasePlayer` that reports `setPlayWhenReady(...)` in
   `getState()` actually deliver `onPlayWhenReadyChanged` to the collector's controller listener after
   `transport.play()` / `pause()` through a real `MediaSession`, under Robolectric with the main looper
   idled? Is anything missing from the fake (e.g. a playback state) that Media3 1.5.1 requires?
3. **`stopIfStreamingOffline` re-entrancy.** It calls `transport.pause()` from inside the snapshot
   collector. Any path where it fires repeatedly, or fires for a local file, or never fires because
   `isOffline` flips before the buffering snapshot?
4. **Detekt/compose-rules.** Parameter order of `CarErrorOverlay` (`modifier` then optional
   `onSkipNext`), top-level `private fun` + `const val` naming in the ViewModel file, line length.
5. **Task 5's doc edits.** Do the row descriptions match the actual table columns in
   `docs/AAOS_PRD.md` §6.3 and `docs/AAOS_SCREEN_CONTRACT.md`?
