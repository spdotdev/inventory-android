package dev.scuttle.inventory

import dev.scuttle.inventory.data.auth.AuthRepository
import dev.scuttle.inventory.data.error.ErrorLogger
import dev.scuttle.inventory.ui.auth.AuthViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class AuthViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeAuthRepository(
        private val succeed: Boolean,
        private val error: Throwable = RuntimeException("Invalid credentials."),
    ) : AuthRepository {
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
            if (!succeed) throw error
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
    fun a_google_401_shows_the_google_specific_copy_not_the_password_copy() = runTest {
        // X12: a rejected Google ID token comes back as a 401, the same status the
        // email/password path uses for "Incorrect email or password." That wrong copy
        // would be nonsensical on the Google button — the Google 401 must map to its
        // own message. Guards toGoogleAuthErrorMessage() against a future regression to
        // the shared password mapper.
        val http401 = HttpException(Response.error<Any>(401, "".toResponseBody(null)))
        val viewModel = AuthViewModel(FakeAuthRepository(succeed = false, error = http401), FakeErrorLogger())

        viewModel.loginWithGoogle("bad-id-token")

        val state = viewModel.state.value
        assertFalse(state.authenticated)
        assertFalse(state.googleLoading)
        assertEquals("Google sign-in failed. Please try again.", state.error)
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
