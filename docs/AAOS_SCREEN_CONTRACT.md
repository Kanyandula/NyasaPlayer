# AAOS Screen and Component Implementation Contract

| | |
|---|---|
| **Applies to** | 20-screen `oem` AAOS launcher |
| **Status** | Binding implementation contract for A2-A8 |
| **Date** | 2026-08-03 |
| **Primary PRD** | `docs/AAOS_PRD.md` |
| **Design system** | `docs/aaos-DESIGN.md` |
| **Compliance gates** | `docs/AAOS_COMPLIANCE.md` |

This document answers the implementation-readiness question the PRD should not absorb: every
screen, CTA, state, shared component, animation rule and colour rule needed to build the AAOS
UI upgrade.

If documents conflict, resolve in this order:

1. `docs/AAOS_COMPLIANCE.md` for AAOS safety and variant compliance.
2. `docs/AAOS_PRD.md` for product scope and phase acceptance.
3. This file for per-screen and shared-component implementation detail.
4. `docs/aaos-DESIGN.md`, `docs/aaos-screens.html`, `docs/aaos-app.html` for visual reference.

The Desktop design notes in `/Users/admin/Desktop/AAOS-Design` are historical input. Their
72px touch target, 48px system bar and pending-screen status are superseded by checked-in docs.

## Global Implementation Rules

- No screen owns the system bar, navigation rail or mini-player. Those are shared chrome.
- Every interactive control has a minimum target of 76dp on the smallest side.
- Text sizes use `sp`; spacing, shape and touch targets use `dp`.
- Letter spacing is `0` in Android implementation.
- Gold `#C9A84C` is used only for active state, progress, focus and primary CTA fills.
- Text on gold is `#0A0A0C`; white on gold is forbidden.
- Non-disabled text/surface pairs must measure at least 7:1.
- No auto-scrolling text. Use ellipsis and stable row heights.
- App voice search is system/Assistant driven through `PlaybackService`; no in-app recording UI.
- Driving mode is based on `CarUxRestrictions`, not a local "driving" boolean.
- **Location gating and action gating are different mechanisms.** Whether a screen may be
  shown is decided by `gate(location, state)`. Whether an action on a permitted screen may
  run — queue remove/reorder/clear, download delete — is decided by the screen reading
  `UxRestrictionState` directly. Do not look for action rules inside `gate()`.
- Every refusal shows a reason and gives a safe dismiss/back action.

## Compose Rules

- Route-level composables collect ViewModel state; leaf components receive immutable state and
  callbacks.
- New UI composables that emit layout accept `modifier: Modifier = Modifier` as the first
  optional parameter.
- Use stable keys for `LazyColumn`, `LazyRow` and grid items.
- Keep navigation state small and saveable; do not store domain objects in `rememberSaveable`
  unless they are already safely parcelable/serializable and intentionally scoped to UI.
- Prefer one shared component with variants over local copies. A new local component is allowed
  only when it is truly screen-specific and cannot appear elsewhere.
- `collectAsStateWithLifecycle` is the target pattern for new lifecycle-aware collection once
  `androidx.lifecycle-runtime-compose` is added. A1 deliberately does not add that dependency.

## Colour and Motion Contract

| Area | Contract |
|---|---|
| Base | Obsidian `#0A0A0C`, edge to edge |
| Chrome | `#111118` system bar and rail |
| Cards / mini-player | `#181824`; raised surfaces `#1E1E2A` |
| Accent | Gold `#C9A84C` only for active, focus, CTA, progress and play control |
| Secondary text | `#ACACBC`; do not revert to `#A0A0B0` |
| Disabled text | `#555568`; disabled controls are nonfunctional |
| Parked motion | Ambient drift, album-art hue tint, screen cross-fade and rail-pill slide are allowed |
| Driving motion | Decorative motion freezes; progress/clock/play state continue |
| Reduced motion | Disable decorative motion when `Settings.Global.ANIMATOR_DURATION_SCALE == 0` |

## Shared Component Inventory

| Component | Reuse / build requirement | Required states |
|---|---|---|
| `CarSystemBar` / evolved `CarTopBar` | One 80dp shared top bar: wordmark, search, settings, avatar, clock/status | normal, driving-restricted affordance, focus |
| `CarNavRail` | One 80dp shared rail for Home, Browse, Library, Favourites | selected, unselected, focused, disabled only if a destination is unavailable |
| `CarMiniPlayer` | Re-theme existing component; artwork/title is one target; add queue button | playing, paused, buffering, error, no item |
| `CarPillButton` | Shared gold/ghost/destructive-safe button primitive | enabled, focused, disabled, loading |
| `CarIconButton` | Shared circular/square icon target wrapper | enabled, focused, selected, disabled |
| `CarChip` | Shared filter / browse-by / segmented chip | selected, unselected, focused, disabled |
| `CarContentCard` | Album, playlist, mix, genre, recommendation card | normal, focused, playing, unavailable |
| `CarTrackRow` | Track list row for songs, queue, albums, playlists, search | normal, focused, current, playing, disabled action |
| `CarPlaybackControls` | Full-player control cluster | play, pause, loading, shuffle on/off, repeat mode |
| `CarRestrictionDialog` | Shared driving restriction explanation | entry refused, evicted after drive transition |
| `CarEmptyState` | Shared empty/offline/no-results layout with optional CTA | no CTA, one CTA, two CTA |
| `CarLoadingSkeleton` | Shared placeholders | static, parked-only shimmer if ever added |
| `CarDownloadRow` | Downloads list row | downloaded, in-progress, failed, queued, delete-disabled |
| `CarParkedBadge` | Small parked-only indicator | visible on parked-only flows, absent elsewhere |

