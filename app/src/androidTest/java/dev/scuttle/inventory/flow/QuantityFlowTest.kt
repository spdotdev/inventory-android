@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package dev.scuttle.inventory.flow

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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
class QuantityFlowTest : FlowTestBase() {
    private fun loginAndNavigateToShelf() {
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

            onNodeWithTag("bottom-nav-home").performClick()
            waitUntilAtLeastOneExists(hasTestTag("home-location-Fridge"), timeoutMillis = 8_000)

            mockServer.route("/households/1/locations/10/shelves", fixture("shelves_one.json"))
            mockServer.route("/households/1/shelves/100/products", fixture("products_one.json"))
            onNodeWithTag("home-location-Fridge").performClick()
            waitForIdle()
        }
    }

    @Test
    fun increment_updates_quantity_display() {
        loginAndNavigateToShelf()

        composeRule.apply {
            onNodeWithText("0").assertIsDisplayed()

            mockServer.route("/households/1/shelves/100/products/1000/add", fixture("product_incremented.json"))
            onNodeWithContentDescription("Increase Milk quantity").performClick()
            waitForIdle()

            onNodeWithText("1").assertIsDisplayed()
        }
    }

    @Test
    fun decrement_below_zero_is_blocked() {
        loginAndNavigateToShelf()

        composeRule.apply {
            val requestsBefore = mockServer.server.requestCount

            onNodeWithContentDescription("Decrease Milk quantity").performClick()
            waitForIdle()

            onNodeWithText("0").assertIsDisplayed()
            assert(mockServer.server.requestCount == requestsBefore) {
                "Expected no network request for decrement at 0"
            }
        }
    }
}
