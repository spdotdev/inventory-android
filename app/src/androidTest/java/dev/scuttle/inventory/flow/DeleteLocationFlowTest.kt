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
import dev.scuttle.inventory.data.hierarchy.LocationDeleteStrategy
import dev.scuttle.inventory.ui.dashboard.DASHBOARD_TITLE_TEST_TAG
import org.junit.Test
import java.util.concurrent.TimeUnit

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

    /**
     * The asymmetry LocationDto.kt's own doc comment warns about: what the
     * server counts as "has contents" for a LOCATION is its SHELF count, not
     * its product count. A location holding shelves that are themselves
     * completely empty (product_count = 0) must still stop and ask — getting
     * this wrong 422s every such delete. locations_one_with_empty_shelf.json
     * carries shelf_count=3, product_count=0 to pin exactly that case, as
     * opposed to the plain-confirm (shelf_count=0) case above.
     */
    @Test
    fun a_location_holding_only_empty_shelves_still_requires_a_strategy() {
        mockServer.enqueue(fixture("auth_login.json"))
        mockServer.route("/households", fixture("households_one.json"))
        mockServer.route("/households/1/locations", fixture("locations_one_with_empty_shelf.json"))
        // This screen never itself visits LocationDetailScreen, but HierarchyStore's
        // login-triggered refresh() walks EVERY location's shelves regardless of what
        // the visible screen does — locations_one_with_empty_shelf.json's shelf_count=3
        // means Fridge is one such location. Without a route here, that background GET
        // falls to the mock server's fallback 500, buildFromNetwork() throws, and the
        // WHOLE refresh aborts before ever publishing entries — so the "Home" wait below
        // (AllStoragesScreen reads HierarchyStore's cache, no fetch of its own) times out.
        // The body's actual shelf content is irrelevant here (this test never renders
        // it) — empty keeps the walk from cascading into per-shelf product fetches too.
        mockServer.route("/households/1/locations/10/shelves", fixture("shelves_empty.json"))

        composeRule.apply {
            onNodeWithText("Email").performTextInput("test@example.com")
            onNodeWithText("Password").performTextInput("password123")
            onAllNodesWithText("Sign in").filterToOne(hasClickAction()).performClick()

            Thread.sleep(3_000)
            waitUntilAtLeastOneExists(hasTestTag(DASHBOARD_TITLE_TEST_TAG), timeoutMillis = 5_000)

            onNodeWithTag("bottom-nav-home").performClick()
            waitForIdle()
            Thread.sleep(2_000)
            waitForIdle()

            mockServer.route("/households/1/locations", fixture("locations_one_with_empty_shelf.json"))
            waitUntilAtLeastOneExists(hasText("Home"), timeoutMillis = 5_000)
            onNodeWithContentDescription("Add storage location").performClick()
            Thread.sleep(2_000)
            waitForIdle()

            waitUntilAtLeastOneExists(hasText("Fridge"), timeoutMillis = 5_000)
            Thread.sleep(1_000)
            waitForIdle()

            onNodeWithContentDescription("Edit storage").performClick()
            waitForIdle()
            onNodeWithText("Fridge").performClick()
            waitForIdle()

            // requestDelete() refreshes the location list first.
            mockServer.route("/households/1/locations", fixture("locations_one_with_empty_shelf.json"))
            onNodeWithText("Delete (1)").performClick()
            waitForIdle()
            waitUntilAtLeastOneExists(hasText("Delete 1 item(s)?"), timeoutMillis = 3_000)

            // The strategy question renders despite ZERO products — it's the
            // shelves, not the products, that force it. Fridge is the household's
            // only location, so "move" is never offered (nowhere to move to).
            onNodeWithText("0 product(s) are stored inside. What should happen to them?").assertIsDisplayed()
            onNodeWithText("Move them somewhere else").assertDoesNotExist()

            onNodeWithText("Delete them too").performClick()
            waitForIdle()

            mockServer.route("/households/1/locations/10", "", code = 204)
            mockServer.route("/households/1/locations", fixture("locations_empty.json"))
            onNodeWithTag("delete-strategy-confirm").performClick()
            Thread.sleep(2_000)
            waitForIdle()

            waitUntilAtLeastOneExists(hasText("No storage locations yet", substring = true), timeoutMillis = 5_000)
        }

        val deleteRequest =
            generateSequence { mockServer.server.takeRequest(1, TimeUnit.SECONDS) }
                .firstOrNull { it.method == "DELETE" && it.path == "/households/1/locations/10" }
        requireNotNull(deleteRequest) { "App never sent DELETE /households/1/locations/10" }
        val body = deleteRequest.body.readUtf8()
        val expectedStrategy = "\"strategy\":\"${LocationDeleteStrategy.DELETE_CONTENTS.wire}\""
        assert(body.contains(expectedStrategy)) { "strategy missing/wrong — body was: $body" }
        assert(!body.contains("target_location_id")) { "target_location_id must be OMITTED — body was: $body" }
    }
}
