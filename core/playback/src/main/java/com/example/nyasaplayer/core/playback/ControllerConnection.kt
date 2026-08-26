package com.example.nyasaplayer.core.playback

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the process's connection to `PlaybackService`, and can build another one.
 *
 * Before this existed, `PlaybackModule` provided a single `@Singleton`
 * `ListenableFuture<MediaController>` and every collector released *that* future when its ViewModel
 * was cleared. `SharedControllerFutureTest` shows what followed: the future is idempotent and
 * release is terminal, so the next ViewModel injected the same instance with `isConnected == false`
 * and every command failed for the rest of the process's life. Backing out of the app and returning
 * was enough to trigger it (T14).
 *
 * So releasing is now reference-counted — a consumer says only that *it* is finished — and there is
 * a way to build a fresh connection when the current one is no good.
 */
@UnstableApi
@Singleton
class ControllerConnection @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionToken: SessionToken,
) {

    private val lock = Any()
    private var future: ListenableFuture<MediaController>? = null
    private var consumers = 0

    /**
     * The current connection, building one if nobody holds it yet.
     *
     * Every caller must pair this with [release], or the controller stays bound for the life of the
     * process and `PlaybackService` can never stop.
     */
    fun acquire(): ListenableFuture<MediaController> = synchronized(lock) {
        consumers++
        future ?: build().also { future = it }
    }

    /**
     * One consumer is finished. The connection itself is released only when the last one leaves —
     * which is the fix: a ViewModel dying no longer takes playback down for whatever is still using
     * it.
     */
    fun release(): Unit = synchronized(lock) {
        consumers = (consumers - 1).coerceAtLeast(0)
        if (consumers == 0) {
            future?.let { MediaController.releaseFuture(it) }
            future = null
        }
    }

    /**
     * Throws away the current connection and builds a fresh one, for a controller that is gone.
     *
     * Asking the old future again cannot work: it hands back the same released instance every time.
     * Consumer count is untouched — the callers are still callers, they just need a live controller.
     */
    fun reconnect(): ListenableFuture<MediaController> = synchronized(lock) {
        future?.let { MediaController.releaseFuture(it) }
        build().also { future = it }
    }

    private fun build(): ListenableFuture<MediaController> =
        MediaController.Builder(context, sessionToken).buildAsync()
}
