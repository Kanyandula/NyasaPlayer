package com.example.nyasaplayer.auto.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nyasaplayer.auto.ui.components.CarPillButton
import com.example.nyasaplayer.auto.ui.theme.CarTextSecondary

private val GlyphSize = 96.dp
private val Spacing = 12.dp
private val TitleSize = 40.sp
private val BodySize = 22.sp

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
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing, Alignment.CenterVertically),
    ) {
        Icon(
            imageVector = Icons.Filled.FavoriteBorder,
            // Decorative: the title below carries the same meaning.
            contentDescription = null,
            tint = CarTextSecondary,
            modifier = Modifier.size(GlyphSize),
        )
        Text(
            text = "No favourites yet",
            color = Color.White,
            fontSize = TitleSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Songs you like will appear here.",
            color = CarTextSecondary,
            fontSize = BodySize,
            textAlign = TextAlign.Center,
        )
        CarPillButton(
            label = "Browse Music",
            onClick = onBrowseClick,
            modifier = Modifier.padding(top = Spacing),
        )
    }
}
