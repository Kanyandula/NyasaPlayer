package com.example.nyasaplayer.auto.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nyasaplayer.auto.ui.navigation.CarScreen
import com.example.nyasaplayer.core.common.ui.icons.HomeIcon
import com.example.nyasaplayer.core.common.ui.icons.LibraryIcon
import com.example.nyasaplayer.core.common.ui.icons.MusicNoteIcon
import com.example.nyasaplayer.core.common.ui.icons.SearchIcon
import com.example.nyasaplayer.core.common.ui.theme.NyasaPrimary
import com.example.nyasaplayer.core.common.ui.theme.NyasaPrimaryDark
import com.example.nyasaplayer.core.common.ui.theme.NyasaSurface2
import com.example.nyasaplayer.core.common.ui.theme.NyasaTextSecondary
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TopBarHeight = 64.dp
private val TabCornerRadius = 24.dp
private val LogoSize = 40.dp

@Composable
fun CarTopBar(
    currentScreen: CarScreen,
    onSelectTab: (CarScreen) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(TopBarHeight)
            .background(NyasaSurface2)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppLogo()
        Spacer(modifier = Modifier.weight(1f))
        NavigationTabs(currentScreen = currentScreen, onSelectTab = onSelectTab)
        Spacer(modifier = Modifier.weight(1f))
        ClockDisplay()
    }
}

@Composable
private fun AppLogo(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(LogoSize)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(NyasaPrimary, NyasaPrimaryDark))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = MusicNoteIcon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = "Nyasa Music",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun NavigationTabs(
    currentScreen: CarScreen,
    onSelectTab: (CarScreen) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CarTab(icon = HomeIcon, label = "Home", isSelected = currentScreen == CarScreen.Home) {
            onSelectTab(CarScreen.Home)
        }
        CarTab(icon = SearchIcon, label = "Browse", isSelected = currentScreen == CarScreen.Browse) {
            onSelectTab(CarScreen.Browse)
        }
        CarTab(icon = LibraryIcon, label = "Library", isSelected = currentScreen == CarScreen.Library) {
            onSelectTab(CarScreen.Library)
        }
    }
}

private const val ClockUpdateIntervalMs = 60_000L

@Composable
private fun ClockDisplay(modifier: Modifier = Modifier) {
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(ClockUpdateIntervalMs)
            currentTime = System.currentTimeMillis()
        }
    }
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    Text(
        text = timeFormat.format(Date(currentTime)),
        color = NyasaTextSecondary,
        fontSize = 16.sp,
        modifier = modifier,
    )
}

@Composable
private fun CarTab(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(TabCornerRadius)
    Box(
        modifier = Modifier
            .clip(shape)
            .then(
                if (isSelected) {
                    Modifier.background(Brush.horizontalGradient(listOf(NyasaPrimary, NyasaPrimaryDark)), shape)
                } else {
                    Modifier.border(1.dp, Color.White.copy(alpha = 0.1f), shape)
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) Color.White else NyasaTextSecondary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                color = if (isSelected) Color.White else NyasaTextSecondary,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}
