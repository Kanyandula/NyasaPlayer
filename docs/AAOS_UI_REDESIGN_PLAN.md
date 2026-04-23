# NyasaPlayer AAOS — UI Redesign Plan

> Source of truth for the Stitch-first design pass before any Compose code is touched.
> Pair this with `docs/AAOS_ARCHITECTURE.md` (architecture) and the gap-analysis HTML
> (`~/Downloads/nyasaplayer_aaos_gap_analysis.html`).

---

## 1. Goal

Design every screen of the AAOS app in Stitch first, lock the visual language and
interaction patterns, then port to Jetpack Compose against the existing
`:automotive` module. No Compose changes until the Stitch set is approved.

## 2. Physical + platform constraints

| Constraint | Value / rule |
| --- | --- |
| Orientation | Landscape only. Head units are fixed landscape. |
| Target resolution | Design to 1280×720 (16:9). Must scale cleanly to 1920×1080 and 1080×600. |
| Minimum touch target | **76 dp** (`CarTouchTargetSize`) — CTS requirement. Applies to every tappable element. |
| Minimum text size | 16 sp for metadata, 18 sp+ for primary list items, 20 sp+ for headings. |
| Contrast | WCAG AA against `#0D0D0D` background; avoid thin strokes and low-opacity text. |
| UX restrictions | `CarUxRestrictionsHandler` caps list length via `limitedContentItems` and blocks text entry (`noTextEntry`) and filtering (`noFiltering`) while driving. Every scrollable/searchable state needs a driving-mode variant. |
| Input model | Touch + rotary + steering-wheel media keys + **voice (primary in motion)**. No keyboard assumed. |
| Reachability | Driver reach across the full width is uneven. Keep primary actions centred; keep destructive/rare actions off-driver-side. |

## 3. Visual language (Nyasa Dark)

Carry the existing `:core:common` theme unchanged so Compose port is a 1:1 swap.

| Token | Hex | Usage |
| --- | --- | --- |
| `NyasaBackground` | `#0D0D0D` | App background |
| `NyasaSurface2` | ~`#1A1A1A` | Cards, mini-player, rows |
| `NyasaPrimary` | `#A855F7` | Primary accent, active tab, like heart |
| `NyasaPrimaryDark` | `#7C3AED` | Gradient partner with `NyasaPrimary` |
| `NyasaTextSecondary` | `#A1A1AA` | Metadata, inactive labels |
| `NyasaTextTertiary` | ~`#71717A` | Kicker/caption text |
| Error | `#EF5350` | Destructive actions, error banners |

Gradient convention: `Brush.horizontalGradient(listOf(NyasaPrimary, NyasaPrimaryDark))`
on the play button, logo chip, active tab, and any primary CTA.

Typography (Compose units, used as Stitch design-system tokens):

- Display (track title on full player): 36 sp bold
- Headline (section header, screen title): 20–30 sp semibold / bold
- Body-lg (list item title): 18 sp medium
- Body-md (artist, metadata): 16 sp regular
- Label (kicker): 12 sp medium, letter-spacing +0.08em, uppercase
- Font: **Inter** across the board (available in Stitch font list).

Shape: 16 dp corner radius for cards and rows; 24 dp for hero surfaces (full-player art,
modals); circle for avatars and icon buttons. Stitch: `ROUND_TWELVE` (maps to 12–full)
as the default roundness.

## 4. Stitch project

- **Project name**: `NyasaPlayer AAOS`
- **Device type**: `TABLET` (closest approximation of landscape head-unit aspect).
- **Design system name**: `Nyasa Dark`.
- **Project ID**: `6899228466021446121`
- **Design system asset ID**: `13435337818663503252`

Every screen prompt in §6 must include the phrase **"landscape car head-unit, 1280×720,
76 dp minimum touch targets"** so the generator respects car ergonomics.

## 5. Screen inventory

### 5.1 Phase 0 — existing screens (redesign pass) ✅ generated

Covers the screens that already ship. Goal: anchor the visual language and catch any
UX gaps before adding new screens.

