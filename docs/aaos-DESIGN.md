---
name: Nyasa Music AAOS
colors:
  background: '#0a0a0c'
  on-background: '#ffffff'
  surface: '#0a0a0c'
  surface-dim: '#0a0a0c'
  surface-bright: '#262634'
  surface-container-lowest: '#0a0a0c'
  surface-container-low: '#111118'
  surface-container: '#181824'
  surface-container-high: '#1e1e2a'
  surface-container-highest: '#262634'
  surface-variant: '#1e1e2a'
  on-surface: '#ffffff'
  on-surface-variant: '#acacbc'
  inverse-surface: '#ffffff'
  inverse-on-surface: '#0a0a0c'
  surface-tint: '#c9a84c'
  outline: '#555568'
  outline-variant: '#2a2a38'
  primary: '#c9a84c'
  on-primary: '#0a0a0c'
  primary-container: '#c9a84c'
  on-primary-container: '#0a0a0c'
  inverse-primary: '#7a6428'
  primary-fixed: '#e0c169'
  primary-fixed-dim: '#c9a84c'
  on-primary-fixed: '#0a0a0c'
  on-primary-fixed-variant: '#3d3110'
  secondary: '#1a3a5c'
  on-secondary: '#ffffff'
  secondary-container: '#1e1e2a'
  on-secondary-container: '#ffffff'
  secondary-fixed: '#1a3a5c'
  secondary-fixed-dim: '#142c46'
  on-secondary-fixed: '#ffffff'
  on-secondary-fixed-variant: '#acacbc'
  tertiary: '#643cb4'
  on-tertiary: '#ffffff'
  tertiary-container: '#2a1f45'
  on-tertiary-container: '#ffffff'
  tertiary-fixed: '#643cb4'
  tertiary-fixed-dim: '#4e2f8f'
  on-tertiary-fixed: '#ffffff'
  on-tertiary-fixed-variant: '#c4b3e6'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
typography:
  screen-title:
    fontFamily: Hanken Grotesk
    fontSize: 40px
    fontWeight: '700'
    lineHeight: 48px
    letterSpacing: 0em
  section-label:
    fontFamily: Hanken Grotesk
    fontSize: 22px
    fontWeight: '700'
    lineHeight: 28px
    letterSpacing: 0em
  card-title:
    fontFamily: Hanken Grotesk
    fontSize: 24px
    fontWeight: '700'
    lineHeight: 30px
    letterSpacing: 0em
  body-md:
    fontFamily: Hanken Grotesk
    fontSize: 18px
    fontWeight: '400'
    lineHeight: 26px
    letterSpacing: 0em
  secondary-sm:
    fontFamily: Hanken Grotesk
    fontSize: 15px
    fontWeight: '400'
    lineHeight: 22px
    letterSpacing: 0em
  label-button:
    fontFamily: Hanken Grotesk
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 24px
    letterSpacing: 0em
  caption-legal:
    fontFamily: Hanken Grotesk
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
    letterSpacing: 0em
rounded:
  sm: 8px
  DEFAULT: 14px
  md: 16px
  lg: 20px
  xl: 24px
  full: 9999px
spacing:
  margin-screen: 48px
  system-bar-height: 80px
  nav-rail-width: 80px
  mini-player-height: 88px   # prototype value. Implementation target is 112dp — see "Units"
  touch-target-min: 76px
  padding-card: 24px
  gutter-grid: 24px
  stack-gap-lg: 40px
  stack-gap-md: 24px
  stack-gap-sm: 12px
---

## Units — read before using any number here

**Every figure in this document is CSS px on a 1920 x 1080 canvas.** The prototypes
(`aaos-screens.html`, `aaos-app.html`) render in those units. The Android implementation is
in **dp**, and the two are not interchangeable.

Conversion rule, settled in
`docs/superpowers/specs/2026-08-02-aaos-foundation-restrictions-design.md` §2:

> Design px maps to dp 1:1, treating this canvas as a 1920 x 1080 dp logical space —
> **except** where existing code already holds a considered value, which wins.

Where that rule produces a different number, the implementation target is authoritative:

