package com.example.nyasaplayer.core.data.api

/**
 * Who is signed in right now, without a Firebase type in sight.
 *
 * `FirebaseUser` cannot be constructed in a unit test, which is why the snapshot API already
 * carries [AuthRepository.currentUserId] beside `currentUser`. The live channel takes the same
 * route: a test drives a session transition by emitting one of these.
 */
data class AuthSession(
    val userId: String? = null,
    val displayName: String = "",
) {
    val isAuthenticated: Boolean get() = !userId.isNullOrBlank()
}
