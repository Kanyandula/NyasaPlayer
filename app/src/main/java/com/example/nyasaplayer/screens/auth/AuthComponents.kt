package com.example.nyasaplayer.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nyasaplayer.core.common.ui.icons.MusicNoteIcon
import com.example.nyasaplayer.core.common.ui.theme.NyasaPrimary
import com.example.nyasaplayer.core.common.ui.theme.NyasaPrimaryDark
import com.example.nyasaplayer.core.common.ui.theme.NyasaSurface2
import com.example.nyasaplayer.core.common.ui.theme.NyasaSurface4
import com.example.nyasaplayer.core.common.ui.theme.NyasaTextSecondary

internal const val PasswordPlaceholder = "\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022"

@Composable
internal fun authTextFieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedContainerColor = NyasaSurface2,
    focusedContainerColor = NyasaSurface2,
    unfocusedBorderColor = NyasaSurface4,
    focusedBorderColor = NyasaPrimary,
    cursorColor = NyasaPrimary,
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
)

@Composable
internal fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = NyasaTextSecondary,
        letterSpacing = 1.sp,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
internal fun AppIconWithGlow(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(80.dp)
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = NyasaPrimary.copy(alpha = 0.3f),
                spotColor = NyasaPrimary.copy(alpha = 0.3f),
            )
            .clip(RoundedCornerShape(20.dp))
            .background(NyasaPrimaryDark),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = MusicNoteIcon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(40.dp),
        )
    }
}

@Composable
internal fun PrimaryGradientButton(
    text: String,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = !isLoading,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        contentPadding = PaddingValues(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(listOf(NyasaPrimaryDark, NyasaPrimary)),
                    RoundedCornerShape(12.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = text,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Color.White,
                )
            }
        }
    }
}
