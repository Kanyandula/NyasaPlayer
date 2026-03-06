package com.example.nyasaplayer.data.sync

import com.example.nyasaplayer.core.data.sync.FirebaseSyncManager
import com.google.firebase.firestore.FirebaseFirestoreException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class FirebaseSyncManagerBackoffTest {

    // --- calculateBackoff ---

    @Test
    fun calculateBackoff_attempt0_returns5s() {
        assertEquals(5_000L, FirebaseSyncManager.calculateBackoff(0))
    }

    @Test
    fun calculateBackoff_attempt1_returns10s() {
        assertEquals(10_000L, FirebaseSyncManager.calculateBackoff(1))
    }

    @Test
    fun calculateBackoff_attempt2_returns20s() {
        assertEquals(20_000L, FirebaseSyncManager.calculateBackoff(2))
    }

    @Test
    fun calculateBackoff_attempt6_cappedAt300s() {
        // 5000 * 2^6 = 320_000, capped to 300_000
        assertEquals(300_000L, FirebaseSyncManager.calculateBackoff(6))
    }

    @Test
    fun calculateBackoff_attempt7_stillCappedAt300s() {
        assertEquals(300_000L, FirebaseSyncManager.calculateBackoff(7))
    }

    @Test
    fun calculateBackoff_attempt20_cappedAt300s() {
        assertEquals(300_000L, FirebaseSyncManager.calculateBackoff(20))
    }

    @Test
    fun calculateBackoff_attempt100_cappedAt300s() {
        assertEquals(300_000L, FirebaseSyncManager.calculateBackoff(100))
    }

    // --- isPermanentError ---

    @Test
    fun isPermanentError_permissionDenied_returnsTrue() {
        val error = FirebaseFirestoreException(
            "denied",
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
        )
        assertTrue(FirebaseSyncManager.isPermanentError(error))
    }

    @Test
    fun isPermanentError_notFound_returnsTrue() {
        val error = FirebaseFirestoreException(
            "not found",
            FirebaseFirestoreException.Code.NOT_FOUND,
        )
        assertTrue(FirebaseSyncManager.isPermanentError(error))
    }

    @Test
    fun isPermanentError_unauthenticated_returnsTrue() {
        val error = FirebaseFirestoreException(
            "unauth",
            FirebaseFirestoreException.Code.UNAUTHENTICATED,
        )
        assertTrue(FirebaseSyncManager.isPermanentError(error))
    }

    @Test
    fun isPermanentError_unavailable_returnsFalse() {
        val error = FirebaseFirestoreException(
            "unavailable",
            FirebaseFirestoreException.Code.UNAVAILABLE,
        )
        assertFalse(FirebaseSyncManager.isPermanentError(error))
    }

    @Test
    fun isPermanentError_cancelled_returnsFalse() {
        val error = FirebaseFirestoreException(
            "cancelled",
            FirebaseFirestoreException.Code.CANCELLED,
        )
        assertFalse(FirebaseSyncManager.isPermanentError(error))
    }

    @Test
    fun isPermanentError_runtimeException_returnsFalse() {
        assertFalse(FirebaseSyncManager.isPermanentError(RuntimeException("boom")))
    }

    @Test
    fun isPermanentError_ioException_returnsFalse() {
        assertFalse(FirebaseSyncManager.isPermanentError(IOException("network")))
    }
}
