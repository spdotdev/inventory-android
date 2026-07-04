package dev.scuttle.inventory

import dev.scuttle.inventory.data.auth.AuthRepository
import dev.scuttle.inventory.data.error.ErrorLogger
import dev.scuttle.inventory.ui.auth.AuthViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AuthViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeAuthRepository(private val succeed: Boolean) : AuthRepository {
        private var authed = false
        val session = MutableStateFlow(false)

        override fun isAuthenticated(): Boolean = authed

        override val sessionActive = session


        override suspend fun register(name: String, email: String, password: String) = maybeFail()

        override suspend fun login(email: String, password: String) = maybeFail()

        override suspend fun loginWithGoogle(idToken: String) = maybeFail()

        override suspend fun loginWithGoogleCode(code: String, codeVerifier: String, redirectUri: String) = maybeFail()

        override suspend fun forgotPassword(email: String) = maybeFail()

        override suspend fun logout() {
            authed = false
            session.value = false
        }

        private fun maybeFail() {
            if (!succeed) throw RuntimeException("Invalid credentials.")
            authed = true
            session.value = true
        }
    }

    private class FakeErrorLogger : ErrorLogger {
        override fun log(code: String, message: String?) = Unit
    }

    @Test
    fun login_success_marks_authenticated() = runTest {
        val viewModel = AuthViewModel(FakeAuthRepository(succeed = true), FakeErrorLogger())
        viewModel.onEmailChange("stan@example.test")
        viewModel.onPasswordChange("secret-password")

        viewModel.submit()

        val state = viewModel.state.value
        assertTrue(state.authenticated)
        assertFalse(state.loading)
        assertNull(state.error)
    }

    @Test
    fun login_failure_surfaces_an_error() = runTest {
        val viewModel = AuthViewModel(FakeAuthRepository(succeed = false), FakeErrorLogger())
        viewModel.onEmailChange("stan@example.test")
        viewModel.onPasswordChange("wrong")

        viewModel.submit()

        val state = viewModel.state.value
        assertFalse(state.authenticated)
        assertEquals("Invalid credentials.", state.error)
    }

    @Test
    fun sign_out_clears_authentication() = runTest {
        val viewModel = AuthViewModel(FakeAuthRepository(succeed = true), FakeErrorLogger())
        viewModel.onEmailChange("stan@example.test")
        viewModel.onPasswordChange("secret-password")
        viewModel.submit()
        assertTrue(viewModel.state.value.authenticated)

        viewModel.signOut()

        assertFalse(viewModel.state.value.authenticated)
    }

    @Test
    fun a_mid_session_token_loss_flips_authenticated_to_false() = runTest {
        val repo = FakeAuthRepository(succeed = true)
        val viewModel = AuthViewModel(repo, FakeErrorLogger())
        viewModel.onEmailChange("stan@example.test")
        viewModel.onPasswordChange("secret-password")
        viewModel.submit()
        assertTrue(viewModel.state.value.authenticated)

        // A 401 elsewhere clears the token off the UI thread → sessionActive emits false.
        repo.session.value = false

        assertFalse(viewModel.state.value.authenticated)
    }
}
