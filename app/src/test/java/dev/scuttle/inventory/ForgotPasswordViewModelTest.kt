package dev.scuttle.inventory

import dev.scuttle.inventory.data.auth.AuthRepository
import dev.scuttle.inventory.ui.auth.ForgotPasswordViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ForgotPasswordViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** Only forgotPassword() is exercised; the rest satisfy the interface. */
    private class FakeAuthRepository(
        private val succeed: Boolean,
    ) : AuthRepository {
        override fun isAuthenticated(): Boolean = false

        override val sessionActive = MutableStateFlow(false)

        override suspend fun register(
            name: String,
            email: String,
            password: String,
        ) = Unit

        override suspend fun login(
            email: String,
            password: String,
        ) = Unit

        override suspend fun loginWithGoogle(idToken: String) = Unit

        override suspend fun loginWithGoogleCode(
            code: String,
            codeVerifier: String,
            redirectUri: String,
        ) = Unit

        override suspend fun logout() = Unit

        override suspend fun forgotPassword(email: String) {
            if (!succeed) throw RuntimeException("Unable to resolve host \"inventory.test\"")
        }
    }

    @Test
    fun submit_success_marks_sent() =
        runTest {
            val vm = ForgotPasswordViewModel(FakeAuthRepository(succeed = true))
            vm.onEmailChange("stan@example.test")

            vm.submit()

            assertTrue(vm.state.value.sent)
            assertFalse(vm.state.value.loading)
            assertNull(vm.state.value.error)
        }

    @Test
    fun submit_failure_surfaces_a_friendly_error_not_the_raw_exception() =
        runTest {
            val vm = ForgotPasswordViewModel(FakeAuthRepository(succeed = false))
            vm.onEmailChange("stan@example.test")

            vm.submit()

            assertFalse(vm.state.value.sent)
            assertFalse(vm.state.value.loading)
            val error = vm.state.value.error
            assertNotNull(error)
            // The raw "Unable to resolve host…" must not leak to the user.
            assertEquals("Something went wrong. Please try again.", error)
        }

    @Test
    fun onEmailChange_clears_a_previous_error() =
        runTest {
            val vm = ForgotPasswordViewModel(FakeAuthRepository(succeed = false))
            vm.onEmailChange("stan@example.test")
            vm.submit()
            assertNotNull(vm.state.value.error)

            vm.onEmailChange("stan2@example.test")

            assertNull(vm.state.value.error)
            assertEquals("stan2@example.test", vm.state.value.email)
        }
}
