# Codex review brief — AAOS A8 design (2026-09-13)

**Target:** `docs/superpowers/specs/2026-09-13-aaos-a8-playback-states-design.md`. Review it against
the code in this repo. Report findings ranked by severity with file:line evidence. Do not edit files.

## Project

NyasaPlayer: Android, Kotlin, Jetpack Compose, Hilt, Media3 1.5.1. Modules: `:core:common` <-
`:core:data` <- `:core:playback` <- `:app` (mobile) and `:automotive` (AAOS custom launcher, `oem`
flavor). `CLAUDE.md` has the architecture. `docs/aaos-DESIGN.md` holds numbered decisions (D1–D70).

## Decisions already taken — do not re-litigate

- Car downloads leave A8 for a later phase (A9).
- NoConnection is a fail-fast behaviour reusing the existing overlay, not a full screen.
- The offline rule is one shared predicate in `:core:playback`, not a car-only copy and not a
  `PlaybackService` refusal.

## Where I am least confident — look here first

1. **Invented or wrong names.** Every class, function, parameter and field the spec names should
   exist exactly as written (or be explicitly new). Check `CarErrorOverlay`, `CarPillButton`,
   `carTouchTarget`, `CarPillButtonHeight`, `AutomotiveUiState`, `observePlaybackSnapshot`,
   `observeNetworkState`, `resolveSongUri`, `handleOfflineBuffering`, `Song.resolvedAudioUrl`,
   `PlayerError.isRetryable`.
2. **The mobile refactor's equivalence claims** (spec §3). Is each `isPlayableNow` substitution
   truly behaviour-identical to the current `PlayerViewModel` code, including `shufflePlay`'s `zip`
   and `togglePlayPause`'s null-current-song case?
3. **The car `togglePlayPause` restructure** (spec §2). Does mirroring mobile preserve the T14
   rebuild-on-tap path (`BasePlayerStateCollector.onControllerLost`)?
4. **The `file:` prefix claim.** Is `File.toURI().toString()` really `file:/...` here, and does
   anything else produce local URIs in another form?
5. **The Media3 retry claim** ("Try again already retries" via `handlePlayButtonAction`). Check it
   against the Media3 1.5.1 sources in `~/.gradle/caches`.
6. **The buffering guard** (`snapshot.isBuffering && isOffline`). Could it fire in a state where it
   should not — e.g. a restored-but-paused session, or the brief buffering at the start of every
   online play?
