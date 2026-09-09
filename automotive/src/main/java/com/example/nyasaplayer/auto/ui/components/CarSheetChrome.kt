package com.example.nyasaplayer.auto.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nyasaplayer.auto.ui.theme.CarRaised
import com.example.nyasaplayer.auto.ui.theme.CarSignOutRed
import com.example.nyasaplayer.auto.ui.theme.CarTextSecondary
import com.example.nyasaplayer.auto.ui.theme.CarTouchTargetSize

private val RowHeight = 88.dp
private val RowCornerRadius = 16.dp
private val RowPadding = 24.dp
private val TitleSize = 34.sp
private val RowLabelSize = 22.sp
private val RowValueSize = 18.sp
private val CloseIconSize = 28.dp

// The pairing measured in A3 and shipped on the Library button: the red only ever sits on its
// own 15% wash, never on CarRaised.
private const val SignOutWashAlpha = 0.15f

/**
 * Title and close for a full-screen sheet.
 *
 * The search and queue sheets keep their own headers: those carry a text field and a queue count
 * respectively, so there is nothing here for them to reuse. This is the plain shape the A7 sheets
 * share.
 */
@Composable
fun CarSheetHeader(title: String, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = TitleSize,
            fontWeight = FontWeight.Bold,
        )
        Box(
            modifier = Modifier
                .carTouchTarget()
                .size(CarTouchTargetSize)
                .clip(CircleShape)
                .background(CarRaised)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close $title",
                tint = Color.White,
                modifier = Modifier.size(CloseIconSize),
            )
        }
    }
}

/** A read-only settings row: what it is on the left, what it says on the right. */
@Composable
fun CarInfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(RowHeight)
            .clip(RoundedCornerShape(RowCornerRadius))
            .background(CarRaised)
            .padding(horizontal = RowPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = Color.White, fontSize = RowLabelSize)
        Text(text = value, color = CarTextSecondary, fontSize = RowValueSize)
    }
}

/**
 * The sign-out row. Both A7 sheets offer it and both hand the shell the same request — the
 * confirmation lives there, in [CarSignOutConfirmation], not behind this row.
 */
@Composable
fun CarSignOutRow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(RowHeight)
            .clip(RoundedCornerShape(RowCornerRadius))
            .background(CarSignOutRed.copy(alpha = SignOutWashAlpha))
            .clickable(onClick = onClick)
            .padding(horizontal = RowPadding),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = "Sign Out",
            color = CarSignOutRed,
            fontSize = RowLabelSize,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
