@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package dev.scuttle.inventory.flow

import androidx.compose.ui.test.assertIsDisplayed
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
import dagger.hilt.android.testing.HiltAndroidTest
import dev.scuttle.inventory.FlowTestBase
import dev.scuttle.inventory.ui.dashboard.DASHBOARD_TITLE_TEST_TAG
import org.junit.Test

/**
 * The regression test for the bug that started Task 5: swiping a location off
 * StorageOverviewScreen used to delete it with NO confirmation dialog at all
 * (and, one level up, no strategy — see the Critical-1 fix in the Task 5/5b
 * review). This drives the full edit-mode flow that replaced the swipe — enter
 * edit mode, select, tap Delete, and the delete only actually happens once the
 * strategy/confirm dialog's own Delete button is tapped. Mirrors
 * DeleteShelfFlowTest.kt one level up: shelves -> locations.
 *
 * Fridge here is EMPTY (locations_one.json carries no shelf_count, which
 * defaults to 0 on LocationDto), so the dialog renders as a plain confirm with
 * no strategy radios — this is exactly the "empty container" case the
 * Critical-1 fix's null-default DTOs exist for (see DeleteRequestSerializationTest).
 */
@HiltAndroidTest
class DeleteLocationFlowTest : FlowTestBase() {
    @Test
    fun select_and_delete_location_requires_confirming_the_strategy_dialog() {
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

            // Storage tab → AllStoragesScreen (no auto-refresh, uses HierarchyStore cache)
            onNodeWithTag("bottom-nav-home").performClick()
            waitForIdle()
            Thread.sleep(2_000)
            waitForIdle()

            // Tap "Add storage location" (the + icon next to "Home" household) → StorageOverviewScreen
            // StorageOverviewViewModel.load() calls GET /households/1/locations
            mockServer.route("/households/1/locations", fixture("locations_one.json"))
            waitUntilAtLeastOneExists(hasText("Home"), timeoutMillis = 5_000)
            onNodeWithContentDescription("Add storage location").performClick()
            Thread.sleep(2_000)
            waitForIdle()

            // StorageOverviewScreen shows "Fridge"
            waitUntilAtLeastOneExists(hasText("Fridge"), timeoutMillis = 5_000)
            Thread.sleep(1_000)
            waitForIdle()

            // Tap the pencil ("Edit storage") → edit mode, replacing the old swipe-to-delete
            onNodeWithContentDescription("Edit storage").performClick()
            waitForIdle()

            // Select "Fridge" row → becomes selected
            onNodeWithText("Fridge").performClick()
            waitForIdle()

            // Delete (1) only OPENS the strategy/confirm dialog — it must not delete yet.
            // requestDelete() refreshes the location list first (GET .../locations).
            mockServer.route("/households/1/locations", fixture("locations_one.json"))
            onNodeWithText("Delete (1)").performClick()
            waitForIdle()
            waitUntilAtLeastOneExists(hasText("Delete 1 item(s)?"), timeoutMillis = 3_000)
            onNodeWithText("Fridge").assertIsDisplayed()

            // Only now, confirming inside the dialog, does the DELETE fire.
            // On success, confirmDelete() refreshes the (now empty) location list again.
            mockServer.route("/households/1/locations/10", "", code = 204)
            mockServer.route("/households/1/locations", fixture("locations_empty.json"))
            onNodeWithTag("delete-strategy-confirm").performClick()
            Thread.sleep(2_000)
            waitForIdle()

            // Fridge removed — StorageOverviewScreen shows its own empty state
            // (AllStoragesScreen in backstack still has "Fridge" so waitUntilDoesNotExist would never pass)
            waitUntilAtLeastOneExists(hasText("No storage locations yet", substring = true), timeoutMillis = 5_000)
            onNodeWithText("No storage locations yet. Tap + to add a fridge, freezer or pantry.").assertIsDisplayed()
        }
    }
}
