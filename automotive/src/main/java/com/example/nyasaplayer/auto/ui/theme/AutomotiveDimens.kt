package com.example.nyasaplayer.auto.ui.theme

import androidx.compose.ui.unit.dp

// All values are dp. The design document is authored in CSS px on a 1920x1080 canvas;
// see the "Units" section of docs/aaos-DESIGN.md for the conversion rule.

// CTS-compliant minimum touch target (76dp >= 76dp requirement)
val CarTouchTargetSize = 76.dp

// Standard album art / avatar thumbnail used in lists
val CarListArtSize = 80.dp

// Default CarContentCard width and art size (album/playlist/genre/artist tiles), and the
// footprint their loading skeletons (BrowseSkeleton, LibrarySkeleton) reserve to match
val CarContentCardSize = 130.dp

// Standard card corner radius
val CarCardCornerRadius = 20.dp

// Mini player bar height. Kept at 112 rather than the design's 88: this value predates
// the design, exceeds its intent, and clears the touch target with room.
val CarMiniPlayerHeight = 112.dp

/**
 * The most width the mini-player's progress block takes: a cap, not a size. Below it the block
 * takes the share an equal weight would give it; above it the title and artist take the rest. On a
 * 1440dp head unit the share is ~468dp and the cap binds; on a 1024dp unit it is ~260dp and the cap
 * never binds, where a fixed 400 would leave the title and artist ~24dp (D75).
 */
val CarMiniPlayerProgressMaxWidth = 400.dp

// Gap between the mini-player's controls. Design for Driving wants at least 23dp between touch
// targets. ponytail: the full player keeps its private 23dp TransportGap, the design's rhythm;
// fold it in here if the two rows should match.
val CarControlGap = 24.dp

// Top system bar. 80 and not 48 because it carries app-tappable controls (search and
// settings; the avatar moved to the rail, D77) and a 48dp bar cannot contain a 76dp target.
val CarSystemBarHeight = 80.dp

// Left navigation rail. 176 and not 80: the tab label sits beside its icon rather than under
// it. "Favourites" at 18sp ends 147dp from the rail's leading edge (measured on a 160dpi head
// unit; the 64dp prefix is the 20dp start inset, the 28dp icon and their 16dp gap), leaving
// 29dp of rail and 21dp inside the selection pill. It costs the content area 96dp, which drops
// the Browse grid's columns from ~190dp to ~166dp.
val CarNavRailWidth = 176.dp

// Filter chip height
val CarChipHeight = 76.dp

// Pill button height
val CarPillButtonHeight = 76.dp

// Track / content list row height
val CarListRowHeight = 80.dp

// Screen edge margin
val CarScreenMargin = 48.dp
