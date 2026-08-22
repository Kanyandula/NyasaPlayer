package com.example.nyasaplayer.core.data.api

import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.Flow

// TODO: Phase 3 — replace FirebaseUser/AuthCredential with domain types
interface AuthRepository {
    val currentUser: FirebaseUser?

    /**
     * The signed-in user's id, or null.
     *
     * Exists so callers that only need the id do not have to reach through [currentUser] and
     * therefore through `FirebaseUser`, which cannot be constructed in a unit test.
     */
    val currentUserId: String?
    val isAuthenticated: Boolean

    /**
     * Who is signed in, as it changes — including a session invalidated elsewhere.
     *
     * The three members above sample this; they answer for the instant they are read and never
     * again. A collector of this flow learns that a session died without polling, which is what
     * the automotive shell's sign-in gate needs (T2).
     */
    val authSession: Flow<AuthSession>
    suspend fun signInWithEmail(email: String, password: String): AuthResult
    suspend fun signUpWithEmail(email: String, password: String): AuthResult
    suspend fun signInWithCredential(credential: AuthCredential): AuthResult
    suspend fun sendPasswordResetEmail(email: String): Result<Unit>
    fun signOut()
}
