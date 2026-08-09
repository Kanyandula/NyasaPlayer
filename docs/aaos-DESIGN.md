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
| `CarChip` | New shared 76dp chip for browse filters, search browse-by categories and segmented states |
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
| Queue edits | Queue remains viewable and skip-to remains available; reorder, remove and clear are refused |
| Downloads edits | Downloads remain viewable; delete/remove actions are refused |

Entering a restricted screen is refused with an explanatory panel rather than a silent
no-op. Starting to drive while already inside a restricted screen **evicts** you from it —
gating entry alone is not sufficient, since the vehicle can start moving at any time.

Tab switching, playback transport, seeking, queue view/skip-to, and download viewing stay
available while driving. Setup, typed search, deletion, removal, reorder and clear actions do
not.

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
