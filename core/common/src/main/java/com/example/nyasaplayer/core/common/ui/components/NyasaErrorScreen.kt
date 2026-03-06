package com.example.nyasaplayer.core.common.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.nyasaplayer.core.common.ui.icons.CloudOffIcon
import com.example.nyasaplayer.core.common.ui.icons.RefreshIcon
import com.example.nyasaplayer.core.common.ui.icons.WifiOffIcon
import com.example.nyasaplayer.core.common.ui.theme.NyasaPrimary
import com.example.nyasaplayer.core.common.ui.theme.NyasaPrimaryDark
import com.example.nyasaplayer.core.common.ui.theme.NyasaSurface3
import com.example.nyasaplayer.core.common.ui.theme.NyasaTextSecondary
import com.example.nyasaplayer.core.common.ui.theme.NyasaTextTertiary

private val IconCircleSize = 80.dp
private val IconSize = 40.dp
private val ButtonHeight = 52.dp
private val ButtonCornerRadius = 24.dp
private val RefreshIconSize = 18.dp

@Composable
fun NyasaErrorScreen(
    title: String,
    subtitle: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    isNetworkError: Boolean = true,
    buttonText: String = "Try Again",
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ErrorIconCircle(isNetworkError = isNetworkError)
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = NyasaTextTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Spacer(modifier = Modifier.height(32.dp))
            GradientRetryButton(text = buttonText, onClick = onRetry)
        }
    }
}

@Composable
private fun ErrorIconCircle(
    isNetworkError: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(IconCircleSize)
            .background(NyasaSurface3, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isNetworkError) WifiOffIcon else CloudOffIcon,
            contentDescription = null,
            tint = NyasaTextSecondary,
            modifier = Modifier.size(IconSize),
        )
    }
}

@Composable
private fun GradientRetryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(ButtonHeight),
        shape = RoundedCornerShape(ButtonCornerRadius),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        contentPadding = PaddingValues(),
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(listOf(NyasaPrimaryDark, NyasaPrimary)),
                    RoundedCornerShape(ButtonCornerRadius),
                )
                .padding(horizontal = 32.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = RefreshIcon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(RefreshIconSize),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }
        }
    }
}
