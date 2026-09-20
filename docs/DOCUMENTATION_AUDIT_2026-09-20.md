# Documentation audit — 2026-09-20

A record of what was found, not a task list. Everything actionable here was either
applied on `ek/docs-audit-refresh` or moved to `docs/BACKLOG.md`; do not read the
findings below as outstanding work.

- **Baseline:** `main` at `441526f` (*Correct T37: say which findings were actually checked*)
- **Scope:** all 115 tracked Markdown files, plus the Notion pages the repo links to
- **Method:** two independent passes — one by Codex, one by Claude — each claim checked
  against the source before it was recorded here

## What was wrong

The rot was concentrated in the documents that read as current truth. The dated records —
`docs/*_VERIFICATION.md`, `AAOS_SHIP_RECORD.md`, `TEST_MUTATION_SWEEP.md`, the
`docs/superpowers/` plans and specs — were accurate as dated records and were left alone.

| Document | Finding | Evidence |
|---|---|---|
| `README.md` | Stated the 2026-04-23 Option-B "no custom playback or browse UI" decision, reversed 2026-08-02. Named `PlayerManager`, `PlayerModule`, `CarAccountScreen`, `CarAudioQualityScreen`, `CarAboutScreen`, `SignOutConfirmationDialog`, and `APPLICATION_PREFERENCES` settings — none exist. Called `PlaybackStatePersistence` disk-based. | `AutomotiveApp.kt`; `PlaybackStatePersistence` takes `UserRepository` |
| `CLAUDE.md` | "Unit tests live in `core/data/src/test/`" — they span five modules, `:automotive` largest at 43 files. 7 car screens (17). 3 `CarScreen` tabs (4). `PlayerModule` (gone). `AutoAppModule` provides `SessionToken` (`PlaybackModule` does). "all 7 ViewModels have a CEH" (12 of 14). | source tree; `CarScreen.kt`; `PlaybackModule.kt:22` |
| `CLAUDE.md`, `CODING_GUIDELINES.md`, `PRODUCTIVITY_TIPS.md` | All three said a pre-commit hook enforces Detekt and Lint. **It does not run.** `core.hooksPath` is set globally to `~/.claude/git-hooks`, so git ignores `.git/hooks/` — where `install-hooks.sh` writes. The documented lint command also omitted `:core:playback` and `:automotive`. | `git config --get core.hooksPath`; empty `.git/hooks/` |
| `docs/stitch-screens/README.md` | Repeated the superseded Option-B decision with no banner. | `AAOS_PRD.md` §3.3 |
| `docs/AAOS_ARCHITECTURE.md` | Claimed `:core:data` owns a preferences DataStore and `AudioQualityPreference` (neither exists — T20 is unspecced), and "No `androidx.car.app`" (the build file uses both artifacts). Roadmap described finished phases in future tense. | `automotive/build.gradle.kts:104-105` |
| `docs/AAOS_PRD.md` | Header read `Draft for review` for a shipped programme. Q1 and its risk row read open; answered 2026-08-08. | `AAOS_SHIP_RECORD.md`; `AAOS_DRIVING_STATE_TESTING.md` |
| `docs/AAOS_SCREEN_CONTRACT.md` | `CarChip` marked "not built"; skeleton named `CarLoadingSkeleton`. | `CarControls.kt:41`; `CarRowSkeleton.kt:31` |
| T10, T13, T14, T25, T29 | All five said a phone/mobile pass was owed, in both the Status line and the Outcome body, while `BACKLOG.md` and `AAOS_SHIP_RECORD.md` recorded those five checks cleared on 2026-09-19. T25 preserved a resolved blocker (low storage on `Medium_Phone_API_35`) as current. | each ticket's `docs/T*_VERIFICATION.md` gained a "run 2026-09-19" section |

## What was checked and found sound

Tickets as a system — all 40 carry an accurate `**Status:**` line, and the five above were
the only drift. Verification records, `AAOS_SHIP_RECORD.md`, `AAOS_COMPLIANCE.md`,
`aaos-DESIGN.md`, `CRASH_REPORTING.md`, `BACKLOG.md`. `AAOS_UI_REDESIGN_PLAN.md` and
`aaos-stitch-regen-prompts.md` both carry correct superseded banners. GitHub has zero
issues, open or closed.

`docs/superpowers/` was left unlabelled deliberately: dated filenames already read as
history, and a directory README earns nothing until someone actually misreads one.

## External

The Notion mirror had diverged from local — see the dated note in that workspace. The
lesson worth keeping: phase status lived in two places and only one was maintained. Where
a Notion page and a repo doc cover the same ground, the page should point at the repo
rather than copy it.
