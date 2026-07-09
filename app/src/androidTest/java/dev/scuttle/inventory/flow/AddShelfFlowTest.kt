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
class AddShelfFlowTest : FlowTestBase() {

    @Test
    fun add_shelf_appears_as_new_tab() {
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

            // Open drawer → navigate to Fridge (LocationDetailScreen)
            onNodeWithContentDescription("Open menu").performClick()
            waitUntilAtLeastOneExists(hasText("Fridge").and(hasClickAction()), timeoutMillis = 8_000)

            mockServer.route("/households/1/locations/10/shelves", fixture("shelves_one.json"))
            mockServer.route("/households/1/shelves/100/products", fixture("products_one.json"))
            onAllNodesWithText("Fridge").filterToOne(hasClickAction()).performClick()
            waitForIdle()

            // Tap "Add shelf" FAB → fill name → submit
            mockServer.route("/households/1/locations/10/shelves", fixture("shelf_created.json"))
            mockServer.route("/households/1/locations/10/shelves", fixture("shelves_two.json"))
            mockServer.route("/households/1/shelves/100/products", fixture("products_one.json"))
            mockServer.route("/households/1/shelves/101/products", fixture("products_empty.json"))

            onNodeWithContentDescription("Add shelf").performClick()
            waitForIdle()
            onNodeWithText("Shelf name").performTextInput("Middle shelf")
            onNodeWithText("Add").performClick()
            Thread.sleep(2_000)
            waitForIdle()

            waitUntilAtLeastOneExists(hasText("Middle shelf"), timeoutMillis = 5_000)
            onNodeWithText("Middle shelf").assertIsDisplayed()
        }
    }
}
