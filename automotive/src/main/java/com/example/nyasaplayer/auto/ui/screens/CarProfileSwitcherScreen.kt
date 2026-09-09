package com.example.nyasaplayer.auto.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nyasaplayer.auto.ui.components.CarInfoRow
import com.example.nyasaplayer.auto.ui.components.CarSectionHeader
import com.example.nyasaplayer.auto.ui.components.CarSheetHeader
import com.example.nyasaplayer.auto.ui.components.CarSignOutRow
import com.example.nyasaplayer.auto.ui.components.carConsumeTouches
import com.example.nyasaplayer.auto.ui.theme.CarChrome
import com.example.nyasaplayer.auto.ui.theme.CarScreenMargin
import com.example.nyasaplayer.auto.ui.theme.CarTextSecondary

private val RowSpacing = 16.dp
private val SectionSpacing = 32.dp
private val BodySize = 20.sp
private val BodyLineHeight = 28.sp

/**
 * Profile — screen 20, opened by the system bar's avatar.
 *
 * A profile here is a **Firebase account**, not one of the platform's car users: switching those
 * needs privileged permissions the app does not hold, and it is a whole-vehicle change to put
 * behind a media app's avatar (D66).
 *
 * The app remembers one account at a time, so switching is signing out and signing in as someone
 * else. The screen says that in words rather than offering a picker with one entry: a switcher that
 * cannot switch is the FR-2.6 problem wearing a different hat. Credential storage for a second
 * remembered account is a separate decision, filed rather than half-built.
 */
@Composable
fun CarProfileSwitcherScreen(
    displayName: String,
    onSignOut: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CarChrome)
            .carConsumeTouches()
            .padding(CarScreenMargin),
        verticalArrangement = Arrangement.spacedBy(SectionSpacing),
    ) {
        CarSheetHeader(title = "Profile", onClose = onClose)

        Column(verticalArrangement = Arrangement.spacedBy(RowSpacing)) {
            CarSectionHeader(title = "Current account")
            CarInfoRow(
                label = "Signed in as",
                value = displayName.ifBlank { "Your account" },
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(RowSpacing)) {
            CarSectionHeader(title = "Use a different account")
            Text(
                text = "Nyasa Music stays signed in to one account at a time. " +
                    "Sign out here, then sign in as someone else.",
                color = CarTextSecondary,
                fontSize = BodySize,
                lineHeight = BodyLineHeight,
            )
            CarSignOutRow(onClick = onSignOut)
        }
    }
}
