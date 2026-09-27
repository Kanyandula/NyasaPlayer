package com.example.nyasaplayer.auto.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.nyasaplayer.auto.artwork.ArtworkThemeDefaults
import com.example.nyasaplayer.auto.ui.motion.animateDecorativeColor
import com.example.nyasaplayer.auto.ui.theme.CarOutline
import com.example.nyasaplayer.auto.ui.theme.CarRaised
import com.example.nyasaplayer.auto.ui.theme.CarScreenMargin
import com.example.nyasaplayer.auto.ui.theme.CarTouchTargetSize
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.common.ui.icons.ChevronRightIcon
import com.example.nyasaplayer.core.common.ui.icons.PauseIcon
import com.example.nyasaplayer.core.common.ui.icons.RepeatIcon
import com.example.nyasaplayer.core.common.ui.icons.RepeatOneIcon
import com.example.nyasaplayer.core.common.ui.icons.ShuffleIcon
import com.example.nyasaplayer.core.common.ui.icons.SkipNextIcon
import com.example.nyasaplayer.core.common.ui.icons.SkipPreviousIcon
import com.example.nyasaplayer.core.common.ui.theme.NyasaBackground
import com.example.nyasaplayer.core.common.ui.theme.NyasaGold
import com.example.nyasaplayer.core.common.ui.theme.NyasaOnGold
import com.example.nyasaplayer.core.common.util.formatDuration
import com.example.nyasaplayer.core.playback.PlaybackSnapshot
import com.example.nyasaplayer.core.playback.RepeatMode

private val FullPlayerAlbumArtSize = 400.dp

/** The design's right column; the transport row nearly fills it, as drawn. */
private val PanelMaxWidth = 536.dp
private val AlbumArtCorner = 28.dp
private val DefaultGlow = Color(ArtworkThemeDefaults.theme.fullPlayerGlow)
private val PlayButtonSize = 96.dp
private val PlayIconSize = 40.dp
private val TransportIconSize = 30.dp
private val BufferingRingStroke = 5.dp
private val BufferingRingInset = 16.dp

// The mockup's vertical rhythm (docs: AAOS Now Playing plan, "What the design asks for"). The seek
// bar's 76dp touch box already holds ~34dp of air above its track, so nothing is added there.
private val TopBarToTitleGap = 26.dp
private val TitleToArtistGap = 8.dp
private val ControlsGap = 22.dp
private val SourceLabelGap = 14.dp

private val TitleSize = 48.sp
private val TitleTracking = (-0.03).em
private val TitleLineHeight = 1.04.em
private const val TabularDigits = "tnum"
private val LikeIconSize = 26.dp
private val ArtistSize = 24.sp
private val SourceNameSize = 20.sp
private val CaptionSize = 14.sp // the mockup's 12-13px captions, raised to the NFR-3 text floor
private val CaptionTracking = 0.1.em
private val TimeSize = 18.sp

private val ScrubberTrackHeight = 8.dp
private val ScrubberThumbSize = 20.dp

private val UpNextCorner = 18.dp
private val UpNextArtSize = 48.dp
private val UpNextArtCorner = 10.dp
private val UpNextPadding = 18.dp
private val UpNextGap = 14.dp
private val UpNextTitleSize = 20.sp
private val UpNextMoreSize = 17.sp
private val UpNextChevronSize = 20.dp

private val ButtonFill = Color.White.copy(alpha = 0.13f)
private val ActiveFill = NyasaGold.copy(alpha = 0.18f)
private val LikedRing = 2.dp
private val CaptionColor = Color.White.copy(alpha = 0.7f)
private val ArtistColor = Color.White.copy(alpha = 0.86f)
private val TimeColor = Color.White.copy(alpha = 0.84f)
private val MutedColor = Color.White.copy(alpha = 0.75f)
private val ScrubberRest = Color.White.copy(alpha = 0.24f)
private val UpNextFill = Color.Black.copy(alpha = 0.34f)

// The design's transport spacing, not a rounding slip: five controls at 23dp fit the panel.
private val TransportGap = 23.dp

