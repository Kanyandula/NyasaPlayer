package com.example.nyasaplayer.core.data.crash

import android.content.Context
import android.content.pm.PackageManager
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The apps' one way into Crashlytics, which is an `implementation` dependency of this module (T24 D2).
 * Constructing it touches no Firebase; only its methods do.
 */
@Singleton
class CrashReporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Tags every report from this process with `surface`: `car` on an automotive device, `mobile`
     * otherwise (T24 D6). Call first in `Application.onCreate`, so a process the media center starts
     * without an activity is tagged too.
     */
    fun start() {
        val surface =
            if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) "car" else "mobile"
        FirebaseCrashlytics.getInstance().setCustomKey("surface", surface)
    }
}
