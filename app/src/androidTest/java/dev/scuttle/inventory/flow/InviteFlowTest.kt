@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package dev.scuttle.inventory.flow

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import dagger.hilt.android.testing.HiltAndroidTest
import dev.scuttle.inventory.FlowTestBase
import org.junit.Test

@HiltAndroidTest
class InviteFlowTest : FlowTestBase() {

    @Test
    fun invite_screen_shows_join_code() {
        mockServer.enqueue(fixture("auth_login.json"))
        mockServer.route("/households", fixture("households_one.json"))
        mockServer.route("/households/1/locations", fixture("locations_one.json"))
        mockServer.route("/households/1/locations/10/shelves", fixture("shelves_one.json"))
        mockServer.route("/households/1/shelves/100/products", fixture("products_one.json"))

        composeRule.apply {
            onNodeWithText("Email").performTextInput("test@example.com")
            onNodeWithText("Password").performTextInput("password123")
            onAllNodesWithText("Sign in").filterToOne(hasClickAction()).performClick()

            Thread.sleep(3_000)
            waitUntilAtLeastOneExists(hasText("Dashboard"), timeoutMillis = 5_000)

            // Open drawer → Households
            onNodeWithContentDescription("Open menu").performClick()
            waitUntilAtLeastOneExists(hasText("Households").and(hasClickAction()), timeoutMillis = 5_000)
            mockServer.route("/households", fixture("households_one.json"))
            onAllNodesWithText("Households").filterToOne(hasClickAction()).performClick()
            waitForIdle()

            // Tap share icon for "Home" → InviteScreen loads — GET /households/1/invite
            mockServer.route("/households/1/invite", fixture("invite.json"))
            onNodeWithContentDescription("Invite to Home").performClick()
            Thread.sleep(2_000)
            waitForIdle()

            waitUntilAtLeastOneExists(hasText("Join code"), timeoutMillis = 5_000)
            onNodeWithText("AAAA-1111").assertIsDisplayed()
        }
    }
}