| # | Stitch screen name | Screen ID | Maps to Compose file |
| --- | --- | --- | --- |
| 0.1 | `Auth / Sign-in` | `37341c693f114341b4ef9f3c88e1db6a` | `CarAuthScreen.kt` |
| 0.2 | `Home` | `244bf29072e9472cacad387810f9a1fd` | `CarHomeScreen.kt` |
| 0.3 | `Browse — default` | `ddf6864566e9496a8ac64082d489870e` | `CarBrowseScreen.kt` |
| 0.4 | `Browse — searching` | `0952d735f6d24f1493d8c97d741b2090` | `CarBrowseScreen.kt` (search-active state) |
| 0.5 | `Browse — driving mode` | `99a01acbf10d4dccad1660cee0965b63` | `CarBrowseScreen.kt` (search disabled) |
| 0.6 | `Library` | `9d6b6cfe0222463bbbfd126a9a9bcf3b` | `CarLibraryScreen.kt` |
| 0.7 | `Library — sign-out modal` | `286557bc0f9d4e168e06a39170409600` | `CarLibraryScreen.kt` overlay |
| 0.8 | `Artist liked songs` | `4bfaa6e8b5fa48a4a2bc61c363aed528` | `CarArtistLikedSongsScreen.kt` |
| 0.9 | `Full player` | `d55966d448dd4a059f1e47269d2ca4d0` | `CarFullPlayerScreen.kt` |
| 0.10 | `Full player — error overlay` | `da4f0f05339e43e69e3fc1c15d5f707e` | `CarErrorOverlay.kt` composed over `CarFullPlayerScreen` |
| 0.11 | `Mini player — with playback` | `e3e987aaed09497ca33dda9ac3e7b85c` | `CarMiniPlayer.kt` (shown as overlay state atop Home) |

### 5.2 Phase 1 — core playback completeness (critical)

Note: `list_screens` truncates at ~12 results; always verify individual screens with
`get_screen` by ID, not by searching the list.

| # | Stitch screen name | Screen ID | New Compose file |
| --- | --- | --- | --- |
| 1.1 | `Queue — parked (reorderable)` | `5c6cee7f25f141f5bf9167fd182f0dda` | `CarQueueScreen.kt` |
| 1.2 | `Queue — driving (read-only)` | `2f60f38712c64a58ad02de02fa3544d9` | `CarQueueScreen.kt` (restricted state) |
| 1.3 | `Browse — voice prompt` | `a367c0594ff44a8d97127842d457da6c` | `CarBrowseScreen.kt` state |
| 1.4 | `Browse — voice results` | `b2d5268b46234041a7116c1b76b5bed9` (primary; `364b834f036b4e3ab15b15211b5ad4f0` + `7affaf8166df4c869daef7428e49d002` are duplicate attempts — review all three, delete the weaker two in Stitch) | `CarBrowseScreen.kt` results-from-voice state |

**Lesson learned**: `generate_screen_from_text` timeouts are lies — they just mean
the response got dropped. The generation succeeds on the server. Check `list_screens`
after a timeout before retrying, otherwise you'll get duplicates.

### 5.3 Phase 2 — content depth (high) ⚠ 3/4

2.3 failed multiple retries with server-side timeout. Paused — to be regenerated in a
later session when the Stitch backend is stable.

| # | Stitch screen name | Screen ID | New Compose file |
| --- | --- | --- | --- |
| 2.1 | `Playlist detail` | `b054a08a66c4479b98a8a51becbbad4a` | `CarPlaylistDetailScreen.kt` |
| 2.2 | `Album detail` | `de379e1b37d340ef807f048b00e04b72` | `CarAlbumDetailScreen.kt` |
| 2.3 | `Artist — full` | ❌ pending — regenerate later | `CarArtistScreen.kt` |
| 2.4 | `Full player — with source chip` | `53e65729aa2747078528e3b1150f22f9` | Variant of `CarFullPlayerScreen.kt` |

### 5.4 Phase 3 — driving usability (high)

| # | Stitch screen name | New / changed file |
| --- | --- | --- |
| 3.1 | `Home — continue listening` | `CarHomeScreen.kt` adds a continue row |
| 3.2 | `Library — downloads section` | `CarLibraryScreen.kt` adds downloads entry |
| 3.3 | `Downloads` | `CarDownloadsScreen.kt` |
| 3.4 | `Mini player — with up-next peek` | Variant of `CarMiniPlayer.kt` with next-track peek + queue shortcut |

### 5.5 Phase 4 — platform compliance (medium)

| # | Stitch screen name | New / changed file |
| --- | --- | --- |
| 4.1 | `Settings` | `CarSettingsScreen.kt` |
| 4.2 | `Settings — audio quality picker` | `CarSettingsScreen.kt` detail |

(`STATE_ERROR` wiring and the system home-screen media widget are backend gaps,
not design ones — no Stitch screen, tracked separately for implementation.)

### 5.6 Phase 5 — polish (low)

| # | Stitch screen name | New / changed file |
| --- | --- | --- |
| 5.1 | `Full player — wavy M3 progress` | Variant of `CarFullPlayerScreen.kt` |

