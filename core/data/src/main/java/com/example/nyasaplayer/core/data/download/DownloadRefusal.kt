package com.example.nyasaplayer.core.data.download

/** Why a download never started. */
enum class DownloadRefusal {
    /** No network, and a download is the one thing that cannot wait for one. */
    Offline,

    /** The song is not in the catalogue, or carries no audio URL. */
    Unavailable,
}
