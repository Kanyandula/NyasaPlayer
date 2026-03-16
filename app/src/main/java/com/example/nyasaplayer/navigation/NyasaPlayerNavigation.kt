package com.example.nyasaplayer.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.download.SongDownloadManager
import com.example.nyasaplayer.screens.downloads.DownloadsScreen
import com.example.nyasaplayer.screens.home.HomeScreen
import com.example.nyasaplayer.screens.library.LibraryScreen
import com.example.nyasaplayer.screens.profile.ProfileScreen
import com.example.nyasaplayer.screens.search.SearchScreen

fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(HomeRoute) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

const val HomeRoute = "home"
const val SearchRoute = "search"
const val LibraryRoute = "library"
const val ProfileRoute = "profile"
const val DownloadsRoute = "downloads"

@Composable
fun NyasaPlayerNavHost(
    navController: NavHostController,
    onSongClick: (List<Song>, Song) -> Unit,
    onShufflePlay: (List<Song>) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    currentlyPlayingMediaId: String? = null,
    isCurrentlyPlaying: Boolean = false,
    downloadManager: SongDownloadManager? = null,
) {
    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        modifier = modifier,
    ) {
        composable(HomeRoute) {
            HomeScreen(
                onSongClick = onSongClick,
                onProfileClick = { navController.navigateToTab(ProfileRoute) },
                currentlyPlayingMediaId = currentlyPlayingMediaId,
                isCurrentlyPlaying = isCurrentlyPlaying,
            )
        }
        composable(SearchRoute) {
            SearchScreen(
                onSongClick = onSongClick,
                downloadManager = downloadManager,
            )
        }
        composable(LibraryRoute) {
            LibraryScreen(
                onSongClick = onSongClick,
                onShufflePlay = onShufflePlay,
                currentlyPlayingMediaId = currentlyPlayingMediaId,
                isCurrentlyPlaying = isCurrentlyPlaying,
                downloadManager = downloadManager,
            )
        }
        composable(ProfileRoute) {
            ProfileScreen(
                onSignOut = onSignOut,
                onLikedSongsClick = { navController.navigateToTab(LibraryRoute) },
                onDownloadsClick = { navController.navigate(DownloadsRoute) },
            )
        }
        composable(DownloadsRoute) {
            DownloadsScreen(
                onSongClick = onSongClick,
                onShufflePlay = onShufflePlay,
                onBack = { navController.popBackStack() },
                currentlyPlayingMediaId = currentlyPlayingMediaId,
                isCurrentlyPlaying = isCurrentlyPlaying,
            )
        }
    }
}