Podcast speed / sleep timer and DPI audit deferred until after Compose port.

## 6. Per-screen generation prompts

Each prompt is designed to be pasted into `generate_screen_from_text` verbatim.
All prompts assume the `Nyasa Dark` design system has been applied (handled via
`apply_design_system` after generation, not repeated in the prompt).

### 6.1 Auth / Sign-in (0.1)

> Landscape car head-unit, 1280×720. Full-screen welcome for a music app.
> Centered column: a 120×120 rounded-square tile filled with a purple gradient
> (#A855F7 → #7C3AED) containing a white music-note icon, large headline
> "Welcome to Nyasa Music" (30 sp bold white), subtitle "Sign in to access your
> library" (20 sp, muted white), a 76 dp-tall "Sign in with Google" button with
> a white background and Google G icon, and a small footer line about terms.
> Background #0D0D0D. Treat the entire area as touch — no small controls.

### 6.2 Home (0.2)

> Landscape car head-unit, 1280×720. Music app home. Top bar (76 dp) spans full
> width: left is a small purple-gradient music-note chip + "Nyasa Music" wordmark;
> center is three pill tabs (Home active, Browse, Library) with the active pill
> filled in purple gradient; right is a clock reading "3:42 PM" in muted grey.
> Below the top bar, a greeting "Good afternoon" (30 sp bold) and subtitle
> "Ready for your drive?" (18 sp muted). Main area is two equal columns:
> left column "Quick Access" as a 2×2 grid of gradient cards (My Music — purple,
> Radio — pink/rose, Favorites — red, Trending — blue/indigo), each card showing
> a large white icon and label; right column "Recently Played" as a vertical list
> of 4 rows, each 80 dp cover + title + artist on a dark card. No mini player yet.

### 6.3 Home with mini player (0.11)

> Same as Home, plus a 112 dp-tall mini-player bar pinned to the bottom. Bar
> content left→right: 80 dp square cover, song title (20 sp semibold white) and
> artist (16 sp muted) with marquee feel; then three circular controls — previous
> (76 dp), play/pause (80 dp purple gradient filled), next (76 dp); then elapsed
> time, a thin purple progress track, total time, and a 76 dp like heart button.
> Bar background slightly lighter than app background.

### 6.4 Browse — default (0.3)

> Landscape car head-unit, 1280×720. Same top bar as Home (Browse tab active).
> Main area is a single scrolling column. Top: a 76 dp search bar with a search
> icon and placeholder "Search songs, artists…" on a dark card. Next: "Browse All"
> heading (22 sp bold), then a 3×2 grid of gradient category cards (Trending Now,
> New Releases, Top Charts, Playlists, Genres, Podcasts) — each card shows a
> large white icon top-right and the category name bottom-left. Last: "Featured
> Playlists" heading, then a horizontal row of 140 dp square album covers with
> name + song count underneath. Right edge shows a slim vertical scrollbar.

### 6.5 Browse — searching (0.4)

> Same as Browse default, but the search bar contains the query "taylor swift",
> has a clear (×) button on the right, and the content below the search bar is
> replaced with a vertical list of search-result rows (song cover, title, artist)
> on dark cards. One row shows a pulsing purple now-playing indicator over the
> cover.

### 6.6 Browse — driving mode (0.5)

> Same as Browse default, but the search bar is visually disabled (reduced opacity)
> with placeholder text "Search unavailable while driving". A small helper chip
> below the search bar reads "Use the car's voice button to search" with a
> microphone icon in purple. Category grid and featured playlists still visible.

### 6.7 Library (0.6)

> Landscape car head-unit, 1280×720. Same top bar, Library tab active.
> Top row inside content: left "Your Library" (30 sp bold) with a
> "Signed in as Ethan" subtitle; right a red-tinted pill "Sign Out" (76 dp tall).
> Then a Liked Songs section with a heart icon, "Liked Songs" heading, "42 songs"
> count, a full-width purple-gradient Shuffle Play button, and the first two
> liked-song rows visible. Below, "Favorite Artists" with a horizontal row of
> five circular artist avatars and names. Finally "Recent Albums" with two rows
> (cover 64 dp + name + artist + circular play button on the right).

### 6.8 Library — sign-out modal (0.7)

> Same Library screen dimmed to 20% brightness with a centered modal card
> (~640 dp wide) on `#1A1A1A` with 24 dp radius. Card contains: "Sign Out?"
> (30 sp bold centered), body text (20 sp muted) "You will need to sign in again
> to access your music library.", and two side-by-side 76 dp buttons — left
> "Cancel" (subtle grey) and right "Sign Out" (red fill with exit icon).

### 6.9 Artist liked songs (0.8)

> Landscape car head-unit, 1280×720. Header row: a circular 76 dp back button
> on the left, then artist avatar (120 dp circle) + artist name (30 sp bold) +
> subtitle "12 liked songs" muted. Top-right: full-width purple-gradient
> Shuffle Play button (76 dp). Below: vertical list of liked songs (cover 80 dp
> + title 18 sp + artist 16 sp muted, on dark cards).

### 6.10 Full player (0.9)

> Landscape car head-unit, 1280×720. Dark background with a subtle radial purple
> glow. Two-column layout. Left column: 400 dp rounded album art (24 dp radius)
> vertically centered. Right column: top row with a circular down-chevron
> collapse button on the left, center kicker "PLAYING FROM PLAYLIST" (12 sp
> uppercase muted) above album name "Chill Drive" (18 sp white). Middle:
> track title "Midnight Roads" (36 sp bold marquee), artist • album line
> (24 sp muted). Progress slider with elapsed/total times (18 sp muted).
> Main controls row centered: shuffle (76 dp), previous (80 dp), play/pause
> (112 dp purple gradient filled), next (80 dp), repeat (76 dp). Below controls,
> centered: a 76 dp heart like button.

### 6.11 Full player — error overlay (0.10)

> Same as Full player but with a dark translucent overlay and a centered card
> showing a red alert icon, "Can't play this track" headline (24 sp bold),
> a short body ("Check your connection and try again."), and two 76 dp buttons
> side-by-side: subtle "Dismiss" and purple-gradient "Retry".

### 6.12 Queue — parked (1.1)

> Landscape car head-unit, 1280×720. Title "Up Next" (30 sp bold) top-left,
> track count "18 songs" muted subtitle. Top-right row of two pill buttons:
> "Clear Queue" (subtle red) and a circular 76 dp close button. Main area is
> a vertical list of queue items — each row is 96 dp tall, shows a drag-handle
> icon on the left (parked only), 80 dp album cover, title + artist, duration
> on the right, and a 76 dp overflow menu. The currently-playing row is
> highlighted with a purple-gradient left border and a pulsing now-playing icon
> over the cover. Mini player pinned to bottom as usual.

### 6.13 Queue — driving (1.2)

> Same as parked queue but: drag handles hidden, "Clear Queue" button disabled
> (reduced opacity), and a small muted helper line under the title:
> "Park the car to reorder or clear your queue."

### 6.14 Browse — voice prompt (1.3)

> Full-screen state layered over Browse. Centered column: a 120 dp circle filled
> with purple gradient containing a large white microphone icon, "Listening…"
> headline (30 sp), and helper "Press the car's voice button and say what you
> want to play." (20 sp muted). A subtle pulsing ring animates around the circle.
> Small "Cancel" link at the bottom.

### 6.15 Browse — voice results (1.4)

> Same as Browse searching, but above the results list a small purple chip
> reads "Voice: 'play upbeat workout music'" with a microphone icon. Results
> are grouped by type with a "Songs" header, list of 3 songs, "Albums" header,
> horizontal strip of 4 albums.

### 6.16 Playlist detail (2.1)

> Landscape car head-unit, 1280×720. Split header: left column is a 320 dp
> playlist cover with 24 dp radius, stacked with "PLAYLIST" kicker, playlist
> title "Chill Drive" (30 sp bold), creator "Made by you" muted, and
> "24 songs · 1 hr 32 min" metadata. Below, two side-by-side 76 dp buttons:
> purple-gradient "Shuffle Play" and subtle-outline "Play". Right column is a
> vertical list of playlist tracks (cover 64 dp + title + artist + duration),
> scrollable with the slim right-edge scrollbar. Mini player at bottom.

### 6.17 Album detail (2.2)

> Same layout as Playlist detail but the left column header says "ALBUM" kicker
> and shows the artist name tappable. Add a "Follow artist" 76 dp ghost button
> between metadata and Shuffle Play.

### 6.18 Artist — full (2.3)

> Landscape car head-unit, 1280×720. Top: a hero band (240 dp tall) with a
> blurred backdrop of the artist image, fading into #0D0D0D. Overlaid: circular
> back button (76 dp), centered artist avatar (160 dp), artist name (36 sp bold),
> subtitle "2.4M monthly listeners" muted, and Shuffle Play + Follow buttons.
> Main area below: "Top Tracks" section — horizontal strip of the first 5 hits
> as large cards with play buttons; then "Albums" section — horizontal strip
> of album covers with name + year.

### 6.19 Full player — with source chip (2.4)

> Same as Full player but the "PLAYING FROM PLAYLIST / Chill Drive" block is now
> a clearly tappable pill (subtle outline, small chevron on the right) — visually
> distinct from the static kicker in 0.9.

### 6.20 Home — continue listening (3.1)

> Same as Home, but insert a new full-width section between the greeting and the
> Quick Access grid titled "Continue listening". It contains a single horizontal
> row of 3 cards, each 320×120: left side is a 96 dp cover with a small circular
> play button overlay, right side is title + "12 min left" + a thin purple
> progress bar showing ~70% progress.

### 6.21 Library — downloads section (3.2)

> Same as Library, but insert a new section between the header row and Liked
> Songs titled "Downloads" with a download icon, "12 songs available offline"
> subtitle, and a full-width 76 dp card "Manage downloads" that leads to the
> Downloads screen.

### 6.22 Downloads (3.3)

> Landscape car head-unit, 1280×720. Title "Downloads" (30 sp bold),
> subtitle "342 MB used of 2 GB". Top actions row: "Only show downloaded",
> "Remove all downloads" (red text), and a small storage-usage bar. Main area
> is a vertical list of downloaded songs/albums, each row with a green check
> mark download indicator, cover, title, artist, size, and a 76 dp overflow
> menu that exposes "Remove download".

### 6.23 Mini player — with up-next peek (3.4)

> Same as Mini player from 0.11 but the right zone is reorganized: elapsed time
> + thin progress + total time, then a small "Next: Sunset Avenue" peek line
> (12 sp muted) above a 76 dp queue-list icon button. Like heart moves next
> to the queue button.

### 6.24 Settings (4.1)

> Landscape car head-unit, 1280×720. Title "Settings" (30 sp bold) with
> circular back button on the left. Two-column layout. Left column is a
> vertical list of section labels (Audio, Downloads, Playback, Account) —
> each row is a 76 dp card; the selected row has a purple-gradient left
> border. Right column shows the selected section's content. Default view
> is Audio with rows: "Audio quality — Very High", "Crossfade — Off",
> "Normalize volume — On" (switch), each row 76 dp tall.

### 6.25 Settings — audio quality picker (4.2)

> Settings layout with the Audio section expanded, and a modal overlay showing
> an "Audio quality" picker: four 76 dp radio rows — "Low (96 kbps)",
> "Normal (160 kbps)", "High (320 kbps)", "Very High (lossless)" — the current
> choice selected with a purple filled circle.

### 6.26 Full player — wavy M3 progress (5.1)

> Same as Full player 0.9, but replace the straight progress slider with a
> Material 3 Expressive wavy progress bar in purple, amplitude ~4 dp. Keep
> elapsed/total labels below.

## 7. Workflow

1. Write this plan (✅).
2. Create Stitch project → record ID in §4.
3. Create `Nyasa Dark` design system → record asset ID in §4; apply to project.
4. Generate Phase 0 screens (0.1 → 0.11) one at a time. Review each.
5. User approves Phase 0 → continue with Phase 1 through 5 in order. Checkpoint
   with the user between phases.
6. After all phases approved: export screens/code from Stitch, open a fresh
   branch, and start porting to Compose one screen at a time, verifying against
   the Stitch reference.

## 8. Non-goals for the design pass

- No Compose code is written until every screen in this plan is approved.
- Voice-session activation, `MediaSession` plumbing, Firestore schema, and DPI
  audit are **not** in scope for Stitch — they are implementation concerns.
- This document does not attempt to be a spec for visual micro-interactions
  (swipe, spring animations). Those are ratified during Compose port.

## 9. Open questions for the user

1. **Stitch device type**: the available enum is `MOBILE / TABLET / DESKTOP /
   AGNOSTIC`. No car type. I'm defaulting to `TABLET` (landscape aspect). If the
   output feels too phone-proportioned, we switch to `DESKTOP` for subsequent
   screens.
2. **Voice trigger**: AAOS apps cannot start voice themselves — the OEM assistant
   is the trigger. The "voice prompt" screen (1.3) is therefore an educational
   state, not a live listener. OK with that framing?
3. **Downloads scope**: are offline downloads actually on the roadmap, or should
   Phase 3 focus only on the "Continue listening" + mini-player upgrades and
   defer Downloads until backend support lands?
4. **Settings home**: should sign-out move out of Library into Settings now
   (cleaner) or stay in both (safer transition)?