| Value | This document | Implementation target | Why |
|---|---|---|---|
| Touch target | 76px | **76.dp** | agree — `CarTouchTargetSize` was already 76.dp, arrived at independently |
| Mini-player height | 88px | **112.dp** | `CarMiniPlayerHeight` predates this design, exceeds its intent, and clears the touch target with room |
| Card corner radius | 20px | **20.dp** | design wins; the existing 16.dp had no recorded rationale |

The 88px mini-player figures below are therefore **correct for the prototype and wrong for
the implementation**. They are left as-is rather than overwritten so this document continues
to describe the HTML it documents.

Note the nav-rail *item* height is also 88 — that one is unrelated to the mini-player and
converts 1:1 to 88.dp.

**dp is density-independent, but physical size is not.** These numbers are correct for the
logical space; they are not a substitute for checking a real head unit.

## Brand & Style

Nyasa Music for Android Automotive OS. A premium in-car entertainment surface on a
fixed 1920 x 1080 landscape display, dark mode only. The feeling is quiet luxury and
technical precision: deep obsidian black for OLED contrast at night, champagne gold
reserved strictly for what is active or actionable, and nothing decorative competing
for the driver's attention.

The product name is **Nyasa Music**. It is the only brand name that may appear in any
screen. Never render any other product, brand, or placeholder name.

## Colors

- **Background:** Obsidian `#0A0A0C` on every screen, edge to edge.
- **Chrome surfaces:** Charcoal `#111118` for the system bar and navigation rail.
- **Cards and mini-player:** Glass `#181824`, with `#1E1E2A` for raised cards and inputs.
- **Accent:** Champagne gold `#C9A84C`, used only for the active nav item, the focused
  border, primary CTAs, the play button, and the progress fill. Never for body text.
- **Text:** `#FFFFFF` primary, `#ACACBC` secondary and metadata, `#555568` disabled.
- **Borders:** 1px `rgba(255,255,255,0.08)` at rest; `rgba(201,168,76,0.5)` when focused
  or active.
- **Ambient:** A low-intensity blue `#1A3A5C` and purple `rgba(100,60,180,0.3)` may tint
  large background gradients only. They never appear as fills on interactive elements.

## Typography

Hanken Grotesk throughout. No text may render below 14px, and no interactive label below
18px, so it stays legible at arm's length in a moving vehicle. Letter spacing is `0` in
the Android implementation; do not carry over generated negative tracking from the static
mockups.

The smallest text actually rendered is 15px — artist names in track rows. That satisfies the
rule above, but 15px is small for a glance from the driver's seat, and car UI body styles are
typically far larger. Treat 15px as the floor to revisit, not as a target.

### Contrast, measured

| Pair | Ratio | |
|---|---|---|
| White on card `#181824` | 17.6:1 | AAA |
| Gold `#C9A84C` on base | 8.7:1 | AAA |
| Dark label on gold CTA | 8.7:1 | AAA |
| Secondary `#ACACBC` on base `#0A0A0C` | 8.8:1 | AAA |
| Secondary `#ACACBC` on cards `#181824` | 7.9:1 | AAA |
| Secondary `#ACACBC` on chrome `#111118` | 8.4:1 | AAA |
| Secondary `#ACACBC` on raised `#1E1E2A` | 7.4:1 | AAA |
| Disabled `#555568` on base | 2.7:1 | exempt — disabled text |

Every non-disabled pair clears AAA on every surface it lands on.

The secondary token was `#A0A0B0` and gave only 6.8:1 on cards — AA, not AAA — which made the
blanket "AAA" claim false. It is now `#ACACBC`. The binding surface is raised `#1E1E2A` at
7.4:1, so do not darken this token without re-measuring against that one, not against the base.

## Layout

Fixed 1920 x 1080 landscape. Every screen composes the same three regions in the same
place so the driver's muscle memory holds across the app:

1. **Top system bar** — full width, 80px tall, pinned to the top.
2. **Left navigation rail** — 80px wide, spanning from below the system bar down to the
   top of the mini-player.
3. **Persistent mini-player** — full width, 88px tall, pinned to the bottom.
   (Implementation target: **112.dp**, not 88 — see "Units".)

Content occupies the region bounded by those three, with a 48px screen margin.

