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
val CarContentCardSize = 180.dp

// Standard card corner radius
val CarCardCornerRadius = 20.dp

// Mini player bar height. Kept at 112 rather than the design's 88: this value predates
// the design, exceeds its intent, and clears the touch target with room.
val CarMiniPlayerHeight = 112.dp

// Top system bar. 80 and not 48 because it carries app-tappable controls (search,
// settings, avatar) and a 48dp bar cannot contain a 76dp target.
val CarSystemBarHeight = 80.dp

// Left navigation rail
val CarNavRailWidth = 80.dp

// Filter chip height
val CarChipHeight = 76.dp

// Pill button height
val CarPillButtonHeight = 76.dp

// Track / content list row height
val CarListRowHeight = 80.dp

// Screen edge margin
val CarScreenMargin = 48.dp
