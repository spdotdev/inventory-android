@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package dev.scuttle.inventory.flow

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import dagger.hilt.android.testing.HiltAndroidTest
import dev.scuttle.inventory.FlowTestBase
import dev.scuttle.inventory.ui.dashboard.DASHBOARD_TITLE_TEST_TAG
import org.junit.Test

@HiltAndroidTest
class DeleteLocationFlowTest : FlowTestBase() {
    @Test
    fun swipe_to_delete_location_removes_it_from_list() {
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

            // Drawer → "All storage" → AllStoragesScreen (no auto-refresh, uses HierarchyStore cache)
            onNodeWithContentDescription("Open menu").performClick()
            waitUntilAtLeastOneExists(hasText("All storage").and(hasClickAction()), timeoutMillis = 5_000)

            onAllNodesWithText("All storage").filterToOne(hasClickAction()).performClick()
            Thread.sleep(2_000)
            waitForIdle()

            // Tap "Add storage location" → StorageOverviewScreen (GET /households/1/locations)
            mockServer.route("/households/1/locations", fixture("locations_one.json"))
            waitUntilAtLeastOneExists(hasText("Home"), timeoutMillis = 5_000)
            onNodeWithContentDescription("Add storage location").performClick()
            Thread.sleep(2_000)
            waitForIdle()

            // StorageOverviewScreen shows "Fridge"
            waitUntilAtLeastOneExists(hasText("Fridge"), timeoutMillis = 5_000)
            Thread.sleep(1_500)
            waitForIdle()
            // Swipe the location card — use first node at the card level (mergeDescendants from clickable)
            onAllNodesWithText("Fridge")[0].performTouchInput { swipeLeft() }
            Thread.sleep(1_000)
            waitForIdle()

            // Confirm dialog "Delete "Fridge"?" — DELETE /households/1/locations/10 → 204
            waitUntilAtLeastOneExists(hasText("Delete \"Fridge\"?"), timeoutMillis = 3_000)
            mockServer.route("/households/1/locations/10", "", code = 204)
            onAllNodesWithText("Delete").filterToOne(hasClickAction()).performClick()
            Thread.sleep(2_000)
            waitForIdle()

            // Fridge removed — StorageOverviewScreen shows empty state
            // (AllStoragesScreen in backstack still has "Fridge" so waitUntilDoesNotExist would never pass)
            waitUntilAtLeastOneExists(hasText("No storage locations yet", substring = true), timeoutMillis = 5_000)
            onNodeWithText("No storage locations yet. Tap + to add a fridge, freezer or pantry.").assertIsDisplayed()
        }
    }
}