## Chrome (identical on every screen)

These three regions are a fixed contract. Render them pixel-identical on every screen.
Do not restyle, reorder, resize, or omit them from screen to screen.

### Top system bar

```
Height: 80px, full bleed, background #111118
Left:   "Nyasa Music" wordmark, gold #C9A84C, 20px weight 700, single line, 24px from left edge
Right, in this exact order, 24px from right edge:
        search icon, settings icon, circular avatar (32px), clock "10:41 PM",
        wi-fi icon, bluetooth icon, battery icon
Icon size: 24px, color #FFFFFF at 80% opacity
The three tappable items (search, settings, avatar) each sit in a 76x76 hit area,
which is why the bar is 80 tall rather than 48. Spacing comes from those hit areas.
```

The wordmark never wraps to two lines and never changes size between screens. The right
cluster is always exactly those seven items in that order — no more, no fewer, no
substitutions.

### Left navigation rail

```
Width: 80px, background #111118
Items, top to bottom: Home, Browse, Library, Favourites
Item height: 88px, icon 28px above a 13px label
Rest state:   icon and label #ACACBC
Active state: icon and label #C9A84C inside a rounded-full pill of
              rgba(201,168,76,0.12) with a soft 32px gold glow
```

Exactly one item is active per screen, indicated by color and pill only.

### Persistent mini-player

```
Height: 88px in the prototype; 112.dp in the implementation (see "Units")
Background #181824, 1px top border rgba(255,255,255,0.08)
Left:   64px album art at 8px radius, then title 18px #FFFFFF over artist 15px #ACACBC.
        Art and title are ONE target (314x76) that opens the full player, not two.
Center: progress bar, 4px tall, gold #C9A84C fill on #2A2A38 track, inside a 76px-tall
        seek target
Right:  heart, previous, play/pause in a 76px gold circle, next, queue — each in a 76x76 area
```

**Implementation deviations** (spec §7):

- **The progress bar is not a seek target (D6).** A seek gesture cannot coexist with the
  row-wide tap-to-expand: the whole mini-player row is one clickable, and narrowing it to fit
  a precision control trades the largest, most forgiving target in the app for a gesture that
  is hard to land in a moving vehicle. Seeking lives in the full player, one tap away and
  permitted while driving. The bar renders as a non-interactive indicator, vertically centred
  in the 76dp region so the layout still matches.
- **Wi-fi, bluetooth and battery are not rendered (D7).** `core.common.ui.icons` has no
  vectors for them, and there is no wiring to real system state, so shipping them would mean
  static icons claiming a full battery and a connected radio — a lie the driver may act on.
  On AAOS the OEM system bar generally owns these. The bar ships as wordmark · search ·
  settings · avatar · clock.
- **D11 — No Browse filter chips.** Screen 4 lists them. `Genre` is `id`, `name`, `color`,
  `imageUrl`, `popularity`, `songIds` — nothing backs "mood" or "category", so any chip set would
  be invented taxonomy. **Data blocker:** a genre taxonomy field in Firestore. The grid is
  unchanged by chips arriving later.
- **D12 — No Download button on `CarAlbumScreen`, and no download-progress state.** Screen 11
  lists both. **Module blocker: downloads are unreachable from `:automotive` until
  `SongDownloadManager` leaves `:app`.** `DownloadRepository` (`:core:data`) is reachable but
  records download state in Room only — `addDownload`, `updateProgress`, `markCompleted`. The code
  that fetches and writes the file is `SongDownloadManager`, `@Singleton` in `:app`, and
  `:automotive` does not and should not depend on `:app`. Wiring the repository alone ships a
  button that permanently claims a download is in progress. Extracting the manager into a shared
  module touches seven `:app` files — `NyasaPlayerNavigation`, `PlayerViewModel`,
  `SongOverflowWithDownload`, `DownloadsViewModel`, `LibraryScreen`, `PlaylistDetailScreen`,
  `SearchScreen` — and belongs to A8, which owns downloads and needs it anyway.
- **D14 — Sign-out stays on `CarLibraryScreen`** with its confirmation overlay, marked for
  deletion in A7. It belongs on screen 14, but removing it in A3 leaves no way to sign out of the
  vehicle at all, since the system bar's avatar is disabled until A7 (A2 D3).
