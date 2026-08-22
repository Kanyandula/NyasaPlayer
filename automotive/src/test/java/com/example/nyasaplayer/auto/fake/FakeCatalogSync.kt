package com.example.nyasaplayer.auto.fake

import com.example.nyasaplayer.core.data.sync.CatalogSync

/** Counts the lifecycle calls, so a test can assert sync followed the session. */
class FakeCatalogSync : CatalogSync {

    var startCount = 0
        private set

    var stopCount = 0
        private set

    /**
     * Whether sync should be running now. Tracked rather than derived from the counts: a stop
     * followed by a start leaves them equal, and "equal" is not "stopped".
     */
    var isRunning: Boolean = false
        private set

    override fun start() {
        startCount++
        isRunning = true
    }

    override fun stop() {
        stopCount++
        isRunning = false
    }
}
