@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package dev.scuttle.inventory.flow

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import dev.scuttle.inventory.ui.dashboard.DASHBOARD_TITLE_TEST_TAG
import dagger.hilt.android.testing.HiltAndroidTest
import dev.scuttle.inventory.FlowTestBase
import org.junit.Test

@HiltAndroidTest
class MissingItemsEmptyFlowTest : FlowTestBase() {

    @Test
    fun missing_items_screen_shows_empty_state_when_no_mandatory_items_missing() {
        // products_one.json: Milk, is_mandatory: false → zero missing items
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

            // Drawer → "Missing items"
            onNodeWithContentDescription("Open menu").performClick()
            waitUntilAtLeastOneExists(hasText("Missing items").and(hasClickAction()), timeoutMillis = 5_000)

            onAllNodesWithText("Missing items").filterToOne(hasClickAction()).performClick()
            waitForIdle()

            // loadFromCache() reads non-mandatory products from in-memory cache → no missing items
            waitUntilAtLeastOneExists(hasText("No missing items"), timeoutMillis = 5_000)
            onNodeWithText("No missing items").assertIsDisplayed()
        }
    }
}
