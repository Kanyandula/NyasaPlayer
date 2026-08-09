@file:Suppress("MatchingDeclarationName") // file is named for CarContentCard, the primary declaration

package com.example.nyasaplayer.auto.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.example.nyasaplayer.auto.ui.theme.CarCardCornerRadius
import com.example.nyasaplayer.auto.ui.theme.CarContentCardSize
import com.example.nyasaplayer.auto.ui.theme.CarRaised
import com.example.nyasaplayer.auto.ui.theme.CarTextDisabled
import com.example.nyasaplayer.auto.ui.theme.CarTextSecondary
import com.example.nyasaplayer.core.common.ui.icons.MusicNoteIcon
import com.example.nyasaplayer.core.common.ui.theme.NyasaGold
import com.example.nyasaplayer.core.common.ui.theme.NyasaOnGold

/** Square for albums, playlists and genres; circle for artists. */
enum class CarCardShape { Square, Circle }

private val LabelSpacing = 10.dp
private val PlaceholderIconSize = 48.dp
private val TitleSize = 18.sp
private val SubtitleSize = 15.sp
private const val DisabledAlpha = 0.4f

/**
 * One card for album, playlist, genre and artist.
 *
 * Replaces `CategoryCard`, `FeaturedPlaylistCard`, `AlbumListItem` and `ArtistAvatar`. Its four
 * states are the inventory's required set: normal, focused (the system focus ring, not painted
 * here), playing ([isPlaying] golds the title) and unavailable ([enabled] = false).
 */
@Composable
fun CarContentCard(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    artworkUrl: String = "",
    shape: CarCardShape = CarCardShape.Square,
    isPlaying: Boolean = false,
    enabled: Boolean = true,
) {
    val cardShape = when (shape) {
        CarCardShape.Square -> RoundedCornerShape(CarCardCornerRadius)
        CarCardShape.Circle -> CircleShape
    }
    // `modifier` is applied before the default width so a caller-supplied width or weight
    // (e.g. BrowseGrid's Modifier.weight(1f)) constrains this card from the outside; the
    // default width(CarContentCardSize) below only takes effect when the caller supplies
    // neither, which is what every existing Library call site relies on.
    Column(
        modifier = modifier
            .width(CarContentCardSize)
            .alpha(if (enabled) 1f else DisabledAlpha)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Square and derived from the card's own resolved width (set above via the caller's
        // modifier, or the CarContentCardSize default) rather than a fixed size, so the tile
        // stays fully covered by its clickable region in both Browse's weighted grid and
        // Library's fixed-width carousels.
        SubcomposeAsyncImage(
            model = artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(cardShape)
                .background(CarRaised),
            loading = { CardPlaceholder() },
            error = { CardPlaceholder() },
        )
        Text(
            text = title,
            color = when {
                !enabled -> CarTextDisabled
                isPlaying -> NyasaGold
                else -> Color.White
            },
            fontSize = TitleSize,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = LabelSpacing),
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                color = CarTextSecondary,
                fontSize = SubtitleSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CardPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NyasaGold),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = MusicNoteIcon,
            contentDescription = null,
            tint = NyasaOnGold,
            modifier = Modifier.size(PlaceholderIconSize),
        )
    }
}
