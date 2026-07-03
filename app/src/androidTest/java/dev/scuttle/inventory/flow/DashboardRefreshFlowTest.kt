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
class DashboardRefreshFlowTest : FlowTestBase() {

    @Test
    fun refresh_button_updates_stat_cards() {
        // Initial data: 1 location, 1 shelf, 1 product
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
            // Wait for stat cards to appear (initial load complete)
            waitUntilAtLeastOneExists(hasText("Locations"), timeoutMillis = 5_000)

            // Tap Refresh → DashboardViewModel.refresh() fetches updated hierarchy
            // locations_two.json gives 2 locations (Fridge id=10, Pantry id=11)
            mockServer.route("/households", fixture("households_one.json"))
            mockServer.route("/households/1/locations", fixture("locations_two.json"))
            mockServer.route("/households/1/locations/10/shelves", fixture("shelves_one.json"))
            mockServer.route("/households/1/shelves/100/products", fixture("products_one.json"))
            mockServer.route("/households/1/locations/11/shelves", fixture("shelves_empty.json"))
            onNodeWithContentDescription("Refresh").performClick()

            Thread.sleep(2_000)
            waitForIdle()

            // Locations stat card now shows "2" — wait for it to appear in the stat area
            // (the stat cards are NOT clickable, so no duplicate from Drawer)
            waitUntilAtLeastOneExists(hasText("2"), timeoutMillis = 5_000)
            // Verify both the updated count and its label are visible on screen
            onNodeWithText("Locations").assertIsDisplayed()
            onNodeWithText("2").assertIsDisplayed()
        }
    }

    @Test
    fun refresh_failure_shows_error_text() {
        // Initial data loads successfully
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

            // Tap Refresh — no mock registered for /households → server returns 500
            // DashboardViewModel.refresh() onFailure sets state.error = e.message
            onNodeWithContentDescription("Refresh").performClick()
            Thread.sleep(2_000)
            waitForIdle()

            // Error message contains HTTP 500 status
            waitUntilAtLeastOneExists(hasText("500", substring = true), timeoutMillis = 5_000)
            onAllNodesWithText("500", substring = true)[0].assertIsDisplayed()
        }
    }
}
