# AAOS screen regeneration — Stitch prompts

> **Archived prompt replay. Do not use for implementation.**
> These prompts describe an early six-screen Stitch generation run and still contain stale
> values such as 72px controls, `#A0A0B0` secondary text and six-screen-only scope.
> Current implementation requirements live in `docs/AAOS_SCREEN_CONTRACT.md`,
> `docs/AAOS_COMPLIANCE.md` and `docs/aaos-DESIGN.md`.

Replay script for regenerating the six Nyasa Music AAOS screens after the Stitch
quota resets. The design system already carries the chrome contract and the
rendering rules, so each prompt below only describes screen-specific content.

## Handles

| Thing | Value |
|---|---|
| Stitch project | `projects/2055291413925827809` (`Nyasa Music AAOS`) |
| `projectId` argument | `2055291413925827809` |
| `designSystem` argument | `assets/c3fc1bc49a674dd695a05fb51bf564d0` |
| `deviceType` | `DESKTOP` |
| Source DESIGN.md | `docs/aaos-DESIGN.md` |

Call `mcp__stitch__generate_screen_from_text` once per prompt. The tool routinely
returns a timeout while still succeeding — do not retry on timeout. Poll
`mcp__stitch__list_screens` every 30s, up to 10 times, before treating a screen as
genuinely failed.

To rebuild the design system from scratch — in a fresh project, or after editing the
tokens — base64-encode `docs/aaos-DESIGN.md`, upload it with
`mcp__stitch__upload_design_md`, then call
`mcp__stitch__create_design_system_from_design_md` on the screen instance that returns.
The existing design system already carries this content, so a plain replay does not
need those two steps.

## Chrome per screen

The design system declares the top system bar, the 80px left navigation rail, and the
88px mini-player as a fixed contract rendered identically everywhere. Two screens are
deliberate exceptions, stated inline in their prompts:

| # | Screen | System bar | Nav rail | Mini-player | Active rail item |
|---|---|---|---|---|---|
| 1 | CarAuthScreen | yes | no | no | — |
| 2 | CarPinOptInScreen | yes | no | no | — |
| 3 | CarHomeScreen | yes | yes | yes | Home |
| 4 | CarBrowseScreen | yes | yes | yes | Browse |
| 5 | CarSearchScreen | yes | yes | yes | Home |
| 6 | CarSearchResultsScreen | yes | yes | yes | Home |

Screens 1 and 2 drop the rail and mini-player because nobody is signed in and nothing
is playing. Screens 5 and 6 are reached from the system bar search icon rather than
from the rail, so they render as an overlay above the originating tab and leave Home
active — the contract requires exactly one active item, and search is not itself a
rail destination.

## Defects these prompts correct

Carried from the six images in
`~/.gemini/antigravity/brain/6f9b6c36-2627-4aad-a755-c755d086b9d5/`:

- System bar differed on every screen; nav rail present on only two of six.
- Screen 3 rendered as a redline — dimension callouts (`48px`, `200px`, `380px`, `64px`,
  `88px`) drawn into the image, and song subtitles reading "Nova Grey · 16px".
- Screens 3 and 4 printed state names as visible text ("Home ACTIVE", "All ACTIVE").
- Screen 5 duplicated a label ("BROWSE BY" eyebrow over a "Browse by" heading), repeated
  "Browsing all categories below" in two columns, and left an empty sixth chip.
- Screen 2 filled the fourth PIN dot instead of the first, and wrapped the wordmark onto
  two lines.

The first four are handled globally by the design system's rendering rules. The PIN dot
and the wordmark are handled by the chrome contract and by prompt 2 below.

---

## 1 — CarAuthScreen

```
CarAuthScreen for Nyasa Music on a 1920x1080 Android Automotive OS head unit. Landscape, dark, sign-in gate.

Pre-sign-in screen: render the top system bar only. No navigation rail and no mini-player, because nobody is signed in and nothing is playing.

Centered in the content area, stacked vertically:
- "Nyasa Music" large champagne gold logotype, 72px weight 700, with a thin gold hairline rule under it
- "Welcome back" white 40px weight 700
- "Connect your music profile before you drive" in #A0A0B0 22px
- A gold-outlined "Parked mode only" pill with a small car icon
- Three stacked buttons, 560px wide, 72px tall, 16px apart: "Continue with Google" with the Google G mark, "Continue with phone" with a phone glyph, "Use email & password" with no icon. First two are transparent with a hairline white border and white 20px labels. The third is quieter, with a #555568 label.
- Footer in #A0A0B0 14px: "Sign-in is only available while parked. Audio continues during your journey."

Obsidian #0A0A0C background with one soft diagonal aurora gradient from lower-left to upper-right in low-intensity blue and purple, kept subtle behind the content so text contrast is unaffected.

Render the finished product screen only: no measurement annotations, no dimension lines, no pixel labels, no state words.
```

