package com.example.nyasaplayer.core.data.sync

/**
 * Starting and stopping catalogue sync, without naming what performs it.
 *
 * [FirebaseSyncManager] is a concrete class wrapping `FirebaseFirestore` and four DAOs, so
 * anything injecting it directly cannot be built in a unit test — the reason
 * `AutomotiveAuthViewModel` had no tests at all. Callers that only drive the lifecycle take this
 * instead (T2).
 *
 * Both calls are idempotent: starting a running sync or stopping a stopped one does nothing.
 */
interface CatalogSync {
    fun start()
    fun stop()
}
