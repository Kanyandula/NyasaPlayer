package com.example.nyasaplayer.core.data

import com.example.nyasaplayer.core.data.api.AuthRepository
import com.example.nyasaplayer.core.data.api.AuthResult
import com.example.nyasaplayer.core.data.api.AuthSession
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

@Suppress("TooGenericExceptionCaught")
class FirebaseAuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
) : AuthRepository {
    override val currentUser: FirebaseUser? get() = firebaseAuth.currentUser

    override val currentUserId: String? get() = firebaseAuth.currentUser?.uid

    override val isAuthenticated: Boolean get() = currentUser != null

    /**
     * Firebase calls the listener once on registration with the current user, so a collector gets
     * the present session before it gets any change — no separate seeding read is needed.
     *
     * `distinctUntilChanged` because Firebase also fires on token refresh, which is not a session
     * change and would otherwise churn every collector roughly hourly.
     */
    override val authSession: Flow<AuthSession> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser.toSession())
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    override suspend fun signInWithEmail(email: String, password: String): AuthResult {
        return try {
            val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            result.user?.let { AuthResult.Success(it) }
                ?: AuthResult.Error("Sign-in failed")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Unknown error")
        }
    }

    override suspend fun signUpWithEmail(email: String, password: String): AuthResult {
        return try {
            val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            result.user?.let { AuthResult.Success(it) }
                ?: AuthResult.Error("Sign-up failed")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Unknown error")
        }
    }

    override suspend fun signInWithCredential(credential: AuthCredential): AuthResult {
        return try {
            val result = firebaseAuth.signInWithCredential(credential).await()
            result.user?.let { AuthResult.Success(it) }
                ?: AuthResult.Error("Credential sign-in failed")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Unknown error")
        }
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return try {
            firebaseAuth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun signOut() {
        firebaseAuth.signOut()
    }
}

private fun FirebaseUser?.toSession(): AuthSession =
    AuthSession(userId = this?.uid, displayName = this?.displayName.orEmpty())