## 2 — CarPinOptInScreen

```
CarPinOptInScreen for Nyasa Music on a 1920x1080 Android Automotive OS head unit. Landscape, dark.

Pre-sign-in screen: render the top system bar only. No navigation rail and no mini-player. The "Nyasa Music" wordmark in the system bar stays on a single line at its normal size — it must not wrap to two lines or shrink.

A gold-outlined "Parked mode only" pill with a small car icon sits centered just below the system bar.

Centered on the screen, one glass card at 20px radius, #181824 fill, 1px hairline white border, roughly 720px wide:
- "Protect your driver profile" white 40px weight 700
- "Your PIN keeps purchases and profile changes secure. You'll need it when parked." in #A0A0B0 22px, wrapped to two lines, centered
- Six PIN indicator circles in a row, 44px each. Exactly one is filled, and it is the FIRST circle on the left, filled solid champagne gold. The remaining five are empty with a 2px #555568 outline. Do not fill any circle other than the leftmost one.
- A 3x4 numeric keypad below: rows 1-2-3, 4-5-6, 7-8-9, then backspace-0-blank. Each key is a 96px tall rounded rectangle, #1E1E2A fill, hairline white border, white 28px numeral. Every key is styled identically at rest — do not highlight or gold-outline a scattered subset of keys.
- A gold "Enable PIN" primary CTA, full card width minus padding, 72px tall
- "Not now" as a quiet ghost action in #A0A0B0 20px, centered beneath

Obsidian #0A0A0C background with a soft low-intensity blue glow in the lower left only.

Render the finished product screen only: no measurement annotations, no dimension lines, no pixel labels, no state words.
```

## 3 — CarHomeScreen

```
CarHomeScreen for Nyasa Music on a 1920x1080 Android Automotive OS head unit. Landscape, dark.

Full chrome: top system bar, left navigation rail with Home active, persistent mini-player at the bottom.

Content area:
- Greeting "Good evening, Alex" white 40px weight 700
- Beneath it "Saturday · Clear skies · 21°C" in #A0A0B0 22px
- Section heading "Continue Listening" white 22px weight 700
- A horizontal row of three wide cards, each 380x200, 20px radius, 24px apart, the third clipped at the right edge to imply scroll. Each card has abstract gradient cover art filling it, with the song title in white 24px weight 700 over the artist name in #A0A0B0 18px, bottom-left. The first card carries a 56px gold circular play button at its bottom-right.
  1. "After Midnight" / "Nova Grey" — indigo to violet gradient
  2. "City Lights" / "Electric North" — deep teal gradient
  3. "Blue Horizon" / "Ava Lane" — amber to rust gradient
- Section heading "Your Mixes" white 22px weight 700
- A second horizontal row of three square gradient cards beneath it, clipped by the mini-player

Card subtitles contain the artist name and nothing else. Do not append a font size, a token name, or any measurement to a song or artist label.

Mini-player shows "After Midnight" by "Nova Grey", paused state, progress bar about 60% filled in gold.

Render the finished product screen only: no measurement annotations, no dimension lines, no rulers, no pixel labels such as 48px or 380px, no state words.
```

## 4 — CarBrowseScreen

```
CarBrowseScreen for Nyasa Music on a 1920x1080 Android Automotive OS head unit. Landscape, dark.

Full chrome: top system bar, left navigation rail with Browse active, persistent mini-player at the bottom.

Content area:
- Screen title "Browse" white 40px weight 700
- A row of five filter chips, 56px tall, fully rounded, 16px apart: All, Genres, Moods, Podcasts, New Releases. "All" is selected: gold fill with dark bold text. The other four are #1E1E2A with a hairline white border and white text.
- A 3-column by 2-row grid of genre tiles, 24px gutters, 20px radius, each filling its cell with a rich diagonal gradient and the genre name in bold white italic 36px at the bottom-left, plus a small translucent white glyph at the bottom-right:
  Pop (pink to coral, music note), Hip-Hop (amber to gold, waveform), Rock (slate blue, lightning bolt), Electronic (cyan to teal, equalizer), R&B (mauve to rose, vinyl), Jazz (bronze to brown, saxophone)

The selected chip and the active rail item are indicated by gold color and pill treatment alone. Do not render the word ACTIVE, SELECTED, or any other state name as visible text anywhere.

Mini-player shows "After Midnight" by "Nova Grey", playing state.

Render the finished product screen only: no measurement annotations, no dimension lines, no pixel labels, no state words.
```

