@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package dev.scuttle.inventory.flow

import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.test.waitUntilDoesNotExist
import dagger.hilt.android.testing.HiltAndroidTest
import dev.scuttle.inventory.FlowTestBase
import dev.scuttle.inventory.ui.dashboard.DASHBOARD_TITLE_TEST_TAG
import org.junit.Test

@HiltAndroidTest
class MoveProductFlowTest : FlowTestBase() {
    @Test
    fun move_product_to_another_shelf_removes_it_from_current_view() {
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

            // Storage tab → Fridge (LocationDetailScreen with "Top shelf" tab showing Milk)
            onNodeWithTag("bottom-nav-home").performClick()
            waitUntilAtLeastOneExists(hasTestTag("home-location-Fridge"), timeoutMillis = 8_000)

            mockServer.route("/households/1/locations/10/shelves", fixture("shelves_two.json"))
            mockServer.route("/households/1/shelves/100/products", fixture("products_one.json"))
            mockServer.route("/households/1/shelves/101/products", fixture("products_empty.json"))
            onNodeWithTag("home-location-Fridge").performClick()
            waitForIdle()

            // Tap "Move Milk" → startMove builds target list
            // GET /households/1/locations, GET /households/1/locations/10/shelves →
            // 2 shelves; current shelf 100 excluded → shows shelf 101
            mockServer.route("/households/1/locations", fixture("locations_one.json"))
            mockServer.route("/households/1/locations/10/shelves", fixture("shelves_two.json"))
            waitUntilAtLeastOneExists(hasText("Milk"), timeoutMillis = 5_000)
            onNodeWithContentDescription("Move Milk").performClick()
            waitForIdle()

            // Dialog "Move to…" shows target shelves
            waitUntilAtLeastOneExists(hasText("Move to…"), timeoutMillis = 5_000)

            // POST /households/1/shelves/100/products/1000/move → product_moved.json
            mockServer.enqueue(fixture("product_moved.json"))
            // "Fridge › Middle shelf" is the only target (shelf 101, current is 100)
            onNodeWithText("Fridge › Middle shelf").performClick()
            Thread.sleep(2_000)
            waitForIdle()

            // Milk moved to another shelf — disappears from current shelf view
            waitUntilDoesNotExist(hasText("Milk"), timeoutMillis = 5_000)
        }
    }
}
