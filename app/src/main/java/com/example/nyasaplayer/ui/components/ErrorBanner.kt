package com.example.nyasaplayer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.nyasaplayer.ui.icons.WarningIcon
import com.example.nyasaplayer.ui.theme.NyasaError
import com.example.nyasaplayer.ui.theme.NyasaTextSecondary

private val BannerCornerRadius = 12.dp
private val WarningIconSize = 20.dp
private const val ErrorBackgroundAlpha = 0.15f
private const val ErrorBorderAlpha = 0.3f

@Composable
fun ErrorBanner(
    title: String,
    subtitle: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(BannerCornerRadius)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(NyasaError.copy(alpha = ErrorBackgroundAlpha), shape)
            .border(1.dp, NyasaError.copy(alpha = ErrorBorderAlpha), shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = WarningIcon,
            contentDescription = null,
            tint = NyasaError,
            modifier = Modifier.size(WarningIconSize),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = NyasaError,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = NyasaTextSecondary,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedButton(
            onClick = onRetry,
            border = BorderStroke(1.dp, NyasaError),
        ) {
            Text(
                text = "Retry",
                color = NyasaError,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