- **D18 — Library's playlist cards render the gold placeholder, not the first resolved track's
  artwork.** The spec defines playlist artwork as the first resolved track's `resolvedCoverUrl`
  (same derivation `deriveFavoriteArtists()` uses for artist avatars), and `CarPlaylistScreen`
  already shows it once a playlist is opened — so the same playlist reads as gold-placeholder on
  Library and real artwork one tap later, visible inconsistency within one journey. Accepted
  rather than closed: unlike artists, whose avatars derive from `likedSongs` already held in
  memory, a playlist's `songIds` are not part of any loaded state, so deriving its artwork means
  a `getSongsByIds` repository call per playlist on every `observePlaylists()` emission, purely to
  decorate a card. **Cost to close:** cache the first resolved track per playlist (e.g. alongside
  `Playlist` in `AutomotiveContentState`, refreshed only when a playlist's `songIds` change) so
  Library reads it without a query on every emission.
- **D19 — The list is frozen at the first unlike, not on tab entry.** `favourites: List<Song>?`
  is null until then, and the screen renders live `likedSongs` while it is. The contract requires
  row removal to be deferred until refresh, and `likedSongs` is a live Firestore listener that
  drops an unliked song immediately — so honouring it means not rendering from the live flow *once
  something has been unliked*. Freezing on entry instead would snapshot an empty list whenever
  the driver opens Favourites before Firestore's first emission, and D20's guard would then prevent
  it ever being retaken: a permanent "no favourites yet" with the songs one tab away. That is A3's
  D17 restore-gap failure in a new place. Freezing at the first unlike is both simpler and
  correct: before an unlike there is nothing to hold back.
- **D20 — `openFavourites()` never clears an existing freeze; only `closeFavourites()` does.**
  `AutomotiveActivity` declares no `configChanges`, so a night-mode flip recreates it and re-runs
  the driving effect. If re-entry reset `favourites` and `pendingUnlikes`, held-back rows would
  reconcile silently mid-drive — the exact behaviour D19 exists to prevent. The identical defect
  was found in A3's `openDetail` by PR #18's whole-branch review; reintroducing it one slice later
  would be indefensible.
- **D21 — Screen 17 is a composable rendered in place at the Favourites tab root, not a
  navigation destination.** Favourites is a tab root at drill depth 0. A destination would sit at
  depth 1, where `maxContentDepth` can refuse it — meaning a driver with nothing liked would be
  blocked from a screen whose only content is "you have nothing yet". Rendering in place keeps it
  reachable under every restriction state.
- **D22 — The unlike affordance goes inside `CarTrackRow`, behind an optional `onLikeToggle`,
  rather than into a new row variant or each call site.** The 76dp touch target is then enforced
  once, in the component, rather than depended upon at three call sites. Gating on a nullable
  callback leaves `CarHomeScreen` and `CarDetailScreen` untouched by their defaults. A separate
  `CarLikedTrackRow` would duplicate a row the screen contract explicitly names as shared.
- **D23 — A4 adds `@file:Suppress("TooManyFunctions")` to `AutomotiveContentViewModel.kt` and
  records the class's growth as debt for a later slice, rather than splitting it now.** The
  file is at 19 functions against detekt's `thresholdInFiles: 20`, and the class-level suppression
  added in A3 does not cover the file threshold, so A4's three methods break the build without
  action. Splitting a ViewModel that four screens depend on is a refactor, not a screen slice; doing
  it inside A4 would make three screens the smaller half of the work. **This is the third
  `TooManyFunctions` suppression currently live across `:automotive` and `:app` view models, and
  the second time this file has needed one — the class now owns search, genres, albums, playlists,
  recently-played, liked songs, popular, detail and favourites, and the next slice to touch it
  should split it rather than suppress again.**
- **D24 — `CarArtistLikedSongsScreen` keeps its Shuffle control, which the contract does not
  list for screen 9.** The control exists and works today. Removing it to match the contract would
  be a visible capability regression for no user benefit. Recorded as an additive deviation rather
  than silently kept.
- **D25 — Unlike on screen 9 removes the row immediately; on screen 8 it does not.** Screen 9's
  list is a live filter over `likedSongs`, which A3's D16 established deliberately: `rememberSaveable`
  restores the artist destination while `likedSongs` is still empty, and any snapshot or resolve step
  taken in that gap drops the user back to the tab root on every process-death restore. Preserving
  D16 is worth more than making the two screens' unlike behaviour identical. The inconsistency is
  real and is recorded here rather than discovered later.
- **D32 — Favourites freezes on entry once liked songs have loaded, refining D19's first-unlike
  freeze.** D19 chose the first unlike because an entry freeze taken before Firestore's first
  emission would snapshot an empty list and strand the driver on "no favourites yet". That
  objection was about *timing*, and `likedSongsLoaded` — added later, for the skeleton — now
  answers it: `openFavourites()` freezes only when the list has actually arrived, and takes no
  freeze at all before that, so the cold-start cache-then-server sequence still lands. The
  first-unlike freeze alone left two holes, because unlike is the common cause of a row moving,
  not the only one: a like or an unlike from another device re-emits the whole ordered list
  (`likedAt DESC`) and shifts or drops rows under the driver mid-visit. D20 still holds — an
  entry that finds a freeze already held disturbs neither it nor the pends behind it.
- **D33 — `openFavourites()` clears pending unlikes when it is not holding a freeze, and a null
  user id is not a user switch.** The pend set has two writers across two screens: leaving the
  artist drill-down does not leave the Library tab, so the tab-keyed effect never fires and a
  pend taken there paints a still-liked song with a hollow heart on the next Favourites visit.
  Clearing on entry is safe only when no freeze is being held, which is why the guard and not
  D20's blanket rule. Separately, `reloadUserContent()` now ignores a null `currentUserId`:
  on Activity recreation auth has not restored yet, and treating that as a switch to nobody
  reflowed the list under a driver who never left the screen. A real sign-out tears the session
  down through `AutomotiveAuthViewModel`.
- **D34 — Favourites reads its own loading and error state, not the catalogue's.** A failed
  liked-songs load left `errorMessage` untouched — its only writers are the genres and albums
  collectors — so screen 8 fell through to `CarEmptyFavouritesScreen` and told a driver with a
  full library "No favourites yet", with no error and no Retry. `AutomotiveContentState` now
  carries `favouritesError` beside the shared field and derives `favouritesLoading` from
  `likedSongsLoaded`, and `AutomotiveApp` binds those. The two stay separate deliberately: a
  catalogue failure is the process's and a liked-songs failure is the account's, so a user
  switch clears one and not the other. `loadContent()` also resets `likedSongsLoaded`, or Retry
  leaves Favourites on the false empty state for the whole reload, and it only tears down the
  user-scoped collectors when there is a user to restart them for.
- **D26 — Queue reorder is deferred, and every doc that promised it now says remove or clear.**
  Reorder has no `PlaybackQueueManager` API, no ViewModel method, and no mobile contract behind it;
  it is also parked-only convenience, while buffering, error surfacing and driving truncation are
  correctness and compliance work. Shipping copy that offers it — `CarQueueScreen`'s driving chip
  read "Park the car to reorder or clear your queue" — promises a control the screen does not have.
  The chip now reads "Park the car to remove or clear your queue," and the contract, PRD and
  restriction tables here name remove and clear only. **Cost to close:** a move API on
  `PlaybackQueueManager`, a ViewModel method, and a drag affordance that clears 76dp; a queue-core
  feature, not the completion of an overlay.
- **D27 — Screen 12's error state is the existing global `CarErrorOverlay`, not a new screen-19
  destination.** `AutomotivePlayerViewModel` already routes playback failures into `PlayerError`,
  and `AutomotiveApp` already composes the overlay after both player overlays, so it draws above
  them. Adding a destination would put a failure message behind `gate()` and `maxContentDepth`,
  which can refuse it — the same reasoning as D21. A8 owns the dedicated playback-error visual.
- **D28 — Driving truncation is a display window over the queue, never a mutation of it.**
  `maxCumulativeContentItems` restricts what the driver may *see*; changing Media3's queue or the
  persisted playback order to achieve that would corrupt state the driver never asked to change,
  and would outlive the drive. `queueDisplayItems()` therefore returns rows paired with their
  original queue index, and `CarQueueScreen` passes that index — not the row's position — to
  `onSkipTo`/`onRemove`. The window also keeps the current track visible when it sits past the
  first capped page, which a plain `take(cap)` would hide. This is the one piece of A5 with real
  index arithmetic, so it landed as a pure function with unit tests before any Compose consumed it.
- **D29 — A5 did not split `AutomotivePlayerViewModel`, because it added no player API.** The
  slice consumed state that already existed: `isBuffering` on `PlaybackSnapshot`, `PlayerError`
  for failures, and the existing skip/remove/clear methods. D23's warning stands for the next
  slice that does need a new public method there — split rather than suppress again.
- **D30 — Queue truncation keys off the same distraction-optimization state as every other
  restriction.** `CarQueueScreen` receives `restrictions.isDistractionOptimized` and
  `restrictions.maxCumulativeContentItems` from the one `CarUxRestrictionsHandler` flow that
  already gates edit actions, so the list and the buttons can never disagree about whether the
  vehicle is moving. A4 verified that injected moving state reaches the app as `DO: true UxR: 255`,
  so the same signal is known to arrive.
- **D31 — Q2 is resolved by using system IME when `NO_KEYBOARD` is absent and a voice-search
  prompt when it is present.** The custom launcher reacts to the platform restriction instead of
  inferring driving state or drawing a custom keyboard. It also keeps the existing voice-search
  boundary intact: system/Assistant voice search reaches `PlaybackService.onSearch` /
  `onGetSearchResult`; the app records no audio and requests no `RECORD_AUDIO`.
- **D32 — A6 searches on explicit submit, not on every keypress.** A head-unit search field should
  not create repository work while the driver is still editing. Screen 6 exists only after a
  committed query, so the ViewModel stores draft `query` separately from `submittedQuery`.
- **D33 — A6 ships song-only results; album, artist and playlist result cards are deferred to
  T4.** `SongRepository.searchSongs()` and `MediaBrowseTree.search()` are song-search contracts
  today. The original album/artist card wording needs a typed result model, repository search APIs
  and a valid artist destination before the UI can be honest.
- **D34 — Recent searches are session-only in the automotive search ViewModel.** The PRD asks for
  recent searches but not durable history. Persisting query history adds privacy/storage questions
  that are not needed to make screen 5 useful; losing recents on process death is acceptable.
- **D35 — Search leaves `AutomotiveContentViewModel` instead of adding another suppression.** A4's
  D23 recorded that the class already owns too many unrelated domains. A6 depends only on
  `SongRepository.searchSongs()`, so a small `AutomotiveSearchViewModel` is the cleaner boundary.
- **D36 — Search results are capped only while distraction optimization is required.** Every other
  screen calls `.take(maxCumulativeContentItems)` unconditionally. The platform reports that value
  when parked too, where it is the unrestricted baseline rather than a restriction, so applying it
  unconditionally would silently truncate a parked search. `visibleSearchResults()` is the tested
  boundary, and it produces both what the driver sees and the list a tap plays — a divergence
  between those two is how a "play this" starts a track that was never on screen.
- **D37 — A committed search drops the previous query's results.** The usual rule is to keep
  previous content during a refresh rather than blanking the screen, but here the header names the
  new query, so the previous query's songs underneath it would be a wrong answer rather than a
  stale one. It also keeps the failure state honest: an error never renders rows.
- **D38 — Submitting closes the editor, and the sheet's view derives from `submittedQuery`.**
  Results are permitted while driving and an active field is not, so an editing flag surviving a
  submit would have the gate evict a driver from a list they are allowed to read. Back is
  `backToSearch()` dropping the submitted query rather than a separate "showing results" boolean,
  which cannot then disagree with the ViewModel about which view is open.
- **D39 — Browse-by shortcuts reuse `CarPillButton`'s ghost variant instead of adding `CarChip`.**
  The component table below calls for a chip; the ghost pill is already that shape at the same
  76dp target, and A6 is its only consumer. Recorded as a deviation rather than silently kept.
  `Songs` is the one shortcut whose only action is focusing the field, so it ships only alongside
  the field — under `NO_KEYBOARD` it would be the silent no-op FR-2.6 prohibits. The submit CTA
  is gated the same way: a query typed while parked survives in state, so without the gate a
  moving vehicle would show a Search button offering to run terms the driver can no longer read.
- **D40 — Opening search does not auto-focus the field.** The A6 spec's manual checklist expects
  the keyboard on open. Auto-focusing raises the IME over `Recent searches` and `Browse by`, the
  two fastest paths out of the sheet, on every press of a control labelled Search — and it would
  make the `Songs` shortcut, whose entire job is focusing the field, meaningless. The field
  focuses on tap or through `Songs`. Recorded as a deliberate deviation from the checklist
  wording rather than silently kept.

## Components

### Implementation ownership

Screens must compose from shared car components rather than each screen inventing its own
chrome, buttons, cards, rows or empty states. This is both a maintenance rule and a safety
rule: a one-off button is how a 76dp target or contrast rule regresses.

| Component | Build/reuse rule |
|---|---|
| `CarSystemBar` / current `CarTopBar` | Evolve the existing top bar into the shared 80dp system bar; do not create per-screen bars |
| `CarNavRail` | New shared rail; every destination screen uses the same instance and active-state logic |
| `CarMiniPlayer` | Reuse and re-theme the existing component; add the queue button and combined artwork/title target |
| `CarPillButton` / primary CTA | New shared 76dp button primitive for gold and ghost actions |
| `CarChip` | Not built. A6's browse-by shortcuts use `CarPillButton`'s ghost variant (D39); build the chip only when a second consumer needs selected/segmented states |
| `CarContentCard` | New shared card primitive for album, playlist, mix, genre and recommendation cards |
| `CarTrackRow` | New shared track row for favourites, album, playlist, queue and search result lists |
| `CarPlaybackControls` | Shared full-player transport controls; mini-player may use a compact wrapper around the same semantics |
| `CarRestrictionDialog` | Shared refusal/eviction explanation for every driving restriction |
| `CarEmptyState` | Shared empty-state layout with optional CTA |
| `CarLoadingSkeleton` | Shared loading placeholder; static while driving and when system animations are disabled |
| `CarDownloadRow` | Shared downloads row with parked-only remove actions |

- **Primary CTA:** gold `#C9A84C` fill, `#0A0A0C` text, 20px weight 600, 14px radius, 76px tall.
- **Secondary button:** transparent fill, 1px `rgba(255,255,255,0.12)` border, white text, 76px tall.
- **Content card:** 20px radius, `#181824` fill, 1px `rgba(255,255,255,0.06)` border. On focus,
  the border becomes gold. Any lift or scale transition is parked-only.
- **Filter chip:** fully rounded, 76px tall, 28px horizontal padding. Selected chips use a gold
  fill with dark bold text; unselected use `#1E1E2A` with a hairline white border.
- **Text input:** 76px tall, 16px radius, `#1E1E2A` fill. Focused inputs take a 2px gold border
  and a soft gold outer glow.
- **Track list row:** 80px tall, 52px art at 8px radius, title 18px white, artist 15px `#ACACBC`,
  duration right-aligned in `#ACACBC`. The currently playing row carries a 3px gold bar on its
  left edge.
- **Parked-mode badge:** a gold-outlined pill with a small car icon and the text
  "Parked mode only", used only on screens that are unavailable while driving.

### Touch targets

The minimum is **76 x 76** on the smallest side. Every interactive control in
`docs/aaos-app.html` meets it — measured across all nine views, the smallest target is
exactly 76.

The mechanism is a `.hit` class carrying `min-width:76px; min-height:76px` around the glyph,
so icons keep their visual size while the padding supplies the target. Do not shrink a
control by deleting its wrapper.

Three changes were structural rather than padding:

- **The system bar is 80 tall, not 48.** A 48px bar cannot contain a 76px target, and this
  bar holds app controls (search, settings, avatar) rather than only OS status icons. If the
  OEM draws the status row itself, the app's own bar still needs this height.
- **Filter chips are 76 tall** (were 56) and **pill buttons 76** (were 64).
- **The mini-player's artwork and title are a single target**, not two. They were 64 x 64 and
  230 x 43 separately; merged they are 314 x 76, and tapping anywhere on the "now playing"
  block opens the player.

Re-run the measurement after any layout change — several of these were introduced by
otherwise harmless styling.

`docs/aaos-screens.html` carries the same sizing. All 20 screens were re-checked after the
change: the 80px bar takes 32px out of every content region, offset by reducing content
padding from 40 to 32. Only the three screens that already clipped a trailing row still
overflow (artist, album, queue), which is the intended scroll affordance rather than a
layout fault.

All figures are CSS px on a 1920 x 1080 canvas. Convert to dp against the target head unit's
density before judging compliance; at ~1.0 density they map 1:1, above that they shrink.

## Motion

Motion is gated on the vehicle's UX-restriction state, not on taste.

- **Parked:** the ambient background gradients may drift slowly, and their hue may follow the
  current track's artwork. Screen changes cross-fade, and the navigation rail's active pill
  slides between items.
- **Driving:** all decorative motion stops. The ambient gradients freeze in place. Only motion
  that carries information continues — the progress bar, the clock, and the play/pause state.

Nothing auto-scrolls, pulses, or parallaxes in either state. If the platform animator duration
scale is `0` (`Settings.Global.ANIMATOR_DURATION_SCALE`), the decorative layer is disabled
entirely even while parked.

The original design doc recorded "no decorative motion" as a flat rule. Gating on parked
vs driving is the same safety position, stated more precisely: it is motion *while the vehicle
is moving* that is restricted.

## Driving restrictions

`docs/aaos-app.html` enforces these while the mode switch reads Driving. They mirror
`CarUxRestrictions` defaults; the shipping app must take the real values from
`CarUxRestrictionsHandler` rather than hardcoding them.

| Restriction | Behaviour while driving |
|---|---|
| `UX_RESTRICTIONS_NO_SETUP` | Settings and the profile switcher refuse to open |
| `UX_RESTRICTIONS_NO_KEYBOARD` | The search text field is replaced by a system/Assistant voice-search prompt; the app records no audio itself |
| `getMaxContentDepth()` = 1 | Drill-down into a playlist or album is refused |
| `getMaxCumulativeContentItems()` = 21 | Content lists truncate to 21 items |
| Decorative motion | Ambient gradients freeze |
| Queue edits | Queue remains viewable and skip-to remains available; remove and clear are refused, and the list truncates to the reported cap |
| Downloads edits | Downloads remain viewable; delete/remove actions are refused |

Entering a restricted screen is refused with an explanatory panel rather than a silent
no-op. Starting to drive while already inside a restricted screen **evicts** you from it —
gating entry alone is not sufficient, since the vehicle can start moving at any time.

Tab switching, playback transport, seeking, queue view/skip-to, and download viewing stay
available while driving. Setup, typed search, deletion, removal and clear actions do not.
Queue reorder ships in no state (D26).

Not yet enforced: no cumulative item count is carried *across* a browse session, and
`getMaxRestrictedStringLength()` (120 chars) is unchecked. Neither currently binds, because
no screen exceeds 21 items and no string approaches 120 characters — but both would need
real enforcement before shipping.

## Rendering rules

The output is a finished product screen, as a user would see it. It is not a
specification drawing.

- Never draw measurement annotations, dimension lines, arrows, rulers, callouts,
  or pixel labels such as "48px", "200px", or "380px" anywhere in the design.
- Never print a state name as visible text. An active tab or selected chip is shown by
  gold color and pill treatment alone — never by rendering the word "ACTIVE",
  "SELECTED", "FOCUSED", or "DEFAULT" next to it.
- Never let a font size, token name, or hex value leak into user-facing copy. Song and
  artist labels contain only the song and artist name.
- Never label the same thing twice. One heading per section, never an uppercase eyebrow
  and a title-case heading saying the same words, and never a helper sentence repeated
  in two columns.
- Never leave an empty placeholder tile in a grid or chip group. Show only real items.
- Use realistic content: plausible song, artist, album, and playlist names, and cover art
  as abstract color gradients rather than photographs of people.
