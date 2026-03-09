package com.example.nyasaplayer.auto.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nyasaplayer.core.common.models.UserProfile
import com.example.nyasaplayer.core.data.api.AuthRepository
import com.example.nyasaplayer.core.data.api.AuthResult
import com.example.nyasaplayer.core.data.api.UserRepository
import com.example.nyasaplayer.core.data.sync.FirebaseSyncManager
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

data class CarAuthUiState(
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class AutomotiveAuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val firebaseSyncManager: FirebaseSyncManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CarAuthUiState(isAuthenticated = authRepository.isAuthenticated),
    )
    val uiState: StateFlow<CarAuthUiState> = _uiState.asStateFlow()

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        _uiState.update {
            it.copy(
                isLoading = false,
                errorMessage = throwable.message ?: "Sign-in failed",
            )
        }
    }

    fun signInWithGoogleToken(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        viewModelScope.launch(exceptionHandler) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = authRepository.signInWithCredential(credential)) {
                is AuthResult.Success -> {
                    createUserProfile(result.user)
                    firebaseSyncManager.start()
                    _uiState.update { it.copy(isLoading = false, isAuthenticated = true) }
                }
                is AuthResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
            }
        }
    }

    fun onGoogleSignInError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun createUserProfile(user: FirebaseUser) {
        try {
            val profile = UserProfile(
                userId = user.uid,
                displayName = user.displayName.orEmpty(),
                email = user.email.orEmpty(),
                photoUrl = user.photoUrl?.toString().orEmpty(),
                createdAt = System.currentTimeMillis(),
                accountType = if (
                    user.providerData.any { it.providerId == "google.com" }
                ) {
                    "google"
                } else {
                    "email"
                },
            )
            userRepository.createOrUpdateProfile(profile)
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
            // Silent fail — profile creation is non-critical during auth
        }
    }
}
