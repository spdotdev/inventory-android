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
import androidx.compose.ui.test.waitUntilDoesNotExist
import dagger.hilt.android.testing.HiltAndroidTest
import dev.scuttle.inventory.FlowTestBase
import dev.scuttle.inventory.ui.dashboard.DASHBOARD_TITLE_TEST_TAG
import org.junit.Test

/**
 * The regression test for the bug that started Task 4: the shelves list used to
 * bulk-delete checked shelves with NO confirmation dialog at all. This now drives
 * the full edit-mode flow — enter edit mode, select, tap Delete, and the delete
 * only actually happens once the strategy/confirm dialog's own Delete button is
 * tapped. If a future change makes the top bar's Delete button call confirmDelete()
 * directly instead of requestDelete(), this test breaks because the dialog step
 * below would never appear before the shelf disappears.
 */
@HiltAndroidTest
class DeleteShelfFlowTest : FlowTestBase() {
    @Test
    fun select_and_delete_shelf_requires_confirming_the_strategy_dialog() {
        mockServer.enqueue(fixture("auth_login.json"))
        mockServer.route("/households", fixture("households_one.json"))
        mockServer.route("/households/1/locations", fixture("locations_one.json"))
        // Use shelves_two to have "Top shelf" and "Middle shelf" — delete "Top shelf"
        mockServer.route("/households/1/locations/10/shelves", fixture("shelves_two.json"))
        mockServer.route("/households/1/shelves/100/products", fixture("products_one.json"))
        mockServer.route("/households/1/shelves/101/products", fixture("products_empty.json"))

        composeRule.apply {
            onNodeWithText("Email").performTextInput("test@example.com")
            onNodeWithText("Password").performTextInput("password123")
            onAllNodesWithText("Sign in").filterToOne(hasClickAction()).performClick()

            Thread.sleep(3_000)
            waitUntilAtLeastOneExists(hasTestTag(DASHBOARD_TITLE_TEST_TAG), timeoutMillis = 5_000)

            // Storage tab → Fridge → LocationDetailScreen
            onNodeWithTag("bottom-nav-home").performClick()
            waitUntilAtLeastOneExists(hasTestTag("home-location-Fridge"), timeoutMillis = 8_000)

            mockServer.route("/households/1/locations/10/shelves", fixture("shelves_two.json"))
            mockServer.route("/households/1/shelves/100/products", fixture("products_one.json"))
            mockServer.route("/households/1/shelves/101/products", fixture("products_empty.json"))
            onNodeWithTag("home-location-Fridge").performClick()
            waitForIdle()

            // Both shelves visible as tabs (default view)
            waitUntilAtLeastOneExists(hasText("Top shelf"), timeoutMillis = 5_000)

            // Tap the pencil ("Edit shelves") → edit mode, forced into the list view
            onNodeWithContentDescription("Edit shelves").performClick()
            waitForIdle()

            // Select "Top shelf" row → becomes selected
            onNodeWithText("Top shelf").performClick()
            waitForIdle()

            // Delete (1) only OPENS the strategy/confirm dialog — it must not delete yet.
            // requestDelete() refreshes the shelf list first (GET .../shelves) before
            // building the plan (see ShelvesViewModel.requestDelete's doc) — without
            // re-registering this route, that second GET falls through to the mock
            // server's fallback 500 and the dialog never opens.
            mockServer.route("/households/1/locations/10/shelves", fixture("shelves_two.json"))
            onNodeWithText("Delete (1)").performClick()
            waitForIdle()
            waitUntilAtLeastOneExists(hasText("Delete 1 item(s)?"), timeoutMillis = 3_000)
            onNodeWithText("Top shelf").assertIsDisplayed()

            // Only now, confirming inside the dialog, does the DELETE fire.
            mockServer.route("/households/1/locations/10/shelves/100", "", code = 204)
            onNodeWithTag("delete-strategy-confirm").performClick()
            Thread.sleep(2_000)
            waitForIdle()

            waitUntilDoesNotExist(hasText("Top shelf"), timeoutMillis = 5_000)
            onNodeWithText("Middle shelf").assertExists()
        }
    }
}