@Suppress("LongParameterList")
@Composable
fun CarFullPlayerScreen(
    playback: PlaybackSnapshot,
    onCollapseClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onSkipNextClick: () -> Unit,
    onSkipPreviousClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    isLiked: Boolean = false,
    onLikeClick: () -> Unit = {},
    onQueueClick: () -> Unit = {},
    glow: Color = DefaultGlow,
    animateGlow: Boolean = false,
) {
    val song = playback.currentSong
    // The artwork's hue, pre-darkened to the CarRaised ceiling by the player's ArtworkTheme (T38).
    val glowColor by animateDecorativeColor(glow, animateGlow)

    Box(
        // Opaque for the same reason as the queue: a full-screen overlay, outside the chrome
        // contract (spec 2.2).
        modifier = modifier
            .fillMaxSize()
            .background(NyasaBackground),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Read in the draw phase: a hue easing in redraws the glow, not the whole player.
                .drawBehind {
                    drawRect(Brush.radialGradient(colors = listOf(glowColor, Color.Transparent), radius = 800f))
                },
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = CarScreenMargin, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            // Centred: on a window wider than the design's, the spare width goes to the margins,
            // not into a stretched column with empty space after the transport row.
            horizontalArrangement = Arrangement.spacedBy(48.dp, Alignment.CenterHorizontally),
        ) {
            AsyncImage(
                model = song?.resolvedCoverUrl,
                contentDescription = song?.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(FullPlayerAlbumArtSize)
                    .clip(RoundedCornerShape(AlbumArtCorner)),
            )

            PlayerControlsPanel(
                playback = playback,
                onCollapseClick = onCollapseClick,
                onPlayPauseClick = onPlayPauseClick,
                onSkipNextClick = onSkipNextClick,
                onSkipPreviousClick = onSkipPreviousClick,
                onShuffleClick = onShuffleClick,
                onRepeatClick = onRepeatClick,
                onSeek = onSeek,
                isLiked = isLiked,
                onLikeClick = onLikeClick,
                onQueueClick = onQueueClick,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .widthIn(max = PanelMaxWidth)
                    .fillMaxHeight(),
            )
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun PlayerControlsPanel(
    playback: PlaybackSnapshot,
    onCollapseClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onSkipNextClick: () -> Unit,
    onSkipPreviousClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onSeek: (Long) -> Unit,
    isLiked: Boolean,
    onLikeClick: () -> Unit,
    onQueueClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val song = playback.currentSong

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
    ) {
        PlayerTopBar(
            source = playback.queueOrigin.sourceLabel(),
            isLiked = isLiked,
            onCollapseClick = onCollapseClick,
            onLikeClick = onLikeClick,
        )
        Spacer(modifier = Modifier.height(TopBarToTitleGap))
        TrackInfo(
            title = song?.title ?: "",
            artistAlbum = buildArtistAlbumText(song?.resolvedArtistName, song?.albumName),
        )
        ProgressSlider(playback = playback, onSeek = onSeek)
        Spacer(modifier = Modifier.height(ControlsGap))
        MainControls(
            playback = playback,
            onPlayPauseClick = onPlayPauseClick,
            onSkipNextClick = onSkipNextClick,
            onSkipPreviousClick = onSkipPreviousClick,
            onShuffleClick = onShuffleClick,
            onRepeatClick = onRepeatClick,
        )
        // Shown whenever there is a queue, with nothing next too: it is the player's only way into it.
        if (playback.queue.isNotEmpty()) {
            Spacer(modifier = Modifier.height(ControlsGap))
            UpNextStrip(song = playback.upNext(), more = playback.moreAfterUpNext(), onClick = onQueueClick)
        }
    }
}

/** The next track as a card that opens the queue; [more] counts what follows it. No [song]: the end. */
@Composable
private fun UpNextStrip(
    song: Song?,
    more: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(UpNextCorner)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(CarTouchTargetSize)
            .clip(shape)
            .background(UpNextFill)
            .border(1.dp, CarOutline, shape)
            .clickable(onClickLabel = "Open queue", onClick = onClick)
            .padding(horizontal = UpNextPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(UpNextGap),
    ) {
        AsyncImage(
            model = song?.resolvedCoverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(UpNextArtSize)
                .clip(RoundedCornerShape(UpNextArtCorner))
                .background(CarRaised),
        )
        Column(modifier = Modifier.weight(1f)) {
            PlayerCaption("UP NEXT")
            Text(
                text = song?.let(::titleAndArtist) ?: "End of queue",
                color = Color.White,
                fontSize = UpNextTitleSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (more > 0) Text("$more more", color = MutedColor, fontSize = UpNextMoreSize)
        Icon(
            imageVector = ChevronRightIcon,
            contentDescription = null,
            tint = MutedColor,
            modifier = Modifier.size(UpNextChevronSize),
        )
    }
}

@Composable
private fun PlayerTopBar(
    source: SourceLabel?,
    isLiked: Boolean,
    onCollapseClick: () -> Unit,
    onLikeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        CircleIconButton(
            icon = Icons.Default.KeyboardArrowDown,
            contentDescription = "Collapse",
            size = CarTouchTargetSize,
            onClick = onCollapseClick,
        )
        // No origin, no label: a single track has no collection behind it to name.
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = SourceLabelGap),
        ) {
            if (source != null) {
                PlayerCaption(source.heading)
                Text(
                    text = source.name,
                    color = Color.White,
                    fontSize = SourceNameSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // The design's top bar: collapse, source, like. The queue opens from the Up Next card.
        // An outline heart either way, as drawn: liked is told by the gold ring and fill.
        CircleIconButton(
            icon = Icons.Filled.FavoriteBorder,
            contentDescription = if (isLiked) "Unlike" else "Like",
            size = CarTouchTargetSize,
            iconSize = LikeIconSize,
            active = isLiked,
            ringed = isLiked,
            onClick = onLikeClick,
        )
    }
}

/** The small uppercase caption over the source label and in the Up Next card. */
@Composable
private fun PlayerCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        color = CaptionColor,
        fontSize = CaptionSize,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = CaptionTracking,
    )
}

@Composable
private fun TrackInfo(
    title: String,
    artistAlbum: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            color = Color.White,
            fontSize = TitleSize,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = TitleTracking,
            lineHeight = TitleLineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = artistAlbum,
            color = ArtistColor,
            fontSize = ArtistSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = TitleToArtistGap),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class) // the Slider overload with thumb and track slots
