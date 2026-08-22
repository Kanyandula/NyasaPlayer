package com.example.nyasaplayer.core.data.fake

import com.example.nyasaplayer.core.data.api.AuthRepository
import com.example.nyasaplayer.core.data.api.AuthResult
import com.example.nyasaplayer.core.data.api.AuthSession
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAuthRepository : AuthRepository {

    val user = MutableStateFlow<FirebaseUser?>(null)

    var signInResult: AuthResult = AuthResult.Error("Not configured")
    var signUpResult: AuthResult = AuthResult.Error("Not configured")
    var credentialResult: AuthResult = AuthResult.Error("Not configured")
    var resetEmailResult: Result<Unit> = Result.success(Unit)

    override val currentUser: FirebaseUser? get() = user.value

    var userId: String? = null

    override val currentUserId: String? get() = userId ?: user.value?.uid

    override val isAuthenticated: Boolean get() = user.value != null

    /** Emit here to model a session appearing or being revoked elsewhere. */
    val sessions = MutableStateFlow(AuthSession())

    override val authSession: Flow<AuthSession> = sessions

    override suspend fun signInWithEmail(email: String, password: String): AuthResult = signInResult

    override suspend fun signUpWithEmail(email: String, password: String): AuthResult = signUpResult

    override suspend fun signInWithCredential(credential: AuthCredential): AuthResult = credentialResult

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> = resetEmailResult

    override fun signOut() {
        user.value = null
        userId = null
        sessions.value = AuthSession()
    }
}
