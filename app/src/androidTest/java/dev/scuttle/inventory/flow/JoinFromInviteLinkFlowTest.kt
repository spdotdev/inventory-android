@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package dev.scuttle.inventory.flow

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import dagger.hilt.android.testing.HiltAndroidTest
import dev.scuttle.inventory.FlowTestBase
import dev.scuttle.inventory.ui.dashboard.DASHBOARD_TITLE_TEST_TAG
import org.junit.Test

/**
 * #30: the invite QR encodes the invite *link*, and the scanner dropped those raw contents into
 * the join field — so `POST /households/join` received a URL as the join_code and 404'd on every
 * scanned invite. The unit tests cover parseJoinCode itself; this covers the thing that actually
 * broke, which is what the app puts on the wire.
 */
@HiltAndroidTest
class JoinFromInviteLinkFlowTest : FlowTestBase() {
    @Test
    fun an_invite_link_reaches_the_api_as_a_bare_join_code() {
        mockServer.enqueue(fixture("auth_login.json"))
        mockServer.route("/households", fixture("households_one.json"))
        mockServer.route("/households/1/locations", fixture("locations_one.json"))

        composeRule.apply {
            onNodeWithText("Email").performTextInput("test@example.com")
            onNodeWithText("Password").performTextInput("password123")
            onAllNodesWithText("Sign in").filterToOne(hasClickAction()).performClick()
            waitUntilAtLeastOneExists(hasTestTag(DASHBOARD_TITLE_TEST_TAG), timeoutMillis = 10_000)

            // "More" tab → SettingsScreen (Settings left the top-bar gear; it's a
            // bottom-nav tab now).
            onNodeWithTag("bottom-nav-more").performClick()
            waitForIdle()

            // Exactly what the QR carries, and what the scanner used to hand over verbatim.
            mockServer.enqueue(fixture("household_created.json"))
            onNodeWithText("Join code").performTextInput("https://inventory.scuttle.dev/join/ABCD-2345")
            onNodeWithText("Join").performClick()

            waitUntilAtLeastOneExists(hasText("Joined successfully!"), timeoutMillis = 10_000)
            onNodeWithText("Joined successfully!").assertIsDisplayed()
        }

        // The evidence: what the app actually sent.
        val joinRequest =
            generateSequence { mockServer.server.takeRequest(1, java.util.concurrent.TimeUnit.SECONDS) }
                .firstOrNull { it.path?.endsWith("/households/join") == true }
        requireNotNull(joinRequest) { "App never called POST /households/join" }
        val body = joinRequest.body.readUtf8()

        assert(body.contains("\"ABCD-2345\"")) { "join_code was not the bare code — body was: $body" }
        assert(!body.contains("http")) { "the invite URL leaked into join_code — body was: $body" }
    }
}
