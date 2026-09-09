package com.example.nyasaplayer.auto.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.nyasaplayer.auto.ui.components.CarInfoRow
import com.example.nyasaplayer.auto.ui.components.CarSectionHeader
import com.example.nyasaplayer.auto.ui.components.CarSheetHeader
import com.example.nyasaplayer.auto.ui.components.CarSignOutRow
import com.example.nyasaplayer.auto.ui.components.carConsumeTouches
import com.example.nyasaplayer.auto.ui.theme.CarChrome
import com.example.nyasaplayer.auto.ui.theme.CarScreenMargin

private val RowSpacing = 16.dp
private val SectionSpacing = 32.dp

/**
 * Settings — screen 14, opened by the system bar's gear.
 *
 * A sheet rather than a destination: `CarSheet.Settings` already exists and `GateResult` already
 * refuses it by name under `NO_SETUP`, so a nav destination would mean a second gating path for one
 * restriction (D69).
 *
 * **Only rows that do something.** The screen contract also lists audio quality; nothing in
 * `PlaybackService` reads a quality preference, and a control that stores a value nothing observes
 * is the same lie as a control that looks live and does nothing (FR-2.6, D70). It arrives when a reader
 * does.
 *
 * No parked badge: the gate makes this screen unreachable while driving, so a "parked only" label
 * would announce a condition the driver cannot be in while reading it.
 */
@Composable
fun CarSettingsScreen(
    displayName: String,
    appVersion: String,
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
        CarSheetHeader(title = "Settings", onClose = onClose)

        Column(verticalArrangement = Arrangement.spacedBy(RowSpacing)) {
            CarSectionHeader(title = "Account")
            CarInfoRow(
                label = "Signed in as",
                value = displayName.ifBlank { "Your account" },
            )
            CarSignOutRow(onClick = onSignOut)
        }

        Column(verticalArrangement = Arrangement.spacedBy(RowSpacing)) {
            CarSectionHeader(title = "About")
            CarInfoRow(label = "Nyasa Music", value = appVersion)
        }
    }
}
