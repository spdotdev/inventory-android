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
            waitUntilAtLeastOneExists(hasTestTag(DASHBOARD_TITLE_TEST_TAG), timeoutMillis = 5_000)

            // Tap Storage tab, wait until AllStoragesScreen has loaded "Fridge" as a clickable item
            onNodeWithTag("bottom-nav-home").performClick()
            waitUntilAtLeastOneExists(hasTestTag("home-location-Fridge"), timeoutMillis = 8_000)

            // Register LocationDetailScreen routes after Storage VM is done (avoids race with refresh)
            mockServer.route("/households/1/locations/10/shelves", fixture("shelves_one.json"))
            mockServer.route("/households/1/shelves/100/products", fixture("products_one.json"))
            onNodeWithTag("home-location-Fridge").performClick()
            waitForIdle()

            // LocationDetailScreen: "Top shelf" tab and "Milk" product visible
            Thread.sleep(2_000)
            waitUntilAtLeastOneExists(hasText("Top shelf"), timeoutMillis = 8_000)
            onNodeWithText("Top shelf").assertIsDisplayed()
            onNodeWithText("Milk").assertIsDisplayed()
        }
    }
}
