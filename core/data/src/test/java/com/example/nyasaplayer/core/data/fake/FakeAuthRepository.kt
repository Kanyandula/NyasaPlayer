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

    /**
     * One backing state for the snapshot and the flow, so a test cannot leave them disagreeing
     * about who is signed in — the confusion T2 exists to remove.
     */
    val sessions = MutableStateFlow(AuthSession())

    override val authSession: Flow<AuthSession> = sessions

    var userId: String?
        get() = sessions.value.userId
        set(value) {
            sessions.value = sessions.value.copy(userId = value)
        }

    override val currentUserId: String? get() = userId ?: user.value?.uid

    override val isAuthenticated: Boolean get() = currentUserId != null

    override suspend fun signInWithEmail(email: String, password: String): AuthResult = signInResult

    override suspend fun signUpWithEmail(email: String, password: String): AuthResult = signUpResult

    override suspend fun signInWithCredential(credential: AuthCredential): AuthResult = credentialResult

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> = resetEmailResult

    override fun signOut() {
        user.value = null
        sessions.value = AuthSession()
    }
}
