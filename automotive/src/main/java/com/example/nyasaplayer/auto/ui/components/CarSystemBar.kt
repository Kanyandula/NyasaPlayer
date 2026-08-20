package com.example.nyasaplayer.auto.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

private val LogoSize = 40.dp
private val ControlIconSize = 24.dp
private val AvatarIconSize = 32.dp
private val ControlSpacing = 8.dp

/**
 * The top system bar. One of the three chrome regions, rendered identically on every screen
 * inside the shell.
 *
 * Navigation is deliberately absent: it lives in [CarNavRail]. The design specifies this bar
 * as a wordmark plus a right-hand control cluster, and having tabs in both places is how a
 * driver's muscle memory breaks between screens.
 *
 * [onSearchClick] opens the search sheet (A6). [onSettingsClick] and [onAvatarClick] are
 * accepted but unused: those controls stay disabled until A7 gives them destinations. They are
 * in the signature now so the caller does not need one more change later.
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
        SystemBarControls(onSearchClick = onSearchClick)
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
 * Search is live from A6. Settings and profile stay **disabled** until A7 gives them
 * destinations. Disabled rather than silently inert: FR-2.6 prohibits a control that looks
 * live and does nothing. All three keep their full hit areas so the bar does not reflow when
 * that slice enables the other two.
 *
 * Wi-fi, bluetooth and battery are deliberately absent — see the A2 spec, D7.
 */
@Composable
private fun SystemBarControls(onSearchClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ControlSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SystemBarControl(SearchIcon, "Search", onClick = onSearchClick)
        SystemBarControl(SettingsIcon, "Settings")
        SystemBarControl(ProfileIcon, "Profile", iconSize = AvatarIconSize)
    }
}

/** A null [onClick] is the disabled control: no click target, and the disabled tint. */
@Composable
private fun SystemBarControl(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = ControlIconSize,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .carTouchTarget()
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (onClick == null) CarTextDisabled else CarTextSecondary,
            modifier = Modifier.size(iconSize),
        )
    }
}

private const val MinuteMs = 60_000L

@Composable
private fun ClockDisplay(modifier: Modifier = Modifier) {
    // Aligned to the minute boundary rather than to composition: a flat 60s delay leaves the
    // displayed minute stale by however far into a minute the bar happened to first compose,
    // and stays that far off for the life of the Activity.
    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            val millis = System.currentTimeMillis()
            value = millis
            delay((MinuteMs - millis % MinuteMs).milliseconds)
        }
    }
    val locale = Locale.getDefault()
    val formatter = remember(locale) { DateTimeFormatter.ofPattern("h:mm a", locale) }
    Text(
        // The zone is resolved per tick. A remembered SimpleDateFormat caches the timezone it
        // was built with, so a vehicle crossing one would show the wrong hour until the
        // Activity was recreated.
        text = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).format(formatter),
        color = CarTextSecondary,
        fontSize = 16.sp,
        modifier = modifier,
    )
}
