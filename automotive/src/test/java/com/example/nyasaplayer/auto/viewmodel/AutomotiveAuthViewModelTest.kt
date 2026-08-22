package com.example.nyasaplayer.auto.viewmodel

import com.example.nyasaplayer.auto.MainDispatcherRule
import com.example.nyasaplayer.auto.fake.DefaultUserId
import com.example.nyasaplayer.auto.fake.FakeAuthRepository
import com.example.nyasaplayer.auto.fake.FakeCatalogSync
import com.example.nyasaplayer.auto.fake.FakeUserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
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
        auth.emitSession(userId = DefaultUserId)

        assertTrue(viewModel().uiState.value.isAuthenticated)
    }

    @Test
    fun `no session renders unauthenticated`() = runTest {
        auth.emitSession(userId = null)

        assertFalse(viewModel().uiState.value.isAuthenticated)
    }
}
