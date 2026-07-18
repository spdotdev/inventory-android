package dev.scuttle.inventory

import dev.scuttle.inventory.R
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
            assertNull(vm.state.value.errorRes)
        }

    @Test
    fun submit_failure_surfaces_a_friendly_error_not_the_raw_exception() =
        runTest {
            val vm = ForgotPasswordViewModel(FakeAuthRepository(succeed = false))
            vm.onEmailChange("stan@example.test")

            vm.submit()

            assertFalse(vm.state.value.sent)
            assertFalse(vm.state.value.loading)
            // The raw "Unable to resolve host…" must not leak to the user —
            // failures resolve to a localized resource id (GAP-8; the message
            // itself was hardcoded EN before, invisible to NL users).
            assertEquals(R.string.error_generic, vm.state.value.errorRes)
        }

    @Test
    fun onEmailChange_clears_a_previous_error() =
        runTest {
            val vm = ForgotPasswordViewModel(FakeAuthRepository(succeed = false))
            vm.onEmailChange("stan@example.test")
            vm.submit()
            assertNotNull(vm.state.value.errorRes)

            vm.onEmailChange("stan2@example.test")

            assertNull(vm.state.value.errorRes)
            assertEquals("stan2@example.test", vm.state.value.email)
        }

    @Test
    fun resetToInput_leaves_sent_state_with_email_still_editable() =
        runTest {
            val vm = ForgotPasswordViewModel(FakeAuthRepository(succeed = true))
            vm.onEmailChange("stan@example.test")
            vm.submit()
            assertTrue(vm.state.value.sent)

            vm.resetToInput()

            assertFalse(vm.state.value.sent)
            assertNull(vm.state.value.errorRes)
            // The typed email survives the reset — GAP5-M6 is about fixing a typo,
            // not starting over from a blank field.
            assertEquals("stan@example.test", vm.state.value.email)
        }
}
