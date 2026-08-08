package com.example.nyasaplayer.auto.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nyasaplayer.auto.ui.theme.CarTextSecondary

private val Spacing = 12.dp
private val TitleSize = 40.sp
private val BodySize = 22.sp

/**
 * Empty-state block: title, explanation, and an optional action.
 *
 * Pass [actionLabel] and [onAction] together or not at all.
 */
@Composable
fun CarEmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing, Alignment.CenterVertically),
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = TitleSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            color = CarTextSecondary,
            fontSize = BodySize,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            CarPillButton(label = actionLabel, onClick = onAction)
        }
    }
}