@Composable
private fun ProgressSlider(
    playback: PlaybackSnapshot,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = if (playback.durationMs > 0) {
        (playback.currentPositionMs.toFloat() / playback.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    val interactionSource = remember { MutableInteractionSource() }
    Column(modifier = modifier) {
        Slider(
            value = progress,
            onValueChange = { fraction -> onSeek((fraction * playback.durationMs).toLong()) },
            modifier = Modifier.fillMaxWidth(),
            interactionSource = interactionSource,
            // The slider is as tall as its tallest slot, and so is its touch area: a 76dp thumb slot
            // makes the seek bar a 76dp target. A height on the Slider itself does not — it pins its
            // own minimum and ignores ours. The thumb and track still draw at their usual size.
            thumb = {
                Box(modifier = Modifier.height(CarTouchTargetSize), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(ScrubberThumbSize)
                            .clip(CircleShape)
                            .background(NyasaGold),
                    )
                }
            },
            track = { state ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ScrubberTrackHeight)
                        .clip(CircleShape)
                        .background(ScrubberRest)
                        // Read in the draw phase: each position tick redraws the fill, no relayout.
                        .drawBehind { drawRect(NyasaGold, size = size.copy(width = size.width * state.value)) },
                )
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                formatDuration(playback.currentPositionMs),
                color = TimeColor,
                fontSize = TimeSize,
                style = LocalTextStyle.current.copy(fontFeatureSettings = TabularDigits),
            )
            // Remaining, not total: the design counts down to the next track.
            val remainingMs = (playback.durationMs - playback.currentPositionMs).coerceAtLeast(0L)
            Text(
                "-${formatDuration(remainingMs)}",
                color = TimeColor,
                fontSize = TimeSize,
                style = LocalTextStyle.current.copy(fontFeatureSettings = TabularDigits),
            )
        }
    }
}

