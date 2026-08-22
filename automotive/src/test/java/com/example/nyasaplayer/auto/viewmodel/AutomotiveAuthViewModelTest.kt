package com.example.nyasaplayer.auto.viewmodel

import com.example.nyasaplayer.auto.MainDispatcherRule
import com.example.nyasaplayer.auto.fake.DefaultUserId
import com.example.nyasaplayer.auto.fake.FakeAuthRepository
import com.example.nyasaplayer.auto.fake.FakeCatalogSync
import com.example.nyasaplayer.auto.fake.FakeUserRepository
import com.example.nyasaplayer.core.data.api.AuthResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The ViewModel had no tests at all: its constructor took `FirebaseSyncManager`, a concrete class
 * wrapping Firestore and four DAOs, and `:automotive` has no mocking library. `CatalogSync` is
 * what makes it constructible here (T2).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AutomotiveAuthViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository()
    private val sync = FakeCatalogSync()

    private fun viewModel() = AutomotiveAuthViewModel(
        authRepository = auth,
        userRepository = FakeUserRepository(),
        catalogSync = sync,
    )

    @Test
    fun `a signed-in session renders authenticated`() = runTest {
        auth.emitSession(userId = DefaultUserId, displayName = "Miracle")
        val vm = viewModel()

        advanceUntilIdle()

        assertTrue(vm.uiState.value.isAuthenticated)
        assertEquals("Miracle", vm.uiState.value.displayName)
        assertTrue(sync.isRunning)
    }

    @Test
    fun `no session renders unauthenticated`() = runTest {
        auth.emitSession(userId = null)
        val vm = viewModel()

        advanceUntilIdle()

        assertFalse(vm.uiState.value.isAuthenticated)
        assertFalse(sync.isRunning)
    }

    /** The failure T2 was filed for: a session revoked elsewhere, with nothing on screen saying so. */
    @Test
    fun `a session revoked elsewhere leaves the shell and stops sync`() = runTest {
        auth.emitSession(userId = DefaultUserId)
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isAuthenticated)

        auth.emitSession(userId = null)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isAuthenticated)
        assertEquals(1, sync.stopCount)
        assertEquals("", vm.uiState.value.displayName)
    }

    @Test
    fun `a session restored elsewhere re-enters the shell and restarts sync`() = runTest {
        auth.emitSession(userId = null)
        val vm = viewModel()
        advanceUntilIdle()

        auth.emitSession(userId = DefaultUserId, displayName = "Miracle")
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isAuthenticated)
        assertTrue(sync.isRunning)
    }

    /**
     * Credentials accepted, profile write still running, and then the session dies — the sequence
     * where the deferral could strand the spinner it is holding.
     */
    @Test
    fun `a session dying mid sign-in clears the loading state it was holding`() = runTest {
        auth.emitSession(userId = null)
        val vm = viewModel()
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        auth.credentialGate = gate

        vm.signInWithGoogleToken("token")
        advanceUntilIdle()
        assertTrue("the sign-in never started", vm.uiState.value.isLoading)

        // Firebase reports the accepted credential, which the deferral holds back from the shell.
        auth.emitSession(userId = DefaultUserId, displayName = "Miracle")
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isAuthenticated)

        auth.emitSession(userId = null)
        advanceUntilIdle()

        assertFalse("the spinner outlived the session", vm.uiState.value.isLoading)
        assertFalse(vm.uiState.value.isAuthenticated)

        gate.complete(Unit)
        advanceUntilIdle()
    }

    /** A sign-in failure explains why the driver is still here; a later revocation must not erase it. */
    @Test
    fun `a revocation leaves the sign-in error on screen`() = runTest {
        auth.emitSession(userId = DefaultUserId)
        val vm = viewModel()
        advanceUntilIdle()
        vm.onGoogleSignInError("network")

        auth.emitSession(userId = null)
        advanceUntilIdle()

        assertEquals("network", vm.uiState.value.errorMessage)
    }

    /**
     * D-T2.4: Firebase reports the session the moment credentials are accepted, before the profile
     * write. Entering the shell on that emission would drop the driver into a launcher whose
     * profile does not exist yet, and would leave the loading state it never exited.
     */
    @Test
    fun `an authenticated emission during sign-in does not enter the shell early`() = runTest {
        auth.emitSession(userId = null)
        val vm = viewModel()
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        auth.credentialGate = gate

        vm.signInWithGoogleToken("token")
        advanceUntilIdle()
        auth.emitSession(userId = DefaultUserId, displayName = "Miracle")
        advanceUntilIdle()

        assertFalse("the shell opened before sign-in finished", vm.uiState.value.isAuthenticated)
        assertTrue(vm.uiState.value.isLoading)
        // Sync is not deferred with it: the catalogue is not user-scoped.
        assertTrue(sync.isRunning)

        gate.complete(Unit)
        advanceUntilIdle()
    }

    /** The deferral must not outlive the sign-in that set it, or the gate never opens again. */
    @Test
    fun `a failed sign-in releases the deferral so later emissions are honoured`() = runTest {
        auth.emitSession(userId = null)
        val vm = viewModel()
        advanceUntilIdle()
        auth.credentialResult = AuthResult.Error("no network")

        vm.signInWithGoogleToken("token")
        advanceUntilIdle()
        assertEquals("no network", vm.uiState.value.errorMessage)

        auth.emitSession(userId = DefaultUserId, displayName = "Miracle")
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isAuthenticated)
    }

    /**
     * The race the review caught: credentials accepted, then the session revoked while the
     * profile is being written. The sign-in used to finish by writing "authenticated" flat out,
     * which reopened the shell over a dead session with sync already stopped and no further
     * emission coming to restart it.
     */
    @Test
    fun `a sign-in finishing after a revocation does not reopen the shell`() = runTest {
        auth.emitSession(userId = null)
        val vm = viewModel()
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        auth.credentialGate = gate
        auth.credentialResult = AuthResult.Error("late failure")

        vm.signInWithGoogleToken("token")
        advanceUntilIdle()
        auth.emitSession(userId = DefaultUserId, displayName = "Miracle")
        advanceUntilIdle()
        auth.emitSession(userId = null)
        advanceUntilIdle()

        gate.complete(Unit)
        advanceUntilIdle()

        assertFalse("the shell reopened over a dead session", vm.uiState.value.isAuthenticated)
        assertFalse("sync outlived the session", sync.isRunning)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `signing out delegates to the repository and transitions through the collector`() = runTest {
        auth.emitSession(userId = DefaultUserId)
        val vm = viewModel()
        advanceUntilIdle()

        vm.signOut()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isAuthenticated)
        // State, not call count: signOut() and the collector both stop, and the contract is
        // idempotent about that.
        assertFalse(sync.isRunning)
    }
}
