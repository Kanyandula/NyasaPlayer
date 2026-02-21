package com.example.nyasaplayer.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nyasaplayer.data.AuthRepository
import com.example.nyasaplayer.data.UserRepository
import com.example.nyasaplayer.util.NetworkMonitor
import com.example.nyasaplayer.util.isNetworkError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

data class ProfileUiState(
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val accountType: String = "free",
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val isNetworkError: Boolean = false,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        _uiState.update {
            it.copy(
                errorMessage = throwable.message ?: "An unexpected error occurred",
                isNetworkError = (throwable as? Exception)?.isNetworkError() == true,
            )
        }
    }

    init {
        loadProfile()
    }

    private fun loadProfile() {
        val user = authRepository.currentUser ?: return
        // Fall back to FirebaseUser fields immediately
        _uiState.update {
            it.copy(
                displayName = user.displayName.orEmpty(),
                email = user.email.orEmpty(),
                photoUrl = user.photoUrl?.toString().orEmpty(),
                isLoading = false,
            )
        }
        if (!networkMonitor.isOnline.value) {
            _uiState.update {
                it.copy(
                    errorMessage = "No internet connection",
                    isNetworkError = true,
                )
            }
        }
        viewModelScope.launch(exceptionHandler) {
            try {
                userRepository.getUserProfile(user.uid).collect { profile ->
                    if (profile != null) {
                        _uiState.update {
                            it.copy(
                                displayName = profile.displayName.ifBlank {
                                    user.displayName.orEmpty()
                                },
                                email = profile.email.ifBlank { user.email.orEmpty() },
                                photoUrl = profile.photoUrl.ifBlank {
                                    user.photoUrl?.toString().orEmpty()
                                },
                                accountType = profile.accountType,
                                isLoading = false,
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                _uiState.update {
                    it.copy(
                        errorMessage = e.message ?: "Unknown error",
                        isNetworkError = e.isNetworkError(),
                    )
                }
            }
        }
    }

    fun retry() {
        _uiState.value = ProfileUiState()
        loadProfile()
    }

    fun signOut() {
        authRepository.signOut()
    }
}