## Screen Contract

| # | Screen | UI and CTAs | Required states | Driving behavior | Phase |
|---|---|---|---|---|---|
| 1 | `CarAuthScreen` | Gold wordmark, parked badge, Google sign-in, phone sign-in, email sign-in, retry on failure | loading, error, signed-out | Refused by `NO_SETUP`; no keyboard while driving | A7 |
| 2 | `CarPinOptInScreen` | PIN dots, numeric keypad, Enable PIN, Not now, back | partial entry, validation error, loading | Refused by `NO_SETUP` | A7 |
| 3 | `CarHomeScreen` | Continue Listening cards, Your Mixes, Recommended, Play card/item | loading, empty, error, offline banner | Allowed; lists truncated by item cap | A2 |
| 4 | `CarBrowseScreen` | Filter chips, genre/mood/category cards, Play/open category | loading, empty, error | Allowed at root; deep drill-down refused by depth cap | A3 |
| 5 | `CarSearchScreen` | Search field while parked, system voice CTA, recent searches, browse-by chips, clear query | idle, recent-empty, no query | Typed entry refused; system/Assistant voice offered | A6 |
| 6 | `CarSearchResultsScreen` | Top Result play, song rows, album/artist result cards, clear/back | loading, no results, error | Results view allowed; deep drill-down refused | A6 |
| 7 | `CarLibraryScreen` | Category rows for Playlists, Albums, Artists, Favourites, Downloads; recently played | loading, empty, error | Allowed at root; drill-down refused past cap | A3 |
| 8 | `CarFavouriteMusicScreen` | Hero liked songs, Play all, Shuffle, track rows, unlike | empty routes to screen 17, loading, error | Allowed; list truncated; unlike is one-tap and row removal is deferred until refresh | A4 |
| 9 | `CarArtistLikedSongsScreen` | Artist hero, Play all, track rows, unlike | loading, empty, error | Refused when beyond depth cap | A4 |
| 10 | `CarPlaylistScreen` | Playlist hero, Play, Shuffle, track rows, save/offline if supported | loading, empty, error | Refused when beyond depth cap | A3 |
| 11 | `CarAlbumScreen` | Album hero, Play, Download, track rows | loading, empty, error, download progress | Refused when beyond depth cap; download mutation parked-only | A3 |
| 12 | `CarFullPlayerScreen` | Large artwork, title/artist, play/pause, prev/next, seek, like, shuffle, repeat, queue | buffering, paused, playing, error overlay | Allowed; playback control remains available | A5 |
| 13 | `CarQueueScreen` | Up Next, skip-to row tap, close, clear, remove/reorder controls while parked | empty, current track, playing indicator | View/skip-to allowed; remove/reorder/clear refused | A5 |
| 14 | `CarSettingsScreen` | Parked badge, account, audio quality, about, sign out | loading, error | Refused by `NO_SETUP`; standalone activity, if created, omits `distractionOptimized` | A7 |
| 15 | `CarDownloadsScreen` | Downloaded content rows, storage bar, remove one, remove all, retry failed | empty, in-progress, failed, offline | View allowed; delete/remove refused | A8 |
| 16 | `CarNoConnectionScreen` | Offline illustration/state, Retry, Browse Downloads | no network, retrying | Allowed; Browse Downloads remains available | A8 |
| 17 | `CarEmptyFavouritesScreen` | Empty heart state, Browse Music CTA | empty | Allowed; CTA routes to Browse root | A4 |
| 18 | `CarLoadingScreen` | Shared static skeletons and optional parked-only shimmer | initial load, section load | Allowed; no distracting animation | A8 |
| 19 | `CarPlaybackErrorOverlay` | Error message, Try again, Skip next, Dismiss | recoverable, fatal | Allowed and dismissible while driving | A8 |
| 20 | `CarProfileSwitcherScreen` | Current profile, switch profile, add profile, close | loading, empty/guest, error | Refused by `NO_SETUP` | A7 |

Screens 16-19 are states/overlays, not rail destinations. They still require full visual,
interaction and accessibility treatment.

## CTA Rules

- Primary CTA means gold fill with `#0A0A0C` label and 76dp height.
- Secondary CTA means transparent or glass fill with white label and 76dp height.
- Destructive-looking work, such as remove download or clear queue, is parked-only. It must not
  use alarm-red styling unless data loss is permanent.
- Disabled CTAs must not fire callbacks. A disabled-looking control that still acts fails the
  AAOS quality contract.
- Every parked-only CTA has one of two behaviors while driving: hidden when not needed, or
  visible disabled with an explanation. Silent no-ops are prohibited.

## Phase Acceptance Additions

Each phase spec must list:

- Screens delivered in that phase.
- Shared components created or evolved.
- CTAs and driving-mode behavior for each screen.
- Loading, empty, error and offline states.
- Motion used while parked and what freezes while driving.
- Automated checks or manual screenshot/emulator checks used before marking the phase complete.

If a screen cannot be completed because data is missing, the phase must either wire the existing
repository/ViewModel source or explicitly record the missing data as a new blocker. Do not ship
static placeholder data in production screens.
