package com.example.nyasaplayer.auto.fake

import com.example.nyasaplayer.core.data.sync.CatalogSync

/** Counts the lifecycle calls, so a test can assert sync followed the session. */
class FakeCatalogSync : CatalogSync {

    var startCount = 0
        private set

    var stopCount = 0
        private set

    /** True while sync should be running, mirroring the real manager's idempotent pair. */
    val isRunning: Boolean get() = startCount > stopCount

    override fun start() {
        startCount++
    }

    override fun stop() {
        stopCount++
    }
}
