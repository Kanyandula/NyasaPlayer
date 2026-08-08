package com.example.nyasaplayer.auto.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nyasaplayer.auto.ui.theme.CarCardCornerRadius
import com.example.nyasaplayer.auto.ui.theme.CarGlass
import com.example.nyasaplayer.auto.ui.theme.CarPillButtonHeight
import com.example.nyasaplayer.auto.ui.theme.CarTextSecondary
import com.example.nyasaplayer.core.common.ui.theme.NyasaGold
import com.example.nyasaplayer.core.common.ui.theme.NyasaOnGold

private const val ScrimAlpha = 0.74f
private val DialogWidth = 780.dp
private val DialogPadding = 44.dp
private val DialogSpacing = 16.dp
private val ButtonPadding = 36.dp
private val TitleSize = 40.sp
private val BodySize = 22.sp
private val ButtonLabelSize = 20.sp

/**
 * Shown when [com.example.nyasaplayer.auto.ui.navigation.gate] refuses a location, and
 * when the driver is evicted from one.
 *
 * Always carries a reason. A silent refusal reads as a broken app.
 */
@Composable
fun CarRestrictionDialog(
    reason: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = ScrimAlpha))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(DialogWidth)
                .background(CarGlass, RoundedCornerShape(CarCardCornerRadius))
                .padding(DialogPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DialogSpacing),
        ) {
            Text(
                text = "Not available while driving",
                color = Color.White,
                fontSize = TitleSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = reason,
                color = CarTextSecondary,
                fontSize = BodySize,
                textAlign = TextAlign.Center,
            )
            Box(
                modifier = Modifier
                    .carTouchTarget()
                    .height(CarPillButtonHeight)
                    .background(NyasaGold, RoundedCornerShape(CarPillButtonHeight / 2))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = ButtonPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Got it",
                    color = NyasaOnGold,
                    fontSize = ButtonLabelSize,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
