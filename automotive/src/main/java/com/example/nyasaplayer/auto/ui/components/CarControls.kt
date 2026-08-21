package com.example.nyasaplayer.auto.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nyasaplayer.auto.ui.theme.CarChipHeight
import com.example.nyasaplayer.auto.ui.theme.CarOutline
import com.example.nyasaplayer.auto.ui.theme.CarPillButtonHeight
import com.example.nyasaplayer.auto.ui.theme.CarRaised
import com.example.nyasaplayer.core.common.ui.theme.NyasaGold
import com.example.nyasaplayer.core.common.ui.theme.NyasaOnGold

private val ChipPadding = 28.dp
private val ButtonPadding = 36.dp

/** Hoisted: the shape is constant, and this button is on nearly every screen. */
private val PillShape = RoundedCornerShape(CarPillButtonHeight / 2)
private val UnselectedBorderWidth = 1.dp
private val SelectedBorderWidth = 0.dp
private val ChipLabelSize = 19.sp
private val ButtonLabelSize = 20.sp
private val SectionLabelSize = 22.sp

/**
 * Filter chip. Selection is shown by gold fill and label colour only — never by
 * rendering the word "ACTIVE" or "SELECTED" as visible text.
 */
@Composable
fun CarChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .carTouchTarget()
            .height(CarChipHeight)
            .background(
                color = if (selected) NyasaGold else CarRaised,
                shape = RoundedCornerShape(CarChipHeight / 2),
            )
            .border(
                width = if (selected) SelectedBorderWidth else UnselectedBorderWidth,
                color = if (selected) {
                    Color.Transparent
                } else {
                    CarOutline
                },
                shape = RoundedCornerShape(CarChipHeight / 2),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = ChipPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) NyasaOnGold else Color.White,
            fontSize = ChipLabelSize,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

/**
 * Primary or secondary pill button.
 *
 * The gold variant uses [NyasaOnGold] for its label. Never white — white on gold
 * measures 2.29:1.
 */
@Composable
fun CarPillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
) {
    Box(
        modifier = modifier
            .carTouchTarget()
            .height(CarPillButtonHeight)
            .background(
                color = if (filled) NyasaGold else Color.Transparent,
                shape = PillShape,
            )
            .border(
                width = if (filled) SelectedBorderWidth else UnselectedBorderWidth,
                color = if (filled) Color.Transparent else CarOutline,
                shape = PillShape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = ButtonPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (filled) NyasaOnGold else Color.White,
            fontSize = ButtonLabelSize,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Section heading above a content row. */
@Composable
fun CarSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        modifier = modifier,
        color = Color.White,
        fontSize = SectionLabelSize,
        fontWeight = FontWeight.Bold,
    )
}
