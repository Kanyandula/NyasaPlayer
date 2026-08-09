package com.example.nyasaplayer.auto.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.nyasaplayer.auto.ui.theme.CarListRowHeight
import com.example.nyasaplayer.auto.ui.theme.CarRaised
import com.example.nyasaplayer.auto.ui.theme.CarTextSecondary
import com.example.nyasaplayer.core.common.ui.theme.NyasaGold

private val ArtSize = 52.dp
private val ArtRadius = 8.dp
private val PlayingBarWidth = 3.dp
private val PlayingBarInset = 14.dp
private val RowSpacing = 16.dp
private val TitleSize = 18.sp
private val ArtistSize = 15.sp
private val DurationSize = 16.sp
private val HeartGlyphSize = 32.dp

/**
 * One track in a list.
 *
 * The currently playing row carries a gold bar on its left edge and a gold title.
 */
@Suppress("LongMethod")
@Composable
fun CarTrackRow(
    title: String,
    artist: String,
    duration: String,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    coverUrl: String = "",
    onLikeToggle: (() -> Unit)? = null,
    isLiked: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(CarListRowHeight)
            .clickable(onClick = onClick)
            .then(
                if (onLikeToggle == null) {
                    Modifier
                } else {
                    Modifier.semantics {
                        stateDescription = if (isLiked) "Liked" else "Not liked"
                        customActions = listOf(
                            CustomAccessibilityAction(
                                label = if (isLiked) "Unlike" else "Like",
                                action = {
                                    onLikeToggle()
                                    true
                                },
                            ),
                        )
                    }
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowSpacing),
    ) {
        Box(
            modifier = Modifier
                .width(PlayingBarWidth)
                .fillMaxHeight()
                .padding(vertical = PlayingBarInset)
                .background(if (isPlaying) NyasaGold else Color.Transparent),
        )
        AsyncImage(
            model = coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(ArtSize)
                .clip(RoundedCornerShape(ArtRadius))
                .background(CarRaised),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (isPlaying) NyasaGold else Color.White,
                fontSize = TitleSize,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = artist,
                color = CarTextSecondary,
                fontSize = ArtistSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = duration,
            color = CarTextSecondary,
            fontSize = DurationSize,
        )
        if (onLikeToggle != null) {
            Box(
                modifier = Modifier
                    .carTouchTarget()
                    .clickable(onClick = onLikeToggle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (isLiked) "Unlike" else "Like",
                    tint = if (isLiked) NyasaGold else CarTextSecondary,
                    modifier = Modifier.size(HeartGlyphSize),
                )
            }
        }
    }
}
