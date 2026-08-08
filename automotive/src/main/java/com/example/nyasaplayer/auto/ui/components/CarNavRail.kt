package com.example.nyasaplayer.auto.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nyasaplayer.auto.ui.navigation.CarScreen
import com.example.nyasaplayer.auto.ui.theme.CarChrome
import com.example.nyasaplayer.auto.ui.theme.CarNavRailWidth
import com.example.nyasaplayer.auto.ui.theme.CarTextSecondary
import com.example.nyasaplayer.core.common.ui.icons.HeartIcon
import com.example.nyasaplayer.core.common.ui.icons.HomeIcon
import com.example.nyasaplayer.core.common.ui.icons.LibraryIcon
import com.example.nyasaplayer.core.common.ui.icons.SearchIcon
import com.example.nyasaplayer.core.common.ui.theme.NyasaGold

private val RailItemHeight = 88.dp
private val RailIconSize = 28.dp
private val RailLabelSize = 13.sp
private val RailPillInset = 8.dp
private val RailPillRadius = 20.dp
private const val ActivePillAlpha = 0.12f
private const val PillSlideDurationMs = 220

/**
 * The left navigation rail. One of the three chrome regions.
 *
 * Navigation used to live in the top bar; the design puts it here, and exactly one item is
 * active per screen.
 *
 * **Never disabled while driving.** FR-2.7 lists tab switching as always available, so this
 * component takes no restriction state at all — there is nothing here to gate.
 *
 * [animateSelection] drives the active pill's slide, which is decorative motion and therefore
 * parked-only. The rail cannot read vehicle state itself; the shell resolves the predicate
 * and passes it down.
 */
@Composable
fun CarNavRail(
    currentScreen: CarScreen,
    onSelectTab: (CarScreen) -> Unit,
    modifier: Modifier = Modifier,
    animateSelection: Boolean = false,
) {
    val destinations = CarScreen.entries
    val selectedIndex = destinations.indexOf(currentScreen).coerceAtLeast(0)
    val targetOffset = RailItemHeight * selectedIndex
    val pillOffset by animateDpAsState(
        targetValue = targetOffset,
        animationSpec = tween(durationMillis = if (animateSelection) PillSlideDurationMs else 0),
        label = "navRailPill",
    )

    Box(
        modifier = modifier
            .width(CarNavRailWidth)
            .fillMaxHeight()
            .background(CarChrome),
    ) {
        Box(
            modifier = Modifier
                .offset(y = pillOffset)
                .padding(horizontal = RailPillInset)
                .fillMaxWidth()
                .height(RailItemHeight)
                .background(NyasaGold.copy(alpha = ActivePillAlpha), RoundedCornerShape(RailPillRadius)),
        )
        Column(modifier = Modifier.fillMaxHeight()) {
            destinations.forEach { destination ->
                CarNavRailItem(
                    icon = iconFor(destination),
                    label = labelFor(destination),
                    selected = destination == currentScreen,
                    onClick = { onSelectTab(destination) },
                )
            }
        }
    }
}

@Composable
private fun CarNavRailItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (selected) NyasaGold else CarTextSecondary
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(RailItemHeight)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            // The Text below already labels this row; describing the icon too makes
            // TalkBack announce "Home, Home".
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(RailIconSize),
        )
        Text(
            text = label,
            color = tint,
            fontSize = RailLabelSize,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private fun iconFor(screen: CarScreen): ImageVector = when (screen) {
    CarScreen.Home -> HomeIcon
    CarScreen.Browse -> SearchIcon
    CarScreen.Library -> LibraryIcon
    CarScreen.Favourites -> HeartIcon
}

private fun labelFor(screen: CarScreen): String = when (screen) {
    CarScreen.Home -> "Home"
    CarScreen.Browse -> "Browse"
    CarScreen.Library -> "Library"
    CarScreen.Favourites -> "Favourites"
}
