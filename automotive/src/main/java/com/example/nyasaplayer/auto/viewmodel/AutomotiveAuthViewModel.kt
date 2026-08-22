package com.example.nyasaplayer.auto.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nyasaplayer.core.common.models.UserProfile
import com.example.nyasaplayer.core.data.api.AuthRepository
import com.example.nyasaplayer.core.data.api.AuthResult
import com.example.nyasaplayer.core.data.api.AuthSession
import com.example.nyasaplayer.core.data.api.UserRepository
import com.example.nyasaplayer.core.data.sync.CatalogSync
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
    val displayName: String = "",
)

@HiltViewModel
class AutomotiveAuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val catalogSync: CatalogSync,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CarAuthUiState(
            isAuthenticated = authRepository.isAuthenticated,
            displayName = authRepository.currentUser?.displayName.orEmpty(),
        ),
    )
    val uiState: StateFlow<CarAuthUiState> = _uiState.asStateFlow()

    /**
     * An explicit sign-in owns the transition into the shell until its own success path finishes.
     *
     * Firebase reports the session the moment credentials are accepted, which is before the
     * profile write. Letting that emission enter the shell would drop the driver into a launcher
     * whose profile does not exist yet, and would strand the loading state it never left.
     */
    private var signInInProgress = false

    /**
     * The last session the listener reported, so the end of a sign-in can act on what is true now
     * rather than on what was true when its credential was accepted.
     */
    private var latestSession = AuthSession(userId = authRepository.currentUserId)

    private fun onSession(session: AuthSession) {
        latestSession = session
        // Sync follows the session even mid-sign-in: the catalogue is not user-scoped, so there
        // is nothing to hold back.
        if (session.isAuthenticated) catalogSync.start() else catalogSync.stop()

        // An explicit sign-in owns *entry* into the shell until its own success path finishes
        // (D52); it applies this same session when it does. A revocation is never deferred: it is
        // authoritative, the sign-in it interrupts is already doomed, and the driver should not
        // watch a spinner until a credential call somewhere times out.
        if (signInInProgress && session.isAuthenticated) return
        applySession(session)
    }

    private fun applySession(session: AuthSession) {
        if (session.isAuthenticated) {
            _uiState.update {
                it.copy(isAuthenticated = true, displayName = session.displayName)
            }
        } else {
            // errorMessage is left alone: a failed sign-in sets it and does not change auth
            // state, so clearing here would erase the reason the driver is still on this screen.
            _uiState.update {
                it.copy(isAuthenticated = false, isLoading = false, displayName = "")
            }
        }
    }

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        signInInProgress = false
        _uiState.update {
            it.copy(
                isLoading = false,
                errorMessage = throwable.message ?: "Sign-in failed",
            )
        }
    }

    init {
        // Seeded above from the snapshot to avoid a frame of signed-out UI; this is what keeps it
        // true afterwards. A session revoked on another device arrives here and nowhere else.
        // The handler is a backstop: a collector that died silently would restore exactly the
        // stale-snapshot behaviour T2 exists to remove, so a throw has to be visible.
        viewModelScope.launch(exceptionHandler) {
            authRepository.authSession.collect(::onSession)
        }
    }

    fun signInWithGoogleToken(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        viewModelScope.launch(exceptionHandler) {
            signInInProgress = true
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                when (val result = authRepository.signInWithCredential(credential)) {
                    is AuthResult.Success -> {
                        createUserProfile(result.user)
                        _uiState.update { it.copy(isLoading = false) }
                    }
                    is AuthResult.Error -> {
                        _uiState.update {
                            it.copy(isLoading = false, errorMessage = result.message)
                        }
                    }
                }
            } finally {
                signInInProgress = false
                // Not "the credential was accepted, so we are in": the session can have been
                // revoked while the profile was being written, and writing `true` here would
                // show the shell with sync already stopped and nothing left to restart it.
                applySession(latestSession)
            }
        }
    }

    /**
     * Signs out and lets the session collector perform the transition, so an explicit sign-out and
     * one revoked elsewhere leave the app in exactly the same state by exactly the same path.
     */
    fun signOut() {
        authRepository.signOut()
        // Also stopped here, not only through the collector: FirebaseSyncManager owns an
        // independent scope, so if this ViewModel is cleared before the collector is scheduled —
        // the activity being destroyed on the sign-out tap — sync would otherwise keep running
        // for a signed-out driver. Both calls are idempotent.
        catalogSync.stop()
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