## 5 — CarSearchScreen

```
CarSearchScreen for Nyasa Music on a 1920x1080 Android Automotive OS head unit. Landscape, dark.

Full chrome: top system bar, left navigation rail with Home active, persistent mini-player at the bottom. Search is an overlay above the Home tab, so Home stays the active rail item.

Content area:
- A back control at the top left of the content area: a chevron and the word "Back" in white 20px
- A full-width search input, 72px tall, 16px radius, #1E1E2A fill, focused: 2px gold border with a soft gold outer glow. A search glyph at its left, placeholder "Search songs, artists, albums, or playlists" in #555568 22px, a gold microphone glyph at its right.
- Below, three columns:
  LEFT — heading "Recent" white 22px weight 700, then four rows each with a clock glyph and white 22px text: "Nova Grey", "Night Drive playlist", "City Lights", "Jazz focus", separated by hairline dividers
  CENTER — a large 260px circular voice target with a 2px gold ring and a dark blue radial fill, a white microphone glyph at its center, a soft gold outer glow. Beneath it "Tap to search by voice" white 26px weight 700, and under that "Voice search is recommended while driving" in #A0A0B0 italic 18px.
  RIGHT — heading "Browse by" white 22px weight 700, then a 2-column grid of exactly five chips: Songs, Albums, Artists, Playlists, Podcasts. Every cell holds a real chip.

Use exactly one heading above the right column — the words "Browse by" appear once, as a title-case heading, with no uppercase eyebrow above them repeating the same words. Do not place a helper sentence beneath more than one column, and do not leave a sixth empty chip in the grid.

Mini-player shows "After Midnight" by "Nova Grey", playing state.

Render the finished product screen only: no measurement annotations, no dimension lines, no pixel labels, no state words.
```

## 6 — CarSearchResultsScreen

```
CarSearchResultsScreen for Nyasa Music on a 1920x1080 Android Automotive OS head unit. Landscape, dark.

Full chrome: top system bar, left navigation rail with Home active, persistent mini-player at the bottom. The navigation rail must be present on this screen — do not drop it.

Content area:
- A back chevron, then a search input, 72px tall, 16px radius, gold 2px border and soft glow, containing the query "Midnight" in white 24px, with a clear X and a gold microphone glyph at its right
- Below, three columns:
  LEFT (narrow) — heading "Top Result" white 22px weight 700, then a card at 20px radius, #181824 fill, containing 200px square abstract violet gradient cover art, "After Midnight" white 28px weight 700, "Nova Grey · Song" in #A0A0B0 18px, and a full-width gold "Play" primary CTA with a play glyph, 72px tall
  CENTER (widest) — heading "Songs" white 22px weight 700, then four track rows, 80px tall, hairline dividers between them: 52px gradient art, title white 18px over artist #A0A0B0 15px, duration right-aligned in #A0A0B0, an overflow ellipsis, and a 44px gold circular play button.
    "After Midnight" / "Nova Grey" / 3:42 — violet
    "Midnight Sun" / "Ava Lane" / 4:15 — teal
    "Midnight City" / "Electric North" / 5:01 — deep blue
    "Midnight Calm" / "Mira Vale" / 3:28 — amber
    The first row is the currently playing track and carries a 3px gold bar on its left edge.
  RIGHT (narrow) — heading "Albums" white 22px weight 700, then two cards each with 72px gradient art, album title white 20px, artist and year in #A0A0B0 15px: "Glass Roads" / "Nova Grey" / 2024, "Midnight Haze" / "Ava Lane" / 2023. Then heading "Artists" white 22px weight 700, then one row with a 56px circular avatar reading "NG", "Nova Grey" white 20px over "Artist" in #A0A0B0 15px, and a chevron at the right.

Mini-player shows "After Midnight" by "Nova Grey", playing state.

Render the finished product screen only: no measurement annotations, no dimension lines, no pixel labels, no state words.
```

---

## After regeneration

Compare all six side by side and confirm the chrome actually held — the previous run's
failure mode was per-screen drift that only shows up when the screens are viewed
together, not one at a time. Check specifically that the system bar right cluster is
the same seven items in the same order on every screen, and that the rail is present
and consistent on screens 3 through 6.
