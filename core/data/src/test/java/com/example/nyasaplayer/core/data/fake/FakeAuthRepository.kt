package com.example.nyasaplayer.core.data.fake

import com.example.nyasaplayer.core.data.api.AuthRepository
import com.example.nyasaplayer.core.data.api.AuthResult
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAuthRepository : AuthRepository {

    val user = MutableStateFlow<FirebaseUser?>(null)

    var signInResult: AuthResult = AuthResult.Error("Not configured")
    var signUpResult: AuthResult = AuthResult.Error("Not configured")
    var credentialResult: AuthResult = AuthResult.Error("Not configured")
    var resetEmailResult: Result<Unit> = Result.success(Unit)

    override val currentUser: FirebaseUser? get() = user.value

    override val isAuthenticated: Boolean get() = user.value != null

    override suspend fun signInWithEmail(email: String, password: String): AuthResult = signInResult

    override suspend fun signUpWithEmail(email: String, password: String): AuthResult = signUpResult

    override suspend fun signInWithCredential(credential: AuthCredential): AuthResult = credentialResult

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> = resetEmailResult

    override fun signOut() {
        user.value = null
    }
}
