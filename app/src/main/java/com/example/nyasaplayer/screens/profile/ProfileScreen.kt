package com.example.nyasaplayer.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.nyasaplayer.R
import com.example.nyasaplayer.ui.components.ErrorBanner
import com.example.nyasaplayer.ui.icons.ChevronRightIcon
import com.example.nyasaplayer.ui.icons.HeartIcon
import com.example.nyasaplayer.ui.icons.ProfileIcon
import com.example.nyasaplayer.ui.icons.SettingsIcon
import com.example.nyasaplayer.ui.icons.VolumeIcon
import com.example.nyasaplayer.ui.theme.NyasaPrimary
import com.example.nyasaplayer.ui.theme.NyasaSurface3
import com.example.nyasaplayer.ui.theme.NyasaTextSecondary

private val SignOutRed = Color(0xFFEF5350)

@Composable
fun ProfileScreen(
    onSignOut: () -> Unit,
    onLikedSongsClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(top = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.profile),
            style = MaterialTheme.typography.headlineLarge,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        ProfileHeader(
            displayName = uiState.displayName,
            accountType = uiState.accountType,
            photoUrl = uiState.photoUrl,
        )
        if (uiState.errorMessage != null) {
            Spacer(modifier = Modifier.height(16.dp))
            ErrorBanner(
                title = stringResource(R.string.error_connection_lost),
                subtitle = stringResource(R.string.error_showing_cached),
                onRetry = viewModel::retry,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        ProfileMenuItems(
            onLikedSongsClick = onLikedSongsClick,
            onSignOut = {
                viewModel.signOut()
                onSignOut()
            },
        )
    }
}

@Composable
private fun ProfileMenuItems(
    onLikedSongsClick: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        ProfileMenuItem(
            icon = HeartIcon,
            label = stringResource(R.string.liked_songs),
            iconTint = NyasaPrimary,
            onClick = onLikedSongsClick,
        )
        ProfileMenuItem(icon = VolumeIcon, label = stringResource(R.string.audio_quality))
        ProfileMenuItem(icon = SettingsIcon, label = stringResource(R.string.settings))
        ProfileMenuItem(icon = ProfileIcon, label = stringResource(R.string.about))
        Spacer(modifier = Modifier.height(16.dp))
        ProfileMenuItem(
            icon = Icons.AutoMirrored.Filled.ExitToApp,
            label = stringResource(R.string.sign_out),
            iconTint = SignOutRed,
            onClick = onSignOut,
        )
    }
}

@Composable
private fun ProfileHeader(
    displayName: String,
    accountType: String,
    photoUrl: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (photoUrl.isNotBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(NyasaSurface3),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = ProfileIcon,
                    contentDescription = null,
                    tint = NyasaTextSecondary,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = displayName.ifBlank { "Music Lover" },
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
            )
            Text(
                text = accountType.replaceFirstChar { it.uppercase() } + " Account",
                style = MaterialTheme.typography.bodySmall,
                color = NyasaTextSecondary,
            )
        }
    }
}

@Composable
private fun ProfileMenuItem(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    iconTint: Color = NyasaTextSecondary,
    onClick: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = ChevronRightIcon,
            contentDescription = null,
            tint = NyasaTextSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}
