package com.example.nyasaplayer.auto.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nyasaplayer.auto.ui.theme.CarChrome
import com.example.nyasaplayer.auto.ui.theme.CarSystemBarHeight
import com.example.nyasaplayer.auto.ui.theme.CarTextDisabled
import com.example.nyasaplayer.auto.ui.theme.CarTextSecondary
import com.example.nyasaplayer.core.common.ui.icons.MusicNoteIcon
import com.example.nyasaplayer.core.common.ui.icons.ProfileIcon
import com.example.nyasaplayer.core.common.ui.icons.SearchIcon
import com.example.nyasaplayer.core.common.ui.icons.SettingsIcon
import com.example.nyasaplayer.core.common.ui.theme.NyasaGold
import com.example.nyasaplayer.core.common.ui.theme.NyasaOnGold
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val LogoSize = 40.dp
private val ControlIconSize = 24.dp
private val ControlSpacing = 8.dp

/**
 * The top system bar. One of the three chrome regions, rendered identically on every screen
 * inside the shell.
 *
 * Navigation is deliberately absent: it lives in [CarNavRail]. The design specifies this bar
 * as a wordmark plus a right-hand control cluster, and having tabs in both places is how a
 * driver's muscle memory breaks between screens.
 *
 * The three callbacks are accepted but unused: the controls they belong to are disabled
 * until A6 and A7 give them destinations. They are in the signature now so enabling each is
 * a one-line change rather than a signature change rippling to the caller.
 */
@Suppress("UnusedParameter")
@Composable
fun CarSystemBar(
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(CarSystemBarHeight)
            .background(CarChrome)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppLogo()
        Spacer(modifier = Modifier.weight(1f))
        SystemBarControls()
        ClockDisplay(modifier = Modifier.padding(start = 16.dp))
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
                .background(NyasaGold),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = MusicNoteIcon,
                contentDescription = null,
                tint = NyasaOnGold,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = "Nyasa Music",
            color = NyasaGold,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/**
 * Search, settings and profile.
 *
 * All three are **disabled** until A6 (search) and A7 (settings, profile) give them
 * destinations. Disabled rather than silently inert: FR-2.6 prohibits a control that looks
 * live and does nothing. They keep their full hit areas so the bar does not reflow when the
 * later slices enable them, and the callbacks stay in the signature so enabling each is a
 * one-line change.
 *
 * Wi-fi, bluetooth and battery are deliberately absent — see the A2 spec, D7.
 */
@Composable
private fun SystemBarControls(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ControlSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SystemBarControl(SearchIcon, "Search")
        SystemBarControl(SettingsIcon, "Settings")
        SystemBarControl(ProfileIcon, "Profile")
    }
}

@Composable
private fun SystemBarControl(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.carTouchTarget(),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = CarTextDisabled,
            modifier = Modifier.size(ControlIconSize),
        )
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
        color = CarTextSecondary,
        fontSize = 16.sp,
        modifier = modifier,
    )
}
