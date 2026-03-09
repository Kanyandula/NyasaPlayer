package com.example.nyasaplayer.auto

import android.app.Application
import com.example.nyasaplayer.core.data.api.AuthRepository
import com.example.nyasaplayer.core.data.sync.FirebaseSyncManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AutomotiveApplication : Application() {

    @Inject
    lateinit var firebaseSyncManager: FirebaseSyncManager

    @Inject
    lateinit var authRepository: AuthRepository

    override fun onCreate() {
        super.onCreate()
        if (authRepository.isAuthenticated) {
            firebaseSyncManager.start()
        }
    }
}
