package com.example.nyasaplayer

import android.app.Application
import com.example.nyasaplayer.data.sync.FirebaseSyncManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NyasaPlayerApplication : Application() {

    @Inject
    lateinit var firebaseSyncManager: FirebaseSyncManager

    override fun onCreate() {
        super.onCreate()
        firebaseSyncManager.start()
    }
}
