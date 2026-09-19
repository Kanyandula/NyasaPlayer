package com.example.nyasaplayer.core.data.crash

import android.content.Context
import android.content.pm.PackageManager
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The apps' one way into Crashlytics, which is an `implementation` dependency of this module (T24 D2).
 * Constructing it touches no Firebase, so it can be injected anywhere (T27 injects it into ViewModels).
 */
@Singleton
class CrashReporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Tags this process's reports from here on with `surface`: `car` on an automotive device, `mobile`
     * otherwise (T24 D6). Call it from `Application.onCreate`, not an activity, because the media center
     * can start the process without one; and ahead of the syncs, so their crashes carry it.
     */
    fun start() {
        val surface =
            if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) "car" else "mobile"
        FirebaseCrashlytics.getInstance().setCustomKey("surface", surface)
    }

    /**
     * Records that a transport command found a controller that was connected and is not any more
     * (T27) — the state T16 closed on the grounds that nothing known produces it.
     *
     * A non-fatal, so the app carries on: the collector rebuilds the controller either way and
     * this only says it happened. The message is a fixed string. Firebase advises keeping unique
     * values out of exception messages — they split one issue into many — and T24 D8 rules out ids
     * and queue data here regardless. The `surface` key set by [start] rides along, which is how
     * the report says which surface it came from.
     *
     * Delivery is on the next launch or with the next fatal, and Crashlytics keeps only the eight
     * most recent between sends. The first report is the evidence; the cap bounds the rest.
     */
    fun reportControllerFoundDisconnected() {
        FirebaseCrashlytics.getInstance()
            .recordException(IllegalStateException(ControllerDisconnectedMessage))
    }

    private companion object {
        const val ControllerDisconnectedMessage =
            "T16 tripwire: transport command found a disconnected controller"
    }
}
