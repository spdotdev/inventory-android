package dev.scuttle.inventory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guards the auth-redirect gating (T13): the redirect must fire only on a real
 * login/logout transition, and stay null on process-death restore so the
 * NavController's restored back stack survives.
 */
class AuthRedirectTest {

    @Test
    fun cold_start_authenticated_routes_to_dashboard() {
        assertEquals(AuthRedirect.TO_DASHBOARD, authRedirectFor(previous = null, current = true))
    }

    @Test
    fun cold_start_unauthenticated_is_a_noop() {
        // AUTH is already the NavHost start destination.
        assertNull(authRedirectFor(previous = null, current = false))
    }

    @Test
    fun login_transition_routes_to_dashboard() {
        assertEquals(AuthRedirect.TO_DASHBOARD, authRedirectFor(previous = false, current = true))
    }

    @Test
    fun logout_transition_routes_to_auth() {
        assertEquals(AuthRedirect.TO_AUTH, authRedirectFor(previous = true, current = false))
    }

    @Test
    fun process_death_restore_while_authenticated_leaves_back_stack() {
        // The crux of T13: previous == current == true → no navigation, back stack intact.
        assertNull(authRedirectFor(previous = true, current = true))
    }

    @Test
    fun no_change_while_unauthenticated_is_a_noop() {
        assertNull(authRedirectFor(previous = false, current = false))
    }
}
