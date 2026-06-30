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
class DashboardLocationCardFlowTest : FlowTestBase() {

    @Test
    fun location_detail_shows_shelf_tabs_and_products() {
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

            // Open drawer, wait until DrawerViewModel has loaded "Fridge" as a clickable item
            onNodeWithContentDescription("Open menu").performClick()
            waitUntilAtLeastOneExists(hasText("Fridge").and(hasClickAction()), timeoutMillis = 8_000)

            // Register LocationDetailScreen routes after Drawer VM is done (avoids race with Drawer refresh)
            mockServer.route("/households/1/locations/10/shelves", fixture("shelves_one.json"))
            mockServer.route("/households/1/shelves/100/products", fixture("products_one.json"))
            onAllNodesWithText("Fridge").filterToOne(hasClickAction()).performClick()
            waitForIdle()

            // LocationDetailScreen: "Top shelf" tab and "Milk" product visible
            Thread.sleep(2_000)
            waitUntilAtLeastOneExists(hasText("Top shelf"), timeoutMillis = 8_000)
            onNodeWithText("Top shelf").assertIsDisplayed()
            onNodeWithText("Milk").assertIsDisplayed()
        }
    }
}
