package com.example.nyasaplayer.data.api

import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.Flow

// TODO: Phase 3 — replace FirebaseUser/AuthCredential with domain types
interface AuthRepository {
    val currentUser: FirebaseUser?
    val isAuthenticated: Boolean
    fun authStateFlow(): Flow<FirebaseUser?>
    suspend fun signInWithEmail(email: String, password: String): AuthResult
    suspend fun signUpWithEmail(email: String, password: String): AuthResult
    suspend fun signInWithCredential(credential: AuthCredential): AuthResult
    suspend fun sendPasswordResetEmail(email: String): Result<Unit>
    fun signOut()
}
