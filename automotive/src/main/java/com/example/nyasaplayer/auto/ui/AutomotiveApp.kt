package com.example.nyasaplayer.auto.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import com.example.nyasaplayer.auto.viewmodel.AutomotivePlayerViewModel
import com.example.nyasaplayer.core.common.ui.theme.NyasaBackground
import com.example.nyasaplayer.core.common.ui.theme.NyasaPrimary

@UnstableApi
@Composable
fun AutomotiveApp(
    modifier: Modifier = Modifier,
    viewModel: AutomotivePlayerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NyasaBackground),
        contentAlignment = Alignment.Center,
    ) {
        val song = uiState.playback.currentSong
        if (song != null) {
            Text(
                text = "${song.title} — ${song.resolvedArtistName}",
                color = NyasaPrimary,
            )
        } else {
            Text(
                text = "NyasaPlayer Automotive",
                color = NyasaPrimary,
            )
        }
    }
}
