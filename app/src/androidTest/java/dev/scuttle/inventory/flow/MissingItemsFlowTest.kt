@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package dev.scuttle.inventory.flow

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import dev.scuttle.inventory.ui.dashboard.DASHBOARD_TITLE_TEST_TAG
import dagger.hilt.android.testing.HiltAndroidTest
import dev.scuttle.inventory.FlowTestBase
import org.junit.Test

@HiltAndroidTest
class MissingItemsFlowTest : FlowTestBase() {

    @Test
    fun mandatory_product_with_zero_stock_appears_in_missing_items() {
        mockServer.enqueue(fixture("auth_login.json"))
        mockServer.route("/households", fixture("households_one.json"))
        mockServer.route("/households/1/locations", fixture("locations_one.json"))
        mockServer.route("/households/1/locations/10/shelves", fixture("shelves_one.json"))
        mockServer.route("/households/1/shelves/100/products", fixture("products_mandatory_one.json"))

        composeRule.apply {
            onNodeWithText("Email").performTextInput("test@example.com")
            onNodeWithText("Password").performTextInput("password123")
            onAllNodesWithText("Sign in").filterToOne(hasClickAction()).performClick()

            Thread.sleep(3_000)
            waitUntilAtLeastOneExists(hasText("1 item is missing"), timeoutMillis = 5_000)
            onNodeWithText("1 item is missing").assertIsDisplayed()

            onNodeWithText("1 item is missing").performClick()
            Thread.sleep(2_000)
            waitForIdle()

            onNodeWithText("Milk").assertIsDisplayed()
            // Location appears in the subtitle "Fridge · Top shelf"
            onNodeWithText("Fridge · Top shelf").assertIsDisplayed()
        }
    }

    @Test
    fun product_detail_loads_correct_product() {
        // Navigate to the product detail screen and verify it shows the product correctly
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

            onNodeWithContentDescription("Open menu").performClick()
            waitUntilAtLeastOneExists(hasText("Fridge").and(hasClickAction()), timeoutMillis = 8_000)

            mockServer.route("/households/1/locations/10/shelves", fixture("shelves_one.json"))
            mockServer.route("/households/1/shelves/100/products", fixture("products_one.json"))
            onAllNodesWithText("Fridge").filterToOne(hasClickAction()).performClick()
            waitForIdle()

            // Register response for ProductDetailViewModel's list call, then tap "Milk"
            mockServer.route("/households/1/shelves/100/products", fixture("products_one.json"))
            waitUntilAtLeastOneExists(hasTestTag("product-1000"), timeoutMillis = 5_000)
            onNodeWithTag("product-1000").performClick()

            // Product detail screen shows the product name and the mandatory toggle label
            waitUntilAtLeastOneExists(hasText("Mandatory on this shelf"), timeoutMillis = 10_000)
            onAllNodesWithText("Milk")[0].assertIsDisplayed()
            onNodeWithText("Mandatory on this shelf").assertIsDisplayed()
        }
    }
}
