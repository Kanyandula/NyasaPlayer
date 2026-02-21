package com.example.nyasaplayer.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val MusicNoteIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "MusicNote",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(12f, 3f)
            verticalLineTo(15.55f)
            curveTo(11.41f, 15.21f, 10.73f, 15f, 10f, 15f)
            curveTo(7.79f, 15f, 6f, 16.79f, 6f, 19f)
            curveTo(6f, 21.21f, 7.79f, 23f, 10f, 23f)
            curveTo(12.21f, 23f, 14f, 21.21f, 14f, 19f)
            verticalLineTo(7f)
            horizontalLineTo(18f)
            verticalLineTo(3f)
            close()
        }
    }.build()
}

val EmailIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Email",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(20f, 4f)
            horizontalLineTo(4f)
            curveTo(2.9f, 4f, 2.01f, 4.9f, 2.01f, 6f)
            lineTo(2f, 18f)
            curveTo(2f, 19.1f, 2.9f, 20f, 4f, 20f)
            horizontalLineTo(20f)
            curveTo(21.1f, 20f, 22f, 19.1f, 22f, 18f)
            verticalLineTo(6f)
            curveTo(22f, 4.9f, 21.1f, 4f, 20f, 4f)
            close()
            moveTo(20f, 8f)
            lineTo(12f, 13f)
            lineTo(4f, 8f)
            verticalLineTo(6f)
            lineTo(12f, 11f)
            lineTo(20f, 6f)
            close()
        }
    }.build()
}

val LockIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Lock",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(18f, 8f)
            horizontalLineTo(17f)
            verticalLineTo(6f)
            curveTo(17f, 3.24f, 14.76f, 1f, 12f, 1f)
            curveTo(9.24f, 1f, 7f, 3.24f, 7f, 6f)
            verticalLineTo(8f)
            horizontalLineTo(6f)
            curveTo(4.9f, 8f, 4f, 8.9f, 4f, 10f)
            verticalLineTo(20f)
            curveTo(4f, 21.1f, 4.9f, 22f, 6f, 22f)
            horizontalLineTo(18f)
            curveTo(19.1f, 22f, 20f, 21.1f, 20f, 20f)
            verticalLineTo(10f)
            curveTo(20f, 8.9f, 19.1f, 8f, 18f, 8f)
            close()
            moveTo(12f, 17f)
            curveTo(10.9f, 17f, 10f, 16.1f, 10f, 15f)
            curveTo(10f, 13.9f, 10.9f, 13f, 12f, 13f)
            curveTo(13.1f, 13f, 14f, 13.9f, 14f, 15f)
            curveTo(14f, 16.1f, 13.1f, 17f, 12f, 17f)
            close()
            moveTo(15.1f, 8f)
            horizontalLineTo(8.9f)
            verticalLineTo(6f)
            curveTo(8.9f, 4.29f, 10.29f, 2.9f, 12f, 2.9f)
            curveTo(13.71f, 2.9f, 15.1f, 4.29f, 15.1f, 6f)
            verticalLineTo(8f)
            close()
        }
    }.build()
}

val PauseIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Pause",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(6f, 19f)
            horizontalLineToRelative(4f)
            verticalLineTo(5f)
            horizontalLineTo(6f)
            verticalLineTo(19f)
            close()
            moveTo(14f, 5f)
            verticalLineToRelative(14f)
            horizontalLineToRelative(4f)
            verticalLineTo(5f)
            horizontalLineTo(14f)
            close()
        }
    }.build()
}

val SkipPreviousIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "SkipPrevious",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(6f, 6f)
            horizontalLineTo(8f)
            verticalLineTo(18f)
            horizontalLineTo(6f)
            close()
            moveTo(9.5f, 12f)
            lineTo(18f, 6f)
            verticalLineTo(18f)
            close()
        }
    }.build()
}

val SkipNextIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "SkipNext",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(6f, 18f)
            lineTo(14.5f, 12f)
            lineTo(6f, 6f)
            close()
            moveTo(16f, 6f)
            horizontalLineTo(18f)
            verticalLineTo(18f)
            horizontalLineTo(16f)
            close()
        }
    }.build()
}

val ShuffleIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Shuffle",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(10.59f, 9.17f)
            lineTo(5.41f, 4f)
            lineTo(4f, 5.41f)
            lineTo(9.17f, 10.59f)
            close()
            moveTo(14.5f, 4f)
            lineTo(16.89f, 6.39f)
            lineTo(4f, 19.29f)
            lineTo(5.41f, 20.7f)
            lineTo(18.29f, 7.82f)
            lineTo(20.7f, 10.22f)
            verticalLineTo(4f)
            close()
            moveTo(14.83f, 13.41f)
            lineTo(13.41f, 14.83f)
            lineTo(16.89f, 18.31f)
            lineTo(14.5f, 20.7f)
            horizontalLineTo(20.7f)
            verticalLineTo(14.5f)
            lineTo(18.29f, 16.89f)
            close()
        }
    }.build()
}

val RepeatIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Repeat",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(7f, 7f)
            horizontalLineTo(17f)
            verticalLineTo(10f)
            lineTo(21f, 6f)
            lineTo(17f, 2f)
            verticalLineTo(5f)
            horizontalLineTo(5f)
            curveTo(3.9f, 5f, 3f, 5.9f, 3f, 7f)
            verticalLineTo(13f)
            horizontalLineTo(5f)
            verticalLineTo(7f)
            close()
            moveTo(17f, 17f)
            horizontalLineTo(7f)
            verticalLineTo(14f)
            lineTo(3f, 18f)
            lineTo(7f, 22f)
            verticalLineTo(19f)
            horizontalLineTo(19f)
            curveTo(20.1f, 19f, 21f, 18.1f, 21f, 17f)
            verticalLineTo(11f)
            horizontalLineTo(19f)
            verticalLineTo(17f)
            close()
        }
    }.build()
}

val RepeatOneIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "RepeatOne",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            // Repeat arrows
            moveTo(7f, 7f)
            horizontalLineTo(17f)
            verticalLineTo(10f)
            lineTo(21f, 6f)
            lineTo(17f, 2f)
            verticalLineTo(5f)
            horizontalLineTo(5f)
            curveTo(3.9f, 5f, 3f, 5.9f, 3f, 7f)
            verticalLineTo(13f)
            horizontalLineTo(5f)
            verticalLineTo(7f)
            close()
            moveTo(17f, 17f)
            horizontalLineTo(7f)
            verticalLineTo(14f)
            lineTo(3f, 18f)
            lineTo(7f, 22f)
            verticalLineTo(19f)
            horizontalLineTo(19f)
            curveTo(20.1f, 19f, 21f, 18.1f, 21f, 17f)
            verticalLineTo(11f)
            horizontalLineTo(19f)
            verticalLineTo(17f)
            close()
            // "1" in the center
            moveTo(13f, 15f)
            verticalLineTo(9f)
            horizontalLineTo(12f)
            lineTo(11f, 10f)
            verticalLineTo(11f)
            horizontalLineTo(12f)
            verticalLineTo(15f)
            close()
        }
    }.build()
}

val VolumeIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "VolumeUp",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(3f, 9f)
            verticalLineTo(15f)
            horizontalLineTo(7f)
            lineTo(12f, 20f)
            verticalLineTo(4f)
            lineTo(7f, 9f)
            close()
            moveTo(16.5f, 12f)
            curveTo(16.5f, 10.23f, 15.48f, 8.71f, 14f, 7.97f)
            verticalLineTo(16.02f)
            curveTo(15.48f, 15.29f, 16.5f, 13.77f, 16.5f, 12f)
            close()
            moveTo(14f, 3.23f)
            verticalLineTo(5.29f)
            curveTo(16.89f, 6.15f, 19f, 8.83f, 19f, 12f)
            curveTo(19f, 15.17f, 16.89f, 17.85f, 14f, 18.71f)
            verticalLineTo(20.77f)
            curveTo(18f, 19.86f, 21f, 16.28f, 21f, 12f)
            curveTo(21f, 7.72f, 18f, 4.14f, 14f, 3.23f)
            close()
        }
    }.build()
}

val LyricsIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Lyrics",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(12f, 3f)
            curveTo(10.34f, 3f, 9f, 4.37f, 9f, 6.07f)
            verticalLineTo(11.93f)
            curveTo(9f, 13.63f, 10.34f, 15f, 12f, 15f)
            curveTo(13.66f, 15f, 15f, 13.63f, 15f, 11.93f)
            verticalLineTo(6.07f)
            curveTo(15f, 4.37f, 13.66f, 3f, 12f, 3f)
            close()
            moveTo(17.91f, 12f)
            curveTo(17.91f, 15.1f, 15.41f, 17.43f, 12.5f, 17.92f)
            verticalLineTo(21f)
            horizontalLineTo(11.5f)
            verticalLineTo(17.92f)
            curveTo(8.59f, 17.43f, 6.09f, 15.1f, 6.09f, 12f)
            horizontalLineTo(5f)
            curveTo(5f, 15.53f, 7.72f, 18.47f, 11f, 18.93f)
            verticalLineTo(21f)
            horizontalLineTo(13f)
            verticalLineTo(18.93f)
            curveTo(16.28f, 18.47f, 19f, 15.53f, 19f, 12f)
            close()
        }
    }.build()
}

val HomeIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Home",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(10f, 20f)
            verticalLineTo(14f)
            horizontalLineTo(14f)
            verticalLineTo(20f)
            horizontalLineTo(19f)
            verticalLineTo(12f)
            horizontalLineTo(22f)
            lineTo(12f, 3f)
            lineTo(2f, 12f)
            horizontalLineTo(5f)
            verticalLineTo(20f)
            close()
        }
    }.build()
}

val SearchIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Search",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(15.5f, 14f)
            horizontalLineTo(14.71f)
            lineTo(14.43f, 13.73f)
            curveTo(15.41f, 12.59f, 16f, 11.11f, 16f, 9.5f)
            curveTo(16f, 5.91f, 13.09f, 3f, 9.5f, 3f)
            curveTo(5.91f, 3f, 3f, 5.91f, 3f, 9.5f)
            curveTo(3f, 13.09f, 5.91f, 16f, 9.5f, 16f)
            curveTo(11.11f, 16f, 12.59f, 15.41f, 13.73f, 14.43f)
            lineTo(14f, 14.71f)
            verticalLineTo(15.5f)
            lineTo(19f, 20.49f)
            lineTo(20.49f, 19f)
            close()
            moveTo(9.5f, 14f)
            curveTo(7.01f, 14f, 5f, 11.99f, 5f, 9.5f)
            curveTo(5f, 7.01f, 7.01f, 5f, 9.5f, 5f)
            curveTo(11.99f, 5f, 14f, 7.01f, 14f, 9.5f)
            curveTo(14f, 11.99f, 11.99f, 14f, 9.5f, 14f)
            close()
        }
    }.build()
}

val LibraryIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Library",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            // Three vertical bars matching the design's equalizer icon
            moveTo(4f, 6f)
            horizontalLineTo(6f)
            verticalLineTo(18f)
            horizontalLineTo(4f)
            close()
            moveTo(11f, 4f)
            horizontalLineTo(13f)
            verticalLineTo(20f)
            horizontalLineTo(11f)
            close()
            moveTo(18f, 8f)
            horizontalLineTo(20f)
            verticalLineTo(16f)
            horizontalLineTo(18f)
            close()
        }
    }.build()
}

val ProfileIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Profile",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            // Head circle
            moveTo(12f, 4f)
            curveTo(13.66f, 4f, 15f, 5.34f, 15f, 7f)
            curveTo(15f, 8.66f, 13.66f, 10f, 12f, 10f)
            curveTo(10.34f, 10f, 9f, 8.66f, 9f, 7f)
            curveTo(9f, 5.34f, 10.34f, 4f, 12f, 4f)
            close()
            // Body arc
            moveTo(12f, 12f)
            curveTo(9.33f, 12f, 4f, 13.34f, 4f, 16f)
            verticalLineTo(18f)
            horizontalLineTo(20f)
            verticalLineTo(16f)
            curveTo(20f, 13.34f, 14.67f, 12f, 12f, 12f)
            close()
        }
    }.build()
}

val ChevronRightIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ChevronRight",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(10f, 6f)
            lineTo(8.59f, 7.41f)
            lineTo(13.17f, 12f)
            lineTo(8.59f, 16.59f)
            lineTo(10f, 18f)
            lineTo(16f, 12f)
            close()
        }
    }.build()
}

val NotificationIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Notification",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(12f, 22f)
            curveTo(13.1f, 22f, 14f, 21.1f, 14f, 20f)
            horizontalLineTo(10f)
            curveTo(10f, 21.1f, 10.9f, 22f, 12f, 22f)
            close()
            moveTo(18f, 16f)
            verticalLineTo(11f)
            curveTo(18f, 7.93f, 16.37f, 5.36f, 13.5f, 4.68f)
            verticalLineTo(4f)
            curveTo(13.5f, 3.17f, 12.83f, 2.5f, 12f, 2.5f)
            curveTo(11.17f, 2.5f, 10.5f, 3.17f, 10.5f, 4f)
            verticalLineTo(4.68f)
            curveTo(7.64f, 5.36f, 6f, 7.92f, 6f, 11f)
            verticalLineTo(16f)
            lineTo(4f, 18f)
            verticalLineTo(19f)
            horizontalLineTo(20f)
            verticalLineTo(18f)
            close()
        }
    }.build()
}

val HeartIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Heart",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(12f, 21.35f)
            lineTo(10.55f, 20.03f)
            curveTo(5.4f, 15.36f, 2f, 12.28f, 2f, 8.5f)
            curveTo(2f, 5.42f, 4.42f, 3f, 7.5f, 3f)
            curveTo(9.24f, 3f, 10.91f, 3.81f, 12f, 5.09f)
            curveTo(13.09f, 3.81f, 14.76f, 3f, 16.5f, 3f)
            curveTo(19.58f, 3f, 22f, 5.42f, 22f, 8.5f)
            curveTo(22f, 12.28f, 18.6f, 15.36f, 13.45f, 20.04f)
            close()
        }
    }.build()
}

val MoreVertIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "MoreVert",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            // Top dot
            moveTo(12f, 8f)
            curveTo(13.1f, 8f, 14f, 7.1f, 14f, 6f)
            curveTo(14f, 4.9f, 13.1f, 4f, 12f, 4f)
            curveTo(10.9f, 4f, 10f, 4.9f, 10f, 6f)
            curveTo(10f, 7.1f, 10.9f, 8f, 12f, 8f)
            close()
            // Middle dot
            moveTo(12f, 10f)
            curveTo(10.9f, 10f, 10f, 10.9f, 10f, 12f)
            curveTo(10f, 13.1f, 10.9f, 14f, 12f, 14f)
            curveTo(13.1f, 14f, 14f, 13.1f, 14f, 12f)
            curveTo(14f, 10.9f, 13.1f, 10f, 12f, 10f)
            close()
            // Bottom dot
            moveTo(12f, 16f)
            curveTo(10.9f, 16f, 10f, 16.9f, 10f, 18f)
            curveTo(10f, 19.1f, 10.9f, 20f, 12f, 20f)
            curveTo(13.1f, 20f, 14f, 19.1f, 14f, 18f)
            curveTo(14f, 16.9f, 13.1f, 16f, 12f, 16f)
            close()
        }
    }.build()
}

val SettingsIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Settings",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(19.14f, 12.94f)
            curveTo(19.18f, 12.64f, 19.2f, 12.33f, 19.2f, 12f)
            curveTo(19.2f, 11.68f, 19.18f, 11.36f, 19.13f, 11.06f)
            lineTo(21.16f, 9.48f)
            curveTo(21.34f, 9.34f, 21.39f, 9.07f, 21.28f, 8.87f)
            lineTo(19.36f, 5.55f)
            curveTo(19.24f, 5.33f, 18.99f, 5.26f, 18.77f, 5.33f)
            lineTo(16.38f, 6.29f)
            curveTo(15.88f, 5.91f, 15.35f, 5.59f, 14.76f, 5.35f)
            lineTo(14.4f, 2.81f)
            curveTo(14.36f, 2.57f, 14.16f, 2.4f, 13.92f, 2.4f)
            horizontalLineTo(10.08f)
            curveTo(9.84f, 2.4f, 9.65f, 2.57f, 9.61f, 2.81f)
            lineTo(9.25f, 5.35f)
            curveTo(8.66f, 5.59f, 8.12f, 5.92f, 7.63f, 6.29f)
            lineTo(5.24f, 5.33f)
            curveTo(5.02f, 5.25f, 4.77f, 5.33f, 4.65f, 5.55f)
            lineTo(2.74f, 8.87f)
            curveTo(2.62f, 9.08f, 2.66f, 9.34f, 2.86f, 9.48f)
            lineTo(4.89f, 11.06f)
            curveTo(4.84f, 11.36f, 4.8f, 11.69f, 4.8f, 12f)
            curveTo(4.8f, 12.31f, 4.82f, 12.64f, 4.87f, 12.94f)
            lineTo(2.84f, 14.52f)
            curveTo(2.66f, 14.66f, 2.61f, 14.93f, 2.72f, 15.13f)
            lineTo(4.64f, 18.45f)
            curveTo(4.76f, 18.67f, 5.01f, 18.74f, 5.23f, 18.67f)
            lineTo(7.62f, 17.71f)
            curveTo(8.12f, 18.09f, 8.65f, 18.41f, 9.24f, 18.65f)
            lineTo(9.6f, 21.19f)
            curveTo(9.65f, 21.43f, 9.84f, 21.6f, 10.08f, 21.6f)
            horizontalLineTo(13.92f)
            curveTo(14.16f, 21.6f, 14.36f, 21.43f, 14.39f, 21.19f)
            lineTo(14.75f, 18.65f)
            curveTo(15.34f, 18.41f, 15.88f, 18.09f, 16.37f, 17.71f)
            lineTo(18.76f, 18.67f)
            curveTo(18.98f, 18.75f, 19.23f, 18.67f, 19.35f, 18.45f)
            lineTo(21.27f, 15.13f)
            curveTo(21.39f, 14.91f, 21.34f, 14.66f, 21.15f, 14.52f)
            close()
            moveTo(12f, 15.6f)
            curveTo(10.02f, 15.6f, 8.4f, 13.98f, 8.4f, 12f)
            curveTo(8.4f, 10.02f, 10.02f, 8.4f, 12f, 8.4f)
            curveTo(13.98f, 8.4f, 15.6f, 10.02f, 15.6f, 12f)
            curveTo(15.6f, 13.98f, 13.98f, 15.6f, 12f, 15.6f)
            close()
        }
    }.build()
}

val MoreHorizIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "MoreHoriz",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            // Left dot
            moveTo(6f, 10f)
            curveTo(4.9f, 10f, 4f, 10.9f, 4f, 12f)
            curveTo(4f, 13.1f, 4.9f, 14f, 6f, 14f)
            curveTo(7.1f, 14f, 8f, 13.1f, 8f, 12f)
            curveTo(8f, 10.9f, 7.1f, 10f, 6f, 10f)
            close()
            // Middle dot
            moveTo(12f, 10f)
            curveTo(10.9f, 10f, 10f, 10.9f, 10f, 12f)
            curveTo(10f, 13.1f, 10.9f, 14f, 12f, 14f)
            curveTo(13.1f, 14f, 14f, 13.1f, 14f, 12f)
            curveTo(14f, 10.9f, 13.1f, 10f, 12f, 10f)
            close()
            // Right dot
            moveTo(18f, 10f)
            curveTo(16.9f, 10f, 16f, 10.9f, 16f, 12f)
            curveTo(16f, 13.1f, 16.9f, 14f, 18f, 14f)
            curveTo(19.1f, 14f, 20f, 13.1f, 20f, 12f)
            curveTo(20f, 10.9f, 19.1f, 10f, 18f, 10f)
            close()
        }
    }.build()
}

val PlayNextIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "PlayNext",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(16f, 13f)
            horizontalLineTo(2f)
            verticalLineTo(11f)
            horizontalLineTo(16f)
            close()
            moveTo(16f, 7f)
            horizontalLineTo(2f)
            verticalLineTo(5f)
            horizontalLineTo(16f)
            close()
            moveTo(2f, 19f)
            verticalLineTo(17f)
            horizontalLineTo(12f)
            verticalLineTo(19f)
            close()
            moveTo(19f, 13f)
            verticalLineTo(7f)
            horizontalLineTo(21f)
            verticalLineTo(13f)
            horizontalLineTo(24f)
            lineTo(20f, 17f)
            lineTo(16f, 13f)
            close()
        }
    }.build()
}

val RadioIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Radio",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(7.3f, 14.7f)
            lineTo(8.7f, 13.3f)
            curveTo(8.27f, 12.87f, 8f, 12.27f, 8f, 11.63f)
            curveTo(8f, 10.99f, 8.27f, 10.38f, 8.7f, 9.95f)
            lineTo(7.3f, 8.55f)
            curveTo(6.49f, 9.36f, 6f, 10.44f, 6f, 11.63f)
            curveTo(6f, 12.81f, 6.49f, 13.89f, 7.3f, 14.7f)
            close()
            moveTo(5.18f, 16.82f)
            lineTo(6.6f, 15.4f)
            curveTo(5.42f, 14.21f, 4.73f, 12.58f, 4.73f, 10.9f)
            curveTo(4.73f, 9.22f, 5.42f, 7.6f, 6.6f, 6.4f)
            lineTo(5.18f, 4.98f)
            curveTo(3.63f, 6.53f, 2.73f, 8.62f, 2.73f, 10.9f)
            curveTo(2.73f, 13.18f, 3.63f, 15.27f, 5.18f, 16.82f)
            close()
            moveTo(16.7f, 14.7f)
            curveTo(17.51f, 13.89f, 18f, 12.81f, 18f, 11.63f)
            curveTo(18f, 10.44f, 17.51f, 9.36f, 16.7f, 8.55f)
            lineTo(15.3f, 9.95f)
            curveTo(15.73f, 10.38f, 16f, 10.99f, 16f, 11.63f)
            curveTo(16f, 12.27f, 15.73f, 12.87f, 15.3f, 13.3f)
            close()
            moveTo(18.82f, 16.82f)
            curveTo(20.37f, 15.27f, 21.27f, 13.18f, 21.27f, 10.9f)
            curveTo(21.27f, 8.62f, 20.37f, 6.53f, 18.82f, 4.98f)
            lineTo(17.4f, 6.4f)
            curveTo(18.58f, 7.6f, 19.27f, 9.22f, 19.27f, 10.9f)
            curveTo(19.27f, 12.58f, 18.58f, 14.21f, 17.4f, 15.4f)
            close()
            moveTo(12f, 9.63f)
            curveTo(10.9f, 9.63f, 10f, 10.53f, 10f, 11.63f)
            curveTo(10f, 12.73f, 10.9f, 13.63f, 12f, 13.63f)
            curveTo(13.1f, 13.63f, 14f, 12.73f, 14f, 11.63f)
            curveTo(14f, 10.53f, 13.1f, 9.63f, 12f, 9.63f)
            close()
            moveTo(12f, 15f)
            verticalLineTo(21f)
            horizontalLineTo(10f)
            verticalLineTo(15.63f)
            curveTo(10.63f, 15.85f, 11.3f, 16f, 12f, 16f)
            close()
        }
    }.build()
}

val DownloadIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Download",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(19f, 9f)
            horizontalLineTo(15f)
            verticalLineTo(3f)
            horizontalLineTo(9f)
            verticalLineTo(9f)
            horizontalLineTo(5f)
            lineTo(12f, 16f)
            close()
            moveTo(5f, 18f)
            verticalLineTo(20f)
            horizontalLineTo(19f)
            verticalLineTo(18f)
            close()
        }
    }.build()
}

val PlaylistAddIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "PlaylistAdd",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(14f, 10f)
            horizontalLineTo(2f)
            verticalLineTo(12f)
            horizontalLineTo(14f)
            close()
            moveTo(14f, 6f)
            horizontalLineTo(2f)
            verticalLineTo(8f)
            horizontalLineTo(14f)
            close()
            moveTo(18f, 14f)
            verticalLineTo(10f)
            horizontalLineTo(16f)
            verticalLineTo(14f)
            horizontalLineTo(12f)
            verticalLineTo(16f)
            horizontalLineTo(16f)
            verticalLineTo(20f)
            horizontalLineTo(18f)
            verticalLineTo(16f)
            horizontalLineTo(22f)
            verticalLineTo(14f)
            close()
            moveTo(2f, 16f)
            horizontalLineTo(10f)
            verticalLineTo(14f)
            horizontalLineTo(2f)
            close()
        }
    }.build()
}

val PlaylistRemoveIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "PlaylistRemove",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(6f, 19f)
            curveTo(6f, 20.1f, 6.9f, 21f, 8f, 21f)
            horizontalLineTo(16f)
            curveTo(17.1f, 21f, 18f, 20.1f, 18f, 19f)
            verticalLineTo(7f)
            horizontalLineTo(6f)
            close()
            moveTo(19f, 4f)
            horizontalLineTo(15.5f)
            lineTo(14.5f, 3f)
            horizontalLineTo(9.5f)
            lineTo(8.5f, 4f)
            horizontalLineTo(5f)
            verticalLineTo(6f)
            horizontalLineTo(19f)
            close()
        }
    }.build()
}

val AlbumIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Album",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(12f, 2f)
            curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
            curveTo(2f, 17.52f, 6.48f, 22f, 12f, 22f)
            curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
            curveTo(22f, 6.48f, 17.52f, 2f, 12f, 2f)
            close()
            moveTo(12f, 16.5f)
            curveTo(9.51f, 16.5f, 7.5f, 14.49f, 7.5f, 12f)
            curveTo(7.5f, 9.51f, 9.51f, 7.5f, 12f, 7.5f)
            curveTo(14.49f, 7.5f, 16.5f, 9.51f, 16.5f, 12f)
            curveTo(16.5f, 14.49f, 14.49f, 16.5f, 12f, 16.5f)
            close()
            moveTo(12f, 10f)
            curveTo(10.9f, 10f, 10f, 10.9f, 10f, 12f)
            curveTo(10f, 13.1f, 10.9f, 14f, 12f, 14f)
            curveTo(13.1f, 14f, 14f, 13.1f, 14f, 12f)
            curveTo(14f, 10.9f, 13.1f, 10f, 12f, 10f)
            close()
        }
    }.build()
}

val WifiOffIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "WifiOff",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(22.99f, 9f)
            curveTo(19.15f, 5.16f, 13.8f, 3.76f, 8.84f, 4.78f)
            lineTo(11.28f, 7.22f)
            curveTo(14.81f, 6.84f, 18.43f, 7.97f, 21.12f, 10.34f)
            close()
            moveTo(18.11f, 13f)
            curveTo(16.76f, 11.98f, 15.15f, 11.37f, 13.46f, 11.22f)
            lineTo(17.73f, 15.49f)
            close()
            moveTo(1f, 9f)
            lineTo(3f, 11f)
            curveTo(3.73f, 10.27f, 4.54f, 9.64f, 5.4f, 9.11f)
            lineTo(1f, 9f)
            close()
            moveTo(9f, 17f)
            lineTo(12f, 20f)
            lineTo(15f, 17f)
            curveTo(13.35f, 15.02f, 10.66f, 15.02f, 9f, 17f)
            close()
            moveTo(2.92f, 2.51f)
            lineTo(1.51f, 3.93f)
            lineTo(5.33f, 7.75f)
            curveTo(4.55f, 8.11f, 3.81f, 8.54f, 3.12f, 9.04f)
            lineTo(1.01f, 9f)
            curveTo(1.01f, 9f, 3f, 11f, 3f, 11f)
            lineTo(5.05f, 13f)
            curveTo(5.05f, 13f, 7.02f, 10.97f, 7.02f, 10.97f)
            lineTo(8.46f, 12.41f)
            curveTo(7.35f, 12.81f, 6.34f, 13.46f, 5.51f, 14.31f)
            lineTo(5.88f, 13f)
            curveTo(5.88f, 13f, 9f, 17f, 9f, 17f)
            lineTo(20.07f, 19.58f)
            lineTo(21.48f, 21.0f)
            lineTo(22.9f, 19.58f)
            lineTo(2.92f, 2.51f)
            close()
        }
    }.build()
}

val CloudOffIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "CloudOff",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(19.35f, 10.04f)
            curveTo(18.67f, 6.59f, 15.64f, 4f, 12f, 4f)
            curveTo(9.11f, 4f, 6.6f, 5.64f, 5.35f, 8.04f)
            curveTo(2.34f, 8.36f, 0f, 10.91f, 0f, 14f)
            curveTo(0f, 17.31f, 2.69f, 20f, 6f, 20f)
            horizontalLineTo(19f)
            curveTo(21.76f, 20f, 24f, 17.76f, 24f, 15f)
            curveTo(24f, 12.36f, 21.95f, 10.22f, 19.35f, 10.04f)
            close()
            moveTo(19f, 18f)
            horizontalLineTo(6f)
            curveTo(3.79f, 18f, 2f, 16.21f, 2f, 14f)
            curveTo(2f, 11.95f, 3.53f, 10.24f, 5.56f, 10.03f)
            lineTo(6.63f, 9.92f)
            lineTo(7.13f, 8.97f)
            curveTo(8.08f, 7.14f, 9.94f, 6f, 12f, 6f)
            curveTo(14.62f, 6f, 16.88f, 7.86f, 17.39f, 10.43f)
            lineTo(17.69f, 11.93f)
            lineTo(19.22f, 12.04f)
            curveTo(20.78f, 12.14f, 22f, 13.45f, 22f, 15f)
            curveTo(22f, 16.65f, 20.65f, 18f, 19f, 18f)
            close()
            // Diagonal slash
            moveTo(3f, 5.27f)
            lineTo(4.28f, 4f)
            lineTo(21f, 20.72f)
            lineTo(19.73f, 22f)
            close()
        }
    }.build()
}

val RefreshIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Refresh",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(17.65f, 6.35f)
            curveTo(16.2f, 4.9f, 14.21f, 4f, 12f, 4f)
            curveTo(7.58f, 4f, 4.01f, 7.58f, 4.01f, 12f)
            curveTo(4.01f, 16.42f, 7.58f, 20f, 12f, 20f)
            curveTo(15.73f, 20f, 18.84f, 17.45f, 19.73f, 14f)
            horizontalLineTo(17.65f)
            curveTo(16.83f, 16.33f, 14.61f, 18f, 12f, 18f)
            curveTo(8.69f, 18f, 6f, 15.31f, 6f, 12f)
            curveTo(6f, 8.69f, 8.69f, 6f, 12f, 6f)
            curveTo(13.66f, 6f, 15.14f, 6.69f, 16.22f, 7.78f)
            lineTo(13f, 11f)
            horizontalLineTo(20f)
            verticalLineTo(4f)
            close()
        }
    }.build()
}

val WarningIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Warning",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(1f, 21f)
            horizontalLineTo(23f)
            lineTo(12f, 2f)
            close()
            moveTo(13f, 18f)
            horizontalLineTo(11f)
            verticalLineTo(16f)
            horizontalLineTo(13f)
            close()
            moveTo(13f, 14f)
            horizontalLineTo(11f)
            verticalLineTo(10f)
            horizontalLineTo(13f)
            close()
        }
    }.build()
}

val VisibilityIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Visibility",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(12f, 4.5f)
            curveTo(7f, 4.5f, 2.73f, 7.61f, 1f, 12f)
            curveTo(2.73f, 16.39f, 7f, 19.5f, 12f, 19.5f)
            curveTo(17f, 19.5f, 21.27f, 16.39f, 23f, 12f)
            curveTo(21.27f, 7.61f, 17f, 4.5f, 12f, 4.5f)
            close()
            moveTo(12f, 17f)
            curveTo(9.24f, 17f, 7f, 14.76f, 7f, 12f)
            curveTo(7f, 9.24f, 9.24f, 7f, 12f, 7f)
            curveTo(14.76f, 7f, 17f, 9.24f, 17f, 12f)
            curveTo(17f, 14.76f, 14.76f, 17f, 12f, 17f)
            close()
            moveTo(12f, 9f)
            curveTo(10.34f, 9f, 9f, 10.34f, 9f, 12f)
            curveTo(9f, 13.66f, 10.34f, 15f, 12f, 15f)
            curveTo(13.66f, 15f, 15f, 13.66f, 15f, 12f)
            curveTo(15f, 10.34f, 13.66f, 9f, 12f, 9f)
            close()
        }
    }.build()
}

val VisibilityOffIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "VisibilityOff",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(12f, 7f)
            curveTo(14.76f, 7f, 17f, 9.24f, 17f, 12f)
            curveTo(17f, 12.65f, 16.87f, 13.26f, 16.64f, 13.83f)
            lineTo(19.56f, 16.75f)
            curveTo(21.07f, 15.49f, 22.26f, 13.86f, 23f, 12f)
            curveTo(21.27f, 7.61f, 17f, 4.5f, 12f, 4.5f)
            curveTo(10.6f, 4.5f, 9.26f, 4.75f, 8f, 5.2f)
            lineTo(10.17f, 7.36f)
            curveTo(10.74f, 7.13f, 11.35f, 7f, 12f, 7f)
            close()
            moveTo(2f, 4.27f)
            lineTo(4.28f, 6.55f)
            lineTo(4.74f, 7.01f)
            curveTo(3.08f, 8.3f, 1.78f, 10.02f, 1f, 12f)
            curveTo(2.73f, 16.39f, 7f, 19.5f, 12f, 19.5f)
            curveTo(13.55f, 19.5f, 15.03f, 19.2f, 16.38f, 18.66f)
            lineTo(16.81f, 19.08f)
            lineTo(19.73f, 22f)
            lineTo(21f, 20.73f)
            lineTo(3.27f, 3f)
            close()
            moveTo(7.53f, 9.8f)
            lineTo(9.08f, 11.35f)
            curveTo(9.03f, 11.56f, 9f, 11.78f, 9f, 12f)
            curveTo(9f, 13.66f, 10.34f, 15f, 12f, 15f)
            curveTo(12.22f, 15f, 12.44f, 14.97f, 12.65f, 14.92f)
            lineTo(14.2f, 16.47f)
            curveTo(13.53f, 16.8f, 12.79f, 17f, 12f, 17f)
            curveTo(9.24f, 17f, 7f, 14.76f, 7f, 12f)
            curveTo(7f, 11.21f, 7.2f, 10.47f, 7.53f, 9.8f)
            close()
            moveTo(11.84f, 9.02f)
            lineTo(14.99f, 12.17f)
            lineTo(15.01f, 12.01f)
            curveTo(15.01f, 10.35f, 13.67f, 9.01f, 12.01f, 9.01f)
            close()
        }
    }.build()
}

val QueueMusicIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "QueueMusic",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(15f, 6f)
            horizontalLineTo(3f)
            verticalLineTo(8f)
            horizontalLineTo(15f)
            close()
            moveTo(15f, 10f)
            horizontalLineTo(3f)
            verticalLineTo(12f)
            horizontalLineTo(15f)
            close()
            moveTo(3f, 16f)
            horizontalLineTo(11f)
            verticalLineTo(14f)
            horizontalLineTo(3f)
            close()
            moveTo(17f, 6f)
            verticalLineTo(14.18f)
            curveTo(16.69f, 14.07f, 16.35f, 14f, 16f, 14f)
            curveTo(14.34f, 14f, 13f, 15.34f, 13f, 17f)
            curveTo(13f, 18.66f, 14.34f, 20f, 16f, 20f)
            curveTo(17.66f, 20f, 19f, 18.66f, 19f, 17f)
            verticalLineTo(8f)
            horizontalLineTo(22f)
            verticalLineTo(6f)
            close()
        }
    }.build()
}
