package com.example.nyasaplayer.auto

import android.app.Application
import com.example.nyasaplayer.core.data.api.AuthRepository
import com.example.nyasaplayer.core.data.sync.CatalogSync
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AutomotiveApplication : Application() {

    @Inject
    lateinit var catalogSync: CatalogSync

    @Inject
    lateinit var authRepository: AuthRepository

    override fun onCreate() {
        super.onCreate()
        if (authRepository.isAuthenticated) {
            catalogSync.start()
        }
    }
}
