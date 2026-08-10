package com.example.nyasaplayer.auto.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.nyasaplayer.auto.ui.components.CarEmptyState

/**
 * Screen 17 — Favourites with nothing liked.
 *
 * Rendered in place by [CarFavouriteMusicScreen] rather than being a navigation destination.
 * Favourites is a tab root at drill depth 0; a destination would sit at depth 1, where
 * `maxContentDepth` can refuse it — blocking a driver from a screen whose only content is
 * "you have nothing yet". See spec D21.
 */
@Composable
fun CarEmptyFavouritesScreen(
    onBrowseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CarEmptyState(
        title = "No favourites yet",
        body = "Songs you like will appear here.",
        modifier = modifier,
        icon = Icons.Filled.FavoriteBorder,
        actionLabel = "Browse Music",
        onAction = onBrowseClick,
    )
}
