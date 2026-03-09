package com.example.nyasaplayer.auto.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nyasaplayer.core.common.ui.icons.RefreshIcon
import com.example.nyasaplayer.core.common.ui.icons.WifiOffIcon
import com.example.nyasaplayer.core.common.ui.theme.NyasaPrimary
import com.example.nyasaplayer.core.common.ui.theme.NyasaPrimaryDark
import com.example.nyasaplayer.core.common.ui.theme.NyasaSurface2
import com.example.nyasaplayer.core.common.ui.theme.NyasaTextSecondary
import com.example.nyasaplayer.core.playback.PlayerError

private val IconCircleSize = 128.dp
private val IconSize = 64.dp
private val ErrorIconGradientStart = Color(0xFFF97316)
private val ErrorIconGradientEnd = Color(0xFFEF4444)
private const val BackdropAlpha = 0.8f
private const val ModalWidthFraction = 0.5f

@Composable
fun CarErrorOverlay(
    error: PlayerError,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = BackdropAlpha))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        ErrorModalCard(error = error, onDismiss = onDismiss, onRetry = onRetry)
    }
}

@Composable
private fun ErrorModalCard(
    error: PlayerError,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth(ModalWidthFraction)
            .clip(RoundedCornerShape(24.dp))
            .background(NyasaSurface2)
            .clickable(enabled = false, onClick = {})
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ErrorIcon()
        Spacer(modifier = Modifier.height(32.dp))
        ErrorText(title = error.title, message = error.message)
        Spacer(modifier = Modifier.height(32.dp))
        ErrorActions(onDismiss = onDismiss, onRetry = onRetry)
    }
}

@Composable
private fun ErrorIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(IconCircleSize)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(ErrorIconGradientStart, ErrorIconGradientEnd))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(WifiOffIcon, null, tint = Color.White, modifier = Modifier.size(IconSize))
    }
}

@Composable
private fun ErrorText(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            color = NyasaTextSecondary,
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
            lineHeight = 28.sp,
        )
    }
}

@Composable
private fun ErrorActions(
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.1f))
                .clickable(onClick = onDismiss)
                .padding(vertical = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Dismiss",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.horizontalGradient(listOf(NyasaPrimary, NyasaPrimaryDark)))
                .clickable(onClick = onRetry)
                .padding(vertical = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(RefreshIcon, null, tint = Color.White, modifier = Modifier.size(24.dp))
                Text(
                    text = "Retry",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
