@file:Suppress("MatchingDeclarationName")

package com.example.nyasaplayer.screens.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.nyasaplayer.R
import com.example.nyasaplayer.core.common.models.Playlist
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.common.ui.components.CreatePlaylistDialog
import com.example.nyasaplayer.core.common.ui.components.PlaylistPickerSheet
import com.example.nyasaplayer.download.SongDownloadManager
import kotlinx.coroutines.flow.Flow

class OverflowDialogState {
    var overflowSong: Song? by mutableStateOf(null)
        private set
    var playlistPickerSong: Song? by mutableStateOf(null)
        private set
    var showCreateDialog: Boolean by mutableStateOf(false)
        private set
    var createDialogSongId: String by mutableStateOf("")
        private set
    var removalPickerSong: Song? by mutableStateOf(null)
        private set

    fun openOverflow(song: Song) {
        overflowSong = song
    }

    fun openPlaylistPicker(song: Song) {
        overflowSong = null
        playlistPickerSong = song
    }

    fun openCreateDialog(songId: String) {
        playlistPickerSong = null
        createDialogSongId = songId
        showCreateDialog = true
    }

    fun openRemovalPicker(song: Song) {
        overflowSong = null
        removalPickerSong = song
    }

    fun dismissOverflow() {
        overflowSong = null
    }

    fun dismissPlaylistPicker() {
        playlistPickerSong = null
    }

    fun dismissCreateDialog() {
        showCreateDialog = false
    }

    fun dismissRemovalPicker() {
        removalPickerSong = null
    }
}

@Composable
fun rememberOverflowDialogState(): OverflowDialogState = remember { OverflowDialogState() }

@Suppress("LongParameterList", "LongMethod")
@Composable
fun SongOverflowDialogs(
    state: OverflowDialogState,
    downloadManager: SongDownloadManager?,
    playlists: List<Playlist>,
    onAddToPlaylist: (playlistId: String, mediaId: String) -> Unit,
    onCreatePlaylistAndAdd: (name: String, mediaId: String) -> Unit,
    modifier: Modifier = Modifier,
    isLikedFlow: ((String) -> Flow<Boolean>)? = null,
    onToggleLike: ((String) -> Unit)? = null,
    onRemoveFromPlaylist: ((playlistId: String, mediaId: String) -> Unit)? = null,
    onDirectRemove: ((String) -> Unit)? = null,
) {
    @Suppress("UNUSED_PARAMETER")
    val unused = modifier

    state.overflowSong?.let { song ->
        val isLiked by if (isLikedFlow != null) {
            remember(song.mediaId) { isLikedFlow(song.mediaId) }
                .collectAsState(initial = false)
        } else {
            remember { mutableStateOf(false) }
        }
        val showRemove = onRemoveFromPlaylist != null || onDirectRemove != null
        val isInAnyPlaylist = if (onRemoveFromPlaylist != null) {
            playlists.any { song.mediaId in it.songIds }
        } else {
            showRemove
        }
        SongOverflowWithDownload(
            song = song,
            downloadManager = downloadManager,
            onDismiss = { state.dismissOverflow() },
            isLiked = isLiked,
            onToggleLike = {
                state.dismissOverflow()
                onToggleLike?.invoke(song.mediaId)
            },
            onSaveToPlaylist = { state.openPlaylistPicker(song) },
            showRemoveFromPlaylist = isInAnyPlaylist,
            onRemoveFromPlaylist = {
                if (onDirectRemove != null) {
                    state.dismissOverflow()
                    onDirectRemove(song.mediaId)
                } else {
                    state.openRemovalPicker(song)
                }
            },
        )
    }

    state.playlistPickerSong?.let { song ->
        PlaylistPickerSheet(
            playlists = playlists,
            onSelectPlaylist = { playlist ->
                state.dismissPlaylistPicker()
                onAddToPlaylist(playlist.id, song.mediaId)
            },
            onCreateNew = { state.openCreateDialog(song.mediaId) },
            onDismiss = { state.dismissPlaylistPicker() },
        )
    }

    if (state.showCreateDialog) {
        CreatePlaylistDialog(
            onConfirm = { name ->
                val songId = state.createDialogSongId
                state.dismissCreateDialog()
                onCreatePlaylistAndAdd(name, songId)
            },
            onDismiss = { state.dismissCreateDialog() },
        )
    }

    if (onRemoveFromPlaylist != null) {
        state.removalPickerSong?.let { song ->
            val containingPlaylists = playlists.filter { song.mediaId in it.songIds }
            PlaylistPickerSheet(
                playlists = containingPlaylists,
                onSelectPlaylist = { playlist ->
                    state.dismissRemovalPicker()
                    onRemoveFromPlaylist(playlist.id, song.mediaId)
                },
                onDismiss = { state.dismissRemovalPicker() },
                title = stringResource(R.string.remove_from_playlist),
                showCreateNew = false,
            )
        }
    }
}