@Composable
private fun MainControls(
    playback: PlaybackSnapshot,
    onPlayPauseClick: () -> Unit,
    onSkipNextClick: () -> Unit,
    onSkipPreviousClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(TransportGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(
            icon = ShuffleIcon,
            contentDescription = "Shuffle",
            size = CarTouchTargetSize,
            iconSize = TransportIconSize,
            active = playback.isShuffled,
            onClick = onShuffleClick,
        )
        CircleIconButton(
            icon = SkipPreviousIcon,
            contentDescription = "Previous",
            size = CarTouchTargetSize,
            iconSize = TransportIconSize,
            onClick = onSkipPreviousClick,
        )
        Box(
            // Fixed at the button's size so the ring appearing never shifts the skip buttons.
            modifier = Modifier.size(PlayButtonSize),
            contentAlignment = Alignment.Center,
        ) {
            PlayPauseButton(isPlaying = playback.isPlaying, onClick = onPlayPauseClick)
            if (playback.isBuffering) {
                BufferingRing()
            }
        }
        CircleIconButton(
            icon = SkipNextIcon,
            contentDescription = "Next",
            size = CarTouchTargetSize,
            iconSize = TransportIconSize,
            onClick = onSkipNextClick,
        )

        val repeatIcon = if (playback.repeatMode == RepeatMode.One) RepeatOneIcon else RepeatIcon
        val repeating = playback.repeatMode != RepeatMode.Off
        CircleIconButton(
            icon = repeatIcon,
            contentDescription = "Repeat",
            size = CarTouchTargetSize,
            iconSize = TransportIconSize,
            active = repeating,
            onClick = onRepeatClick,
        )
    }
}

@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(PlayButtonSize)
            .clip(CircleShape)
            .background(NyasaGold),
    ) {
        Icon(
            imageVector = if (isPlaying) PauseIcon else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = NyasaOnGold,
            modifier = Modifier.size(PlayIconSize),
        )
    }
}

/**
 * Ring around play/pause while the player buffers. It has no pointer input, so the button
 * underneath stays tappable.
 */
@Composable
private fun BufferingRing(modifier: Modifier = Modifier) {
    CircularProgressIndicator(
        // Outside the button, not on it: NyasaOnGold on a gold fill reads as a smudge rather
        // than a ring — seen on the emulator during A5 verification.
        modifier = modifier
            .requiredSize(PlayButtonSize + BufferingRingInset)
            .semantics { contentDescription = "Buffering" },
        color = NyasaGold,
        strokeWidth = BufferingRingStroke,
    )
}

@Composable
private fun CircleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: androidx.compose.ui.unit.Dp = 28.dp,
    active: Boolean = false,
    ringed: Boolean = false,
) {
    val tint = if (active) NyasaGold else Color.White
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (active) ActiveFill else ButtonFill)
            .then(if (ringed) Modifier.border(LikedRing, NyasaGold, CircleShape) else Modifier),
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(iconSize))
    }
}

private fun buildArtistAlbumText(artist: String?, album: String?): String = buildString {
    append(artist ?: "")
    if (!album.isNullOrBlank()) {
        append(" \u2022 $album")
    }
}

/**
 * The track after the current one, or null when nothing follows: the queue's next item, or its
 * first when repeat-all will wrap round to it.
 */
internal fun PlaybackSnapshot.upNext(): Song? {
    if (currentQueueIndex !in queue.indices) return null
    return queue.getOrNull(currentQueueIndex + 1)
        ?: queue.first().takeIf { repeatMode == RepeatMode.All && queue.size > 1 }
}

/** How many tracks follow the one [upNext] shows: the "5 more" beside it. */
internal fun PlaybackSnapshot.moreAfterUpNext(): Int {
    if (upNext() == null) return 0
    val wraps = currentQueueIndex + 1 >= queue.size
    return (if (wraps) queue.size - 2 else queue.size - currentQueueIndex - 2).coerceAtLeast(0)
}

private fun titleAndArtist(song: Song) =
    listOf(song.title, song.resolvedArtistName).filter(String::isNotBlank).joinToString(" · ")
