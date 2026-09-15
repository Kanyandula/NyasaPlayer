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
}
